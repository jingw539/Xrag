import argparse
import os
import sys
from typing import List, Dict, Any, Optional

try:
    import psycopg2
    import psycopg2.extras
except Exception as exc:
    print("Missing dependency: psycopg2-binary")
    print("Install with: pip install psycopg2-binary")
    raise SystemExit(1) from exc

import requests


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Reindex signed reports into RAG service.")
    parser.add_argument("--db-host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--db-port", type=int, default=int(os.getenv("DB_PORT", "5432")))
    parser.add_argument("--db-name", default=os.getenv("DB_NAME", "chest_xray_db"))
    parser.add_argument("--db-user", default=os.getenv("DB_USER", "gaussdb"))
    parser.add_argument("--db-password", default=os.getenv("DB_PASSWORD", ""))

    parser.add_argument("--rag-url", default=os.getenv("RAG_URL", "http://127.0.0.1:8010"))
    parser.add_argument("--batch-size", type=int, default=50)
    parser.add_argument("--limit", type=int, default=0, help="0 means no limit")
    parser.add_argument("--only-signed", action="store_true", default=True)
    parser.add_argument("--include-ai", action="store_true", default=True,
                        help="Use AI findings/impression when final is empty")
    return parser.parse_args()


def pick_text(primary: Optional[str], fallback: Optional[str]) -> str:
    if primary and primary.strip():
        return primary.strip()
    if fallback and fallback.strip():
        return fallback.strip()
    return ""


def fetch_reports(conn, only_signed: bool, limit: int):
    clauses = []
    params: List[Any] = []
    if only_signed:
        clauses.append("report_status = %s")
        params.append("SIGNED")
    where_sql = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    limit_sql = "LIMIT %s" if limit and limit > 0 else ""
    if limit_sql:
        params.append(limit)
    sql = f"""
        SELECT report_id, case_id, report_status,
               final_findings, final_impression,
               ai_findings, ai_impression
        FROM report_info
        {where_sql}
        ORDER BY report_id DESC
        {limit_sql}
    """
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    cur.execute(sql, params)
    return cur


def main() -> None:
    args = parse_args()
    rag_url = args.rag_url.rstrip("/")
    if not rag_url:
        raise SystemExit("RAG_URL is empty")

    conn = psycopg2.connect(
        host=args.db_host,
        port=args.db_port,
        dbname=args.db_name,
        user=args.db_user,
        password=args.db_password,
    )
    conn.autocommit = True

    cur = fetch_reports(conn, args.only_signed, args.limit)
    batch: List[Dict[str, Any]] = []
    total = 0
    skipped = 0

    for row in cur:
        findings = pick_text(row.get("final_findings"), row.get("ai_findings") if args.include_ai else "")
        impression = pick_text(row.get("final_impression"), row.get("ai_impression") if args.include_ai else "")
        if not findings and not impression:
            skipped += 1
            continue
        item = {
            "report_id": int(row["report_id"]),
            "case_id": int(row["case_id"]),
            "findings": findings,
            "impression": impression,
        }
        batch.append(item)
        if len(batch) >= args.batch_size:
            resp = requests.post(f"{rag_url}/index", json={"items": batch}, timeout=120)
            resp.raise_for_status()
            total += len(batch)
            batch = []

    if batch:
        resp = requests.post(f"{rag_url}/index", json={"items": batch}, timeout=120)
        resp.raise_for_status()
        total += len(batch)

    cur.close()
    conn.close()
    print(f"Done. Indexed {total} items. Skipped {skipped} empty reports.")


if __name__ == "__main__":
    main()
