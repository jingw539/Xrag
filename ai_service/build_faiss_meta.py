"""
构建 FAISS 元数据 JSON (meta.json)
将数据库中的病例 case_id/exam_no/findings/impression 与 FAISS 索引槽位对齐
在 AutoDL 上运行，训练完分类器后执行

Usage:
  python build_faiss_meta.py \
    --db_host 111.229.72.224 --db_port 3306 \
    --db_user root --db_pass xxx --db_name xray \
    --image_dir /root/mimic-cxr/files \
    --model_path /root/checkpoints/chexpert/best_chexpert.pth \
    --output_dir /root/checkpoints/chexpert/faiss
"""
import os
import json
import argparse
import numpy as np
import torch
import faiss
from PIL import Image
from pathlib import Path
from torchvision import transforms, models
import torch.nn as nn
import pymysql

IMG_TRANSFORM = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
])

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"


def load_model(model_path: str):
    base = models.densenet121(pretrained=False)
    base.classifier = nn.Linear(base.classifier.in_features, 14)
    if Path(model_path).exists():
        ckpt = torch.load(model_path, map_location=DEVICE)
        base.load_state_dict(ckpt.get("model_state", ckpt), strict=False)
        print(f"Model loaded from {model_path}")
    else:
        print("Warning: model not found, using random weights")
    return base.to(DEVICE).eval()


def embed_image(model, img_path: str) -> np.ndarray:
    try:
        img = Image.open(img_path).convert("RGB")
    except Exception:
        return None
    tensor = IMG_TRANSFORM(img).unsqueeze(0).to(DEVICE)
    with torch.no_grad():
        f = model.features(tensor)
        emb = torch.nn.functional.adaptive_avg_pool2d(f, 1).flatten(1)
    emb_np = emb.cpu().numpy().astype(np.float32)
    faiss.normalize_L2(emb_np)
    return emb_np


def query_cases(db_config: dict):
    """Query all typical cases with their signed reports from MySQL"""
    conn = pymysql.connect(**db_config)
    cursor = conn.cursor(pymysql.cursors.DictCursor)
    cursor.execute("""
        SELECT c.case_id, c.exam_no, c.patient_id,
               r.final_findings, r.final_impression, i.file_path
        FROM case_info c
        JOIN image_info i ON i.case_id = c.case_id
        LEFT JOIN report_info r ON r.case_id = c.case_id AND r.report_status = 'SIGNED'
        WHERE c.is_typical = 1
        GROUP BY c.case_id, i.image_id
        ORDER BY c.exam_time DESC
    """)
    rows = cursor.fetchall()
    conn.close()
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--db_host",    default="111.229.72.224")
    parser.add_argument("--db_port",    type=int, default=3306)
    parser.add_argument("--db_user",    default="root")
    parser.add_argument("--db_pass",    default="")
    parser.add_argument("--db_name",    default="xray")
    parser.add_argument("--image_dir",  default="/root/mimic-cxr/files")
    parser.add_argument("--minio_url",  default="http://111.229.72.224:9000/cxr-images")
    parser.add_argument("--model_path", default="/root/checkpoints/chexpert/best_chexpert.pth")
    parser.add_argument("--output_dir", default="/root/checkpoints/chexpert/faiss")
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)
    model = load_model(args.model_path)

    db_config = dict(host=args.db_host, port=args.db_port,
                     user=args.db_user, password=args.db_pass, db=args.db_name)
    rows = query_cases(db_config)
    print(f"Found {len(rows)} typical cases with images")

    embeddings = []
    meta = []
    for row in rows:
        file_path = row["file_path"]
        img_path = Path(args.image_dir) / file_path if not file_path.startswith("http") \
                   else None
        if img_path and img_path.exists():
            emb = embed_image(model, str(img_path))
        elif file_path.startswith("http"):
            import requests
            from io import BytesIO
            try:
                resp = requests.get(file_path, timeout=15)
                img = Image.open(BytesIO(resp.content)).convert("RGB")
                tensor = IMG_TRANSFORM(img).unsqueeze(0).to(DEVICE)
                with torch.no_grad():
                    f = model.features(tensor)
                    emb_t = torch.nn.functional.adaptive_avg_pool2d(f, 1).flatten(1)
                emb = emb_t.cpu().numpy().astype(np.float32)
                faiss.normalize_L2(emb)
            except Exception as e:
                print(f"  Skip {file_path}: {e}")
                continue
        else:
            print(f"  Skip missing: {img_path}")
            continue

        if emb is None:
            continue

        embeddings.append(emb)
        meta.append({
            "case_id":    row["case_id"],
            "exam_no":    row["exam_no"],
            "findings":   row.get("final_findings") or "",
            "impression": row.get("final_impression") or "",
        })
        print(f"  Embedded case_id={row['case_id']}")

    if not embeddings:
        print("No embeddings built. Check image paths.")
        return

    all_embs = np.vstack(embeddings).astype(np.float32)
    dim = all_embs.shape[1]
    index = faiss.IndexFlatIP(dim)
    index.add(all_embs)

    index_path = os.path.join(args.output_dir, "cxr.index")
    meta_path  = os.path.join(args.output_dir, "meta.json")
    faiss.write_index(index, index_path)
    with open(meta_path, "w") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    print(f"\nDone! FAISS index: {index.ntotal} vectors (dim={dim})")
    print(f"  Index: {index_path}")
    print(f"  Meta:  {meta_path}")


if __name__ == "__main__":
    main()
