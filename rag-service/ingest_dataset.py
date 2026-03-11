import argparse
import csv
import hashlib
import json
import os
import re
import time
from pathlib import Path
from typing import Dict, List, Optional

import requests
from requests import exceptions as req_exceptions

DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-chat"

PROMPT = (
    "You are a radiology report translator. Translate the English chest X-ray report into Chinese medical report.\n"
    "Requirements:\n"
    "1) Do not add any findings not present in the source.\n"
    "2) Keep clinically appropriate and concise.\n"
    "3) Output format must be:\n"
    "\\u6240\\u89c1\\uff1a...\\n"
    "\\u5370\\u8c61\\uff1a...\\n"
    "4) If no clear impression, write '\\u672a\\u89c1\\u660e\\u786e\\u5370\\u8c61'."
)


def stable_int_id(text: str) -> int:
    digest = hashlib.md5(text.encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big") & ((1 << 63) - 1)


def normalize_split(split: str) -> str:
    if split.lower() in {"val", "valid", "validation"}:
        return "Valid"
    if split.lower() == "train":
        return "Train"
    if split.lower() == "test":
        return "Test"
    return split


def resolve_image_path(raw_path: str, image_root: Path) -> Optional[Path]:
    if not raw_path:
        return None
    raw = raw_path.replace("\\", "/")
    match = re.search(r"mimic_dset/re_512_3ch/(Train|Test|Valid|Val)", raw, re.IGNORECASE)
    filename = Path(raw).name
    if match:
        split = normalize_split(match.group(1))
        candidate = image_root / split / filename
        if candidate.exists():
            return candidate
    for split in ["Train", "Valid", "Test"]:
        candidate = image_root / split / filename
        if candidate.exists():
            return candidate
    return None


def is_chinese_text(text: str) -> bool:
    if not text:
        return False
    cjk = sum(1 for ch in text if "\u4e00" <= ch <= "\u9fff")
    return cjk >= 2


def translate_with_deepseek(text: str, base_url: str, api_key: str, model: str) -> str:
    url = base_url.rstrip("/") + "/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
    }
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "You are a radiology report translator."},
            {"role": "user", "content": PROMPT + "\nEnglish report:\n" + text},
        ],
        "temperature": 0.2,
        "top_p": 0.8,
    }
    for attempt in range(3):
        try:
            resp = requests.post(url, headers=headers, json=payload, timeout=60)
            resp.raise_for_status()
            data = resp.json()
            return data.get("choices", [{}])[0].get("message", {}).get("content", "").strip()
        except req_exceptions.RequestException:
            if attempt == 2:
                return ""
            time.sleep(2 + attempt * 2)
        except ValueError:
            if attempt == 2:
                return ""
            time.sleep(2 + attempt * 2)


def upload_to_minio(local_path: Path, endpoint: str, access_key: str, secret_key: str, bucket: str, object_name: str) -> str:
    try:
        from minio import Minio
    except Exception as exc:
        raise RuntimeError("minio package not installed. pip install minio") from exc

    client = Minio(endpoint.replace("http://", "").replace("https://", ""),
                  access_key=access_key,
                  secret_key=secret_key,
                  secure=endpoint.startswith("https://"))
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)
    client.fput_object(bucket, object_name, str(local_path))
    return object_name


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True, help="CSV path with id,text,path")
    parser.add_argument("--limit", type=int, default=500)
    parser.add_argument("--batch-size", type=int, default=20)
    parser.add_argument("--rag-url", default="http://127.0.0.1:8010")
    parser.add_argument("--out", default="rag_ingest.jsonl")
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--image-root", default=r"D:\\data\\mimic_dset\\re_512_3ch")
    parser.add_argument("--upload-minio", action="store_true")
    parser.add_argument("--minio-endpoint", default=os.getenv("MINIO_ENDPOINT", ""))
    parser.add_argument("--minio-access", default=os.getenv("MINIO_USER", ""))
    parser.add_argument("--minio-secret", default=os.getenv("MINIO_PASSWORD", ""))
    parser.add_argument("--minio-bucket", default=os.getenv("MINIO_BUCKET", "cxr-images"))
    parser.add_argument("--deepseek-base-url", default=os.getenv("DEEPSEEK_BASE_URL", DEFAULT_DEEPSEEK_BASE_URL))
    parser.add_argument("--deepseek-api-key", default=os.getenv("DEEPSEEK_API_KEY", ""))
    parser.add_argument("--deepseek-model", default=os.getenv("DEEPSEEK_MODEL", DEFAULT_MODEL))
    return parser.parse_args()


def load_done_ids(out_path: Path) -> set:
    done = set()
    if not out_path.exists():
        return done
    with out_path.open("r", encoding="utf-8") as f:
        for line in f:
            try:
                obj = json.loads(line)
                if "source_id" in obj:
                    done.add(obj["source_id"])
            except Exception:
                continue
    return done


def main() -> None:
    args = parse_args()
    csv_path = Path(args.csv)
    out_path = Path(args.out)
    image_root = Path(args.image_root)

    if not csv_path.exists():
        raise SystemExit(f"CSV not found: {csv_path}")

    if not args.deepseek_api_key:
        raise SystemExit("DEEPSEEK_API_KEY is required for translation")

    done_ids = load_done_ids(out_path) if args.resume else set()
    batch: List[Dict] = []
    written = 0

    with csv_path.open("r", encoding="utf-8", errors="ignore", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if written >= args.limit:
                break
            source_id = (row.get("id") or "").strip()
            text_en = (row.get("text") or "").strip()
            raw_path = (row.get("path") or "").strip()
            if not source_id or not text_en:
                continue
            if source_id in done_ids:
                continue

            translated = translate_with_deepseek(text_en, args.deepseek_base_url, args.deepseek_api_key, args.deepseek_model)
            if not is_chinese_text(translated):
                translated = ""

            image_path = None
            local_img = resolve_image_path(raw_path, image_root)
            if args.upload_minio and local_img is not None:
                if not args.minio_endpoint or not args.minio_access or not args.minio_secret:
                    raise SystemExit("MinIO endpoint/access/secret required for upload")
                ext = local_img.suffix.lower() or ".jpg"
                object_name = f"rag/{source_id}{ext}"
                image_path = upload_to_minio(local_img, args.minio_endpoint, args.minio_access, args.minio_secret,
                                             args.minio_bucket, object_name)
            else:
                image_path = str(local_img) if local_img else raw_path

            int_id = stable_int_id(source_id)
            item = {
                "report_id": int_id,
                "case_id": int_id,
                "findings": text_en,
                "impression": translated,
                "source_id": source_id,
                "image_path": image_path or "",
            }

            with out_path.open("a", encoding="utf-8") as out:
                out.write(json.dumps(item, ensure_ascii=False) + "\n")
            written += 1

            batch.append(item)
            if len(batch) >= args.batch_size:
                resp = requests.post(args.rag_url.rstrip("/") + "/index", json={"items": batch}, timeout=120)
                resp.raise_for_status()
                batch = []
                time.sleep(0.2)

    if batch:
        resp = requests.post(args.rag_url.rstrip("/") + "/index", json={"items": batch}, timeout=120)
        resp.raise_for_status()

    print(f"Done. Indexed {written} items. Output: {out_path}")


if __name__ == "__main__":
    main()
