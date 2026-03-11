# RAG Service (Minimal)

This is a lightweight RAG sidecar for XRAG. It provides:
- `/index` to upsert report text into a FAISS index
- `/search` to retrieve similar reports by text query

It supports two embedding modes:
1. Remote OpenAI-compatible embeddings API
2. Local `sentence-transformers` model

## Run (local)

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8010
```

## Environment

```
RAG_INDEX_PATH=/data/index.faiss
RAG_META_PATH=/data/meta.json

# Remote embeddings (optional)
EMBEDDING_API_BASE_URL=
EMBEDDING_API_KEY=
EMBEDDING_API_MODEL=text-embedding-3-small

# Local embeddings (used when remote is not set)
EMBEDDING_MODEL=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

## API

### POST /index

```json
{
  "items": [
    {
      "report_id": 123,
      "case_id": 456,
      "findings": "...",
      "impression": "..."
    }
  ]
}
```

### POST /search

```json
{
  "query": "short findings text here",
  "top_k": 3
}
```



## Dataset Ingest (CSV + DeepSeek)

Example (sample 500 rows):

```bash
set DEEPSEEK_API_KEY=your_key_here
python ingest_dataset.py --csv "D:\data\Cleanses csv tfrecords\df_train.csv" --limit 500 --batch-size 20 --rag-url http://127.0.0.1:8010
```

Optional MinIO upload:

```bash
set MINIO_ENDPOINT=http://127.0.0.1:9000
set MINIO_USER=minioadmin
set MINIO_PASSWORD=minioadmin
python ingest_dataset.py --csv "D:\data\Cleanses csv tfrecords\df_train.csv" --limit 500 --upload-minio
```
