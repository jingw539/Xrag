import json
import os
import threading
from typing import List, Optional, Dict, Any

import numpy as np
import requests
import faiss
from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

app = FastAPI()

INDEX_PATH = os.getenv("RAG_INDEX_PATH", "/data/index.faiss")
META_PATH = os.getenv("RAG_META_PATH", "/data/meta.json")

EMBEDDING_API_BASE_URL = os.getenv("EMBEDDING_API_BASE_URL", "").strip()
EMBEDDING_API_KEY = os.getenv("EMBEDDING_API_KEY", "").strip()
EMBEDDING_API_MODEL = os.getenv("EMBEDDING_API_MODEL", "text-embedding-3-small").strip()

EMBEDDING_MODEL = os.getenv(
    "EMBEDDING_MODEL",
    "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
).strip()

_lock = threading.Lock()
_index = None
_meta: Dict[str, Dict[str, Any]] = {}
_model: Optional[SentenceTransformer] = None


class IndexItem(BaseModel):
    report_id: Optional[int] = None
    case_id: int
    findings: str = ""
    impression: str = ""
    source_id: Optional[str] = None
    image_path: Optional[str] = None


class IndexRequest(BaseModel):
    items: List[IndexItem]


class SearchRequest(BaseModel):
    query: str
    top_k: int = 3


def _ensure_dirs():
    os.makedirs(os.path.dirname(INDEX_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(META_PATH), exist_ok=True)


def _load_meta():
    global _meta
    if os.path.exists(META_PATH):
        try:
            with open(META_PATH, "r", encoding="utf-8") as f:
                _meta = json.load(f)
        except Exception:
            _meta = {}


def _save_meta():
    with open(META_PATH, "w", encoding="utf-8") as f:
        json.dump(_meta, f, ensure_ascii=False)


def _load_index():
    global _index
    if os.path.exists(INDEX_PATH):
        _index = faiss.read_index(INDEX_PATH)


def _save_index():
    if _index is not None:
        faiss.write_index(_index, INDEX_PATH)


def _get_model() -> SentenceTransformer:
    global _model
    if _model is None:
        _model = SentenceTransformer(EMBEDDING_MODEL)
    return _model


def _normalize(vectors: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(vectors, axis=1, keepdims=True) + 1e-12
    return vectors / norms


def _create_index(dim: int):
    global _index
    base = faiss.IndexFlatIP(dim)
    _index = faiss.IndexIDMap2(base)


def _embed_with_api(texts: List[str]) -> List[List[float]]:
    if not EMBEDDING_API_BASE_URL:
        return []
    url = EMBEDDING_API_BASE_URL.rstrip("/") + "/embeddings"
    headers = {"Content-Type": "application/json"}
    if EMBEDDING_API_KEY:
        headers["Authorization"] = f"Bearer {EMBEDDING_API_KEY}"
    payload = {"model": EMBEDDING_API_MODEL, "input": texts}
    resp = requests.post(url, headers=headers, json=payload, timeout=30)
    resp.raise_for_status()
    data = resp.json().get("data", [])
    return [item["embedding"] for item in data]


def _embed(texts: List[str]) -> np.ndarray:
    if EMBEDDING_API_BASE_URL:
        vectors = _embed_with_api(texts)
        if vectors:
            return _normalize(np.array(vectors, dtype="float32"))
    model = _get_model()
    emb = model.encode(texts, normalize_embeddings=True)
    return np.array(emb, dtype="float32")


@app.on_event("startup")
def _startup():
    _ensure_dirs()
    _load_meta()
    _load_index()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/index")
def index_docs(req: IndexRequest):
    if not req.items:
        return {"indexed": 0}
    texts = []
    ids = []
    metas = []
    for item in req.items:
        report_id = item.report_id if item.report_id is not None else item.case_id
        text = (item.findings or "") + "\n" + (item.impression or "")
        texts.append(text.strip())
        ids.append(int(report_id))
        metas.append({
            "report_id": int(report_id),
            "case_id": int(item.case_id),
            "findings": item.findings or "",
            "impression": item.impression or "",
            "source_id": item.source_id or "",
            "image_path": item.image_path or "",
        })
    vectors = _embed(texts)
    with _lock:
        if _index is None:
            _create_index(vectors.shape[1])
        ids_np = np.array(ids, dtype="int64")
        _index.remove_ids(ids_np)
        _index.add_with_ids(vectors, ids_np)
        for m in metas:
            _meta[str(m["report_id"])] = m
        _save_index()
        _save_meta()
    return {"indexed": len(ids)}


@app.post("/search")
def search(req: SearchRequest):
    query = (req.query or "").strip()
    if not query:
        return {"results": []}
    vectors = _embed([query])
    with _lock:
        if _index is None or _index.ntotal == 0:
            return {"results": []}
        top_k = max(1, int(req.top_k))
        scores, ids = _index.search(vectors, top_k)
    results = []
    for score, rid in zip(scores[0].tolist(), ids[0].tolist()):
        if rid == -1:
            continue
        meta = _meta.get(str(rid))
        if not meta:
            continue
        results.append({
            "case_id": meta.get("case_id"),
            "report_id": meta.get("report_id"),
            "score": float(score),
            "findings": meta.get("findings", ""),
            "impression": meta.get("impression", ""),
            "source_id": meta.get("source_id", ""),
            "image_path": meta.get("image_path", ""),
        })
    return {"results": results}
