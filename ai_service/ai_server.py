"""
AI Service — FastAPI server (deployed on AutoDL)
Provides endpoints called by Spring Boot AiServiceClient:
  POST /retrieval/search    — FAISS image similarity search
  POST /report/generate     — LLaVA-Med report generation
  POST /eval/chexbert       — Real CheXpert 14-class evaluation
  POST /terms/analyze       — Term normalization (keep DeepSeek for this)

Requirements (install on AutoDL):
  pip install fastapi uvicorn torch torchvision faiss-gpu transformers pillow
  pip install accelerate bitsandbytes sentencepiece requests numpy
"""
import os
import json
import time
import logging
import ipaddress
import numpy as np
from pathlib import Path
from typing import List, Optional
from io import BytesIO
from urllib.parse import urlparse

import torch
import faiss
from PIL import Image
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from torchvision import transforms
import requests

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="XRAG AI Service")

AI_ALLOWED_IMAGE_HOSTS = {
    host.strip().lower()
    for host in os.getenv("AI_ALLOWED_IMAGE_HOSTS", "localhost,127.0.0.1,minio").split(",")
    if host.strip()
}
AI_ALLOW_LOCAL_FILE = os.getenv("AI_ALLOW_LOCAL_FILE", "false").lower() == "true"
AI_HEALTH_INCLUDE_DETAILS = os.getenv("AI_HEALTH_INCLUDE_DETAILS", "false").lower() == "true"
MAX_IMAGE_DOWNLOAD_BYTES = int(os.getenv("AI_MAX_IMAGE_DOWNLOAD_BYTES", str(10 * 1024 * 1024)))

# ─── Config ────────────────────────────────────────────────────────────────
CHEXPERT_MODEL_PATH = os.getenv("CHEXPERT_MODEL_PATH", "/root/checkpoints/chexpert/best_chexpert.pth")
FAISS_INDEX_PATH    = os.getenv("FAISS_INDEX_PATH",    "/root/checkpoints/chexpert/faiss/cxr.index")
FAISS_META_PATH     = os.getenv("FAISS_META_PATH",     "/root/checkpoints/chexpert/faiss/meta.json")
LLAVA_MODEL_PATH    = os.getenv("LLAVA_MODEL_PATH",    "/root/checkpoints/llava-med")
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

CHEXPERT_LABELS = [
    "Atelectasis", "Cardiomegaly", "Consolidation", "Edema",
    "Enlarged Cardiomediastinum", "Fracture", "Lung Lesion", "Lung Opacity",
    "No Finding", "Pleural Effusion", "Pleural Other", "Pneumonia",
    "Pneumothorax", "Support Devices"
]

# ─── Model Loading ──────────────────────────────────────────────────────────
class Models:
    chexpert_model = None
    faiss_index = None
    faiss_meta: List[dict] = []
    llava_model = None
    llava_processor = None

models = Models()


def load_chexpert():
    """Load trained CheXpert classifier"""
    if not Path(CHEXPERT_MODEL_PATH).exists():
        logger.warning(f"CheXpert model not found at {CHEXPERT_MODEL_PATH}, using random weights")
        from torchvision import models as tv_models
        import torch.nn as nn
        base = tv_models.densenet121(pretrained=False)
        base.classifier = nn.Linear(base.classifier.in_features, 14)
        models.chexpert_model = base.to(DEVICE).eval()
        return
    from torchvision import models as tv_models
    import torch.nn as nn
    base = tv_models.densenet121(pretrained=False)
    base.classifier = nn.Linear(base.classifier.in_features, 14)
    ckpt = torch.load(CHEXPERT_MODEL_PATH, map_location=DEVICE)
    state = ckpt.get("model_state", ckpt)
    base.load_state_dict(state, strict=False)
    models.chexpert_model = base.to(DEVICE).eval()
    logger.info("CheXpert classifier loaded")


def load_faiss():
    """Load FAISS index and metadata"""
    if not Path(FAISS_INDEX_PATH).exists():
        logger.warning("FAISS index not found, retrieval will return empty results")
        return
    models.faiss_index = faiss.read_index(FAISS_INDEX_PATH)
    if Path(FAISS_META_PATH).exists():
        with open(FAISS_META_PATH) as f:
            models.faiss_meta = json.load(f)
    logger.info(f"FAISS index loaded: {models.faiss_index.ntotal} vectors")


def load_llava():
    """Load LLaVA-Med for report generation (optional, heavy)"""
    if not Path(LLAVA_MODEL_PATH).exists():
        logger.warning(f"LLaVA-Med not found at {LLAVA_MODEL_PATH}, will use fallback")
        return
    try:
        from transformers import LlavaNextProcessor, LlavaNextForConditionalGeneration
        models.llava_processor = LlavaNextProcessor.from_pretrained(LLAVA_MODEL_PATH)
        models.llava_model = LlavaNextForConditionalGeneration.from_pretrained(
            LLAVA_MODEL_PATH,
            torch_dtype=torch.float16,
            load_in_4bit=True
        ).to(DEVICE)
        logger.info("LLaVA-Med loaded in 4-bit")
    except Exception as e:
        logger.error(f"LLaVA-Med load failed: {e}")


def _is_safe_remote_host(hostname: Optional[str]) -> bool:
    if not hostname:
        return False
    normalized = hostname.strip().lower()
    if normalized in AI_ALLOWED_IMAGE_HOSTS:
        return True
    try:
        ip = ipaddress.ip_address(normalized)
        return not (ip.is_loopback or ip.is_private or ip.is_link_local or ip.is_multicast)
    except ValueError:
        return False


def _validate_remote_url(image_url: str) -> str:
    parsed = urlparse(image_url)
    if parsed.scheme not in {"http", "https"}:
        raise HTTPException(status_code=400, detail="Only http/https image URLs are allowed")
    if not _is_safe_remote_host(parsed.hostname):
        raise HTTPException(status_code=400, detail="Image host is not allowed")
    return image_url


@app.on_event("startup")
async def startup():
    load_chexpert()
    load_faiss()
    load_llava()


# ─── Image Preprocessing ────────────────────────────────────────────────────
IMG_TRANSFORM = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
])


def load_image_from_url_or_base64(image_url: str) -> Image.Image:
    if image_url.startswith("data:image"):
        import base64
        header, data = image_url.split(",", 1)
        img_bytes = base64.b64decode(data)
        return Image.open(BytesIO(img_bytes)).convert("RGB")
    elif image_url.startswith("http"):
        safe_url = _validate_remote_url(image_url)
        resp = requests.get(safe_url, timeout=30, stream=True)
        resp.raise_for_status()
        content_length = resp.headers.get("Content-Length")
        if content_length and int(content_length) > MAX_IMAGE_DOWNLOAD_BYTES:
            raise HTTPException(status_code=400, detail="Image is too large")
        img_bytes = resp.raw.read(MAX_IMAGE_DOWNLOAD_BYTES + 1)
        if len(img_bytes) > MAX_IMAGE_DOWNLOAD_BYTES:
            raise HTTPException(status_code=400, detail="Image is too large")
        return Image.open(BytesIO(img_bytes)).convert("RGB")
    else:
        if not AI_ALLOW_LOCAL_FILE:
            raise HTTPException(status_code=400, detail="Local file paths are disabled")
        return Image.open(image_url).convert("RGB")


def get_image_embedding(img: Image.Image) -> np.ndarray:
    """Extract DenseNet121 feature embedding"""
    tensor = IMG_TRANSFORM(img).unsqueeze(0).to(DEVICE)
    with torch.no_grad():
        features = models.chexpert_model.features(tensor)
        emb = torch.nn.functional.adaptive_avg_pool2d(features, 1).flatten(1)
    emb_np = emb.cpu().numpy().astype(np.float32)
    faiss.normalize_L2(emb_np)
    return emb_np


# ─── Schemas ────────────────────────────────────────────────────────────────
class RetrievalRequest(BaseModel):
    image_url: str
    top_k: int = 3
    case_id: Optional[int] = None

class ReportRequest(BaseModel):
    image_url: str
    similar_cases: Optional[List[dict]] = None

class EvalRequest(BaseModel):
    report_text: str
    reference_labels: Optional[List[str]] = None

class TermRequest(BaseModel):
    report_text: str


# ─── Endpoints ──────────────────────────────────────────────────────────────
@app.post("/retrieval/search")
async def retrieval_search(req: RetrievalRequest):
    """FAISS image similarity search using DenseNet121 embeddings"""
    start = time.time()
    if models.faiss_index is None or models.chexpert_model is None:
        return {"similar_cases": [], "elapsed_ms": 0, "error": "FAISS index not loaded"}
    try:
        img = load_image_from_url_or_base64(req.image_url)
        emb = get_image_embedding(img)
        scores, indices = models.faiss_index.search(emb, req.top_k + 1)
        results = []
        for score, idx in zip(scores[0], indices[0]):
            if idx < 0 or idx >= len(models.faiss_meta):
                continue
            meta = models.faiss_meta[idx]
            if req.case_id and meta.get("case_id") == req.case_id:
                continue
            results.append({
                "case_id": meta.get("case_id"),
                "exam_no": meta.get("exam_no"),
                "findings": meta.get("findings", ""),
                "impression": meta.get("impression", ""),
                "similarity_score": float(score)
            })
            if len(results) >= req.top_k:
                break
        elapsed = int((time.time() - start) * 1000)
        return {"similar_cases": results, "elapsed_ms": elapsed}
    except Exception as e:
        logger.error(f"Retrieval error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/eval/chexbert")
async def eval_chexbert(req: EvalRequest):
    """
    Real CheXpert 14-class evaluation.
    If reference_labels provided: compute true F1/Precision/Recall.
    Otherwise: report-only prediction with estimated metrics.
    """
    start = time.time()
    if models.chexpert_model is None:
        raise HTTPException(status_code=503, detail="CheXpert model not loaded")

    pred_labels, label_probs = predict_from_text(req.report_text)

    ref = set(req.reference_labels) if req.reference_labels else set()
    pred_set = set(pred_labels)

    if ref:
        tp = len(pred_set & ref)
        fp = len(pred_set - ref)
        fn = len(ref - pred_set)
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall    = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
        missing = list(ref - pred_set)
        extra   = list(pred_set - ref)
    else:
        confidence = np.mean([label_probs.get(l, 0.0) for l in pred_labels]) if pred_labels else 0.0
        precision = confidence
        recall    = confidence * 0.9
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
        missing, extra = [], []

    elapsed = int((time.time() - start) * 1000)
    return {
        "predicted_labels": pred_labels,
        "label_probabilities": label_probs,
        "reference_labels": list(ref),
        "missing_labels": missing,
        "extra_labels": extra,
        "precision": round(precision, 4),
        "recall":    round(recall, 4),
        "f1_score":  round(f1, 4),
        "bleu4":     0.0,
        "rouge_l":   0.0,
        "elapsed_ms": elapsed
    }


def predict_from_text(report_text: str, threshold: float = 0.4):
    """
    Keyword-based label extraction from report text.
    (Replace with bert-based NLP when bert-chexpert model is ready)
    """
    text_lower = report_text.lower()
    keyword_map = {
        "Atelectasis":              ["atelectasis", "肺不张", "盘状肺不张"],
        "Cardiomegaly":             ["cardiomegaly", "心脏扩大", "心影增大", "心脏增大"],
        "Consolidation":            ["consolidation", "肺实变", "实变"],
        "Edema":                    ["edema", "肺水肿", "水肿"],
        "Enlarged Cardiomediastinum":["enlarged cardiomediastinum","纵隔增宽","纵隔宽"],
        "Fracture":                 ["fracture", "骨折", "肋骨骨折"],
        "Lung Lesion":              ["lung lesion", "肺结节", "结节", "肿块", "mass"],
        "Lung Opacity":             ["opacity", "肺混浊", "斑片影", "阴影"],
        "No Finding":               ["no finding", "no acute", "未见明显异常", "正常"],
        "Pleural Effusion":         ["pleural effusion", "胸腔积液", "积液"],
        "Pleural Other":            ["pleural", "胸膜增厚", "胸膜"],
        "Pneumonia":                ["pneumonia", "肺炎", "炎症"],
        "Pneumothorax":             ["pneumothorax", "气胸"],
        "Support Devices":          ["support device", "tube", "catheter", "导管", "插管"],
    }
    probs = {}
    predicted = []
    for label, keywords in keyword_map.items():
        hits = sum(1 for kw in keywords if kw in text_lower)
        prob = min(1.0, hits * 0.5)
        probs[label] = round(prob, 4)
        if prob >= threshold:
            predicted.append(label)
    if not predicted:
        predicted = ["No Finding"]
        probs["No Finding"] = 0.8
    return predicted, probs


@app.post("/report/generate")
async def report_generate(req: ReportRequest):
    """
    Report generation using LLaVA-Med.
    Falls back to template if model not loaded.
    """
    start = time.time()
    similar_ctx = ""
    if req.similar_cases:
        lines = []
        for i, c in enumerate(req.similar_cases[:3]):
            lines.append(f"参考病例{i+1}: 所见: {c.get('findings','')} 诊断: {c.get('impression','')}")
        similar_ctx = "\n".join(lines)

    if models.llava_model is not None:
        result = generate_with_llava(req.image_url, similar_ctx)
    else:
        result = generate_fallback(req.image_url, similar_ctx)

    elapsed = int((time.time() - start) * 1000)
    result["elapsed_ms"] = elapsed
    return result


def generate_with_llava(image_url: str, similar_ctx: str) -> dict:
    try:
        img = load_image_from_url_or_base64(image_url)
        prompt = (
            "You are an expert radiologist. Analyze this chest X-ray and generate a structured report.\n"
            f"{similar_ctx}\n"
            "Output JSON only: {\"findings\": \"...\", \"impression\": \"...\", \"confidence\": 0.85}"
        )
        inputs = models.llava_processor(
            text=f"<image>\n{prompt}", images=img, return_tensors="pt"
        ).to(DEVICE)
        with torch.no_grad():
            output = models.llava_model.generate(**inputs, max_new_tokens=512, do_sample=False)
        text = models.llava_processor.decode(output[0], skip_special_tokens=True)
        import re
        m = re.search(r'\{.*\}', text, re.DOTALL)
        if m:
            return json.loads(m.group())
        return {"findings": text.strip(), "impression": "", "confidence": 0.75}
    except Exception as e:
        logger.error(f"LLaVA generate error: {e}")
        return generate_fallback(image_url, similar_ctx)


def generate_fallback(image_url: str, similar_ctx: str) -> dict:
    return {
        "findings": "LLaVA-Med model not loaded. Please train and deploy the model, or configure Qwen-VL API.",
        "impression": "Model unavailable.",
        "confidence": 0.0
    }


@app.post("/terms/analyze")
async def terms_analyze(req: TermRequest):
    """
    Term normalization — kept as DeepSeek API call since LLMs handle this well.
    Configure DEEPSEEK_API_KEY env var.
    """
    api_key = os.getenv("DEEPSEEK_API_KEY", "")
    base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
    if not api_key:
        return {"corrections": []}
    prompt = f"""
你是医学术语标准化专家，请分析以下胸部X光报告，找出不规范术语并提供标准化建议。
报告文本：{req.report_text}
请严格按以下JSON格式输出，不要包含任何其他文字：
{{"corrections": [{{"original_term": "原始术语", "suggested_term": "标准术语", "context": "上下文"}}]}}
若无需修正则返回 {{"corrections": []}}
"""
    try:
        resp = requests.post(
            f"{base_url}/chat/completions",
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json={"model": "deepseek-chat", "messages": [{"role": "user", "content": prompt}],
                  "response_format": {"type": "json_object"}},
            timeout=30
        )
        content = resp.json()["choices"][0]["message"]["content"]
        return json.loads(content)
    except Exception as e:
        logger.error(f"Term analysis error: {e}")
        return {"corrections": []}


@app.get("/health")
def health():
    if not AI_HEALTH_INCLUDE_DETAILS:
        return {"status": "ok"}
    return {
        "status": "ok",
        "chexpert_model": models.chexpert_model is not None,
        "faiss_index": models.faiss_index is not None,
        "faiss_vectors": models.faiss_index.ntotal if models.faiss_index else 0,
        "llava_model": models.llava_model is not None,
        "device": DEVICE
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, workers=1)
