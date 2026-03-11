import argparse
import json
import time
from typing import List, Dict

import requests


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="JSONL file from ingest_dataset.py")
    parser.add_argument("--rag-url", default="http://127.0.0.1:8010")
    parser.add_argument("--batch-size", type=int, default=50)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    items: List[Dict] = []
    total = 0
    with open(args.input, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except Exception:
                continue
            items.append({
                "report_id": obj.get("report_id"),
                "case_id": obj.get("case_id"),
                "findings": obj.get("findings", ""),
                "impression": obj.get("impression", ""),
                "source_id": obj.get("source_id", ""),
                "image_path": obj.get("image_path", ""),
            })
            if len(items) >= args.batch_size:
                resp = requests.post(args.rag_url.rstrip("/") + "/index", json={"items": items}, timeout=120)
                resp.raise_for_status()
                total += len(items)
                items = []
                time.sleep(0.1)
    if items:
        resp = requests.post(args.rag_url.rstrip("/") + "/index", json={"items": items}, timeout=120)
        resp.raise_for_status()
        total += len(items)
    print(f"Done. Indexed {total} items.")


if __name__ == "__main__":
    main()
