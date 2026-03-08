"""
CheXpert 14类分类器训练脚本
基于 MIMIC-CXR-JPG 数据集
推荐GPU: RTX 4090 (24GB)
"""
import os
import json
import numpy as np
import pandas as pd
from PIL import Image
from pathlib import Path

import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms, models
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR
from sklearn.metrics import f1_score, precision_score, recall_score
import argparse

CHEXPERT_LABELS = [
    "Atelectasis", "Cardiomegaly", "Consolidation", "Edema",
    "Enlarged Cardiomediastinum", "Fracture", "Lung Lesion", "Lung Opacity",
    "No Finding", "Pleural Effusion", "Pleural Other", "Pneumonia",
    "Pneumothorax", "Support Devices"
]

class MimicCxrDataset(Dataset):
    """MIMIC-CXR-JPG Dataset"""
    def __init__(self, csv_path: str, image_dir: str, transform=None, split="train"):
        self.df = pd.read_csv(csv_path)
        self.df = self.df[self.df["split"] == split].reset_index(drop=True)
        self.image_dir = Path(image_dir)
        self.transform = transform
        for col in CHEXPERT_LABELS:
            if col not in self.df.columns:
                self.df[col] = 0.0
        self.df[CHEXPERT_LABELS] = self.df[CHEXPERT_LABELS].fillna(0).clip(0, 1)

    def __len__(self):
        return len(self.df)

    def __getitem__(self, idx):
        row = self.df.iloc[idx]
        img_path = self.image_dir / row["path"] if "path" in row else \
                   self.image_dir / f"p{str(row['subject_id'])[:2]}" / \
                   f"p{row['subject_id']}" / f"s{row['study_id']}" / \
                   f"{row['dicom_id']}.jpg"
        try:
            img = Image.open(img_path).convert("RGB")
        except Exception:
            img = Image.new("RGB", (224, 224), 128)
        if self.transform:
            img = self.transform(img)
        labels = torch.FloatTensor([row[l] for l in CHEXPERT_LABELS])
        return img, labels


class CheXpertClassifier(nn.Module):
    def __init__(self, num_classes=14, backbone="densenet121", pretrained=True):
        super().__init__()
        if backbone == "densenet121":
            base = models.densenet121(pretrained=pretrained)
            self.features = base.features
            in_features = base.classifier.in_features
        elif backbone == "efficientnet_b4":
            base = models.efficientnet_b4(pretrained=pretrained)
            self.features = base.features
            in_features = base.classifier[1].in_features
        else:
            base = models.resnet50(pretrained=pretrained)
            modules = list(base.children())[:-1]
            self.features = nn.Sequential(*modules)
            in_features = base.fc.in_features

        self.pool = nn.AdaptiveAvgPool2d(1)
        self.dropout = nn.Dropout(0.3)
        self.classifier = nn.Linear(in_features, num_classes)

    def forward(self, x):
        f = self.features(x)
        f = self.pool(f).flatten(1)
        f = self.dropout(f)
        return self.classifier(f)

    def get_embeddings(self, x):
        """Extract 1024-dim embeddings for FAISS indexing"""
        f = self.features(x)
        return self.pool(f).flatten(1)


def train_epoch(model, loader, optimizer, criterion, device):
    model.train()
    total_loss = 0.0
    for imgs, labels in loader:
        imgs, labels = imgs.to(device), labels.to(device)
        optimizer.zero_grad()
        logits = model(imgs)
        loss = criterion(logits, labels)
        loss.backward()
        nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        total_loss += loss.item()
    return total_loss / len(loader)


@torch.no_grad()
def evaluate(model, loader, device, threshold=0.5):
    model.eval()
    all_preds, all_labels = [], []
    for imgs, labels in loader:
        imgs = imgs.to(device)
        logits = model(imgs)
        probs = torch.sigmoid(logits).cpu().numpy()
        all_preds.append((probs > threshold).astype(int))
        all_labels.append(labels.numpy().astype(int))
    preds = np.vstack(all_preds)
    labels = np.vstack(all_labels)
    f1_per_label = f1_score(labels, preds, average=None, zero_division=0)
    f1_macro = f1_score(labels, preds, average="macro", zero_division=0)
    f1_dict = {CHEXPERT_LABELS[i]: round(float(f1_per_label[i]), 4) for i in range(14)}
    return f1_macro, f1_dict


def build_faiss_index(model, loader, device, save_path="faiss_index"):
    """Build FAISS index from image embeddings"""
    import faiss
    model.eval()
    embeddings, case_ids = [], []
    with torch.no_grad():
        for imgs, labels in loader:
            imgs = imgs.to(device)
            embs = model.get_embeddings(imgs).cpu().numpy()
            embeddings.append(embs)
    embeddings = np.vstack(embeddings).astype(np.float32)
    faiss.normalize_L2(embeddings)
    index = faiss.IndexFlatIP(embeddings.shape[1])
    index.add(embeddings)
    os.makedirs(save_path, exist_ok=True)
    faiss.write_index(index, os.path.join(save_path, "cxr.index"))
    np.save(os.path.join(save_path, "embeddings.npy"), embeddings)
    print(f"FAISS index built: {embeddings.shape[0]} vectors, dim={embeddings.shape[1]}")
    return index


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data_csv",  default="/root/mimic-cxr/mimic-cxr-2.0.0-chexpert.csv")
    parser.add_argument("--image_dir", default="/root/mimic-cxr/files")
    parser.add_argument("--output_dir", default="/root/checkpoints/chexpert")
    parser.add_argument("--backbone",  default="densenet121")
    parser.add_argument("--epochs",    type=int, default=20)
    parser.add_argument("--batch_size",type=int, default=32)
    parser.add_argument("--lr",        type=float, default=1e-4)
    parser.add_argument("--img_size",  type=int, default=224)
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    train_tfm = transforms.Compose([
        transforms.Resize((args.img_size + 32, args.img_size + 32)),
        transforms.RandomCrop(args.img_size),
        transforms.RandomHorizontalFlip(),
        transforms.ColorJitter(brightness=0.2, contrast=0.2),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ])
    val_tfm = transforms.Compose([
        transforms.Resize((args.img_size, args.img_size)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ])

    train_ds = MimicCxrDataset(args.data_csv, args.image_dir, train_tfm, "train")
    val_ds   = MimicCxrDataset(args.data_csv, args.image_dir, val_tfm,   "validate")
    train_loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True,
                              num_workers=4, pin_memory=True)
    val_loader   = DataLoader(val_ds,   batch_size=args.batch_size, shuffle=False,
                              num_workers=4, pin_memory=True)

    model = CheXpertClassifier(num_classes=14, backbone=args.backbone).to(device)
    pos_weight = torch.ones(14).to(device) * 5.0
    criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
    optimizer = AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = CosineAnnealingLR(optimizer, T_max=args.epochs)

    best_f1 = 0.0
    for epoch in range(1, args.epochs + 1):
        loss = train_epoch(model, train_loader, optimizer, criterion, device)
        f1_macro, f1_dict = evaluate(model, val_loader, device)
        scheduler.step()
        print(f"Epoch {epoch:02d} | loss={loss:.4f} | macro_F1={f1_macro:.4f}")
        for label, score in f1_dict.items():
            print(f"  {label:<30} F1={score:.4f}")
        if f1_macro > best_f1:
            best_f1 = f1_macro
            ckpt_path = os.path.join(args.output_dir, "best_chexpert.pth")
            torch.save({"model_state": model.state_dict(),
                        "f1_dict": f1_dict, "epoch": epoch}, ckpt_path)
            print(f"  ✓ Saved best model (F1={best_f1:.4f})")

    print(f"\nTraining done. Best macro F1: {best_f1:.4f}")
    print("Building FAISS index from training set embeddings...")
    index_loader = DataLoader(train_ds, batch_size=64, shuffle=False, num_workers=4)
    build_faiss_index(model, index_loader, device,
                      save_path=os.path.join(args.output_dir, "faiss"))


if __name__ == "__main__":
    main()
