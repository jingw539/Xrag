-- V19: Evaluation tables for model comparison and tag-level metrics
CREATE TABLE IF NOT EXISTS evaluation_run (
    run_id       BIGINT PRIMARY KEY,
    run_name     VARCHAR(64),
    model_name   VARCHAR(64),
    dataset_name VARCHAR(64),
    task_type    VARCHAR(32) DEFAULT 'xray',
    status       VARCHAR(16) DEFAULT 'DONE',
    notes        TEXT,
    params_json  TEXT,
    created_by   BIGINT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_eval_run_model ON evaluation_run(model_name);
CREATE INDEX IF NOT EXISTS idx_eval_run_dataset ON evaluation_run(dataset_name);
CREATE INDEX IF NOT EXISTS idx_eval_run_created ON evaluation_run(created_at DESC);

CREATE TABLE IF NOT EXISTS evaluation_metric (
    metric_id    BIGINT PRIMARY KEY,
    run_id       BIGINT NOT NULL,
    scope        VARCHAR(16) DEFAULT 'GLOBAL',
    tag_name     VARCHAR(64),
    metric_name  VARCHAR(32),
    metric_value DOUBLE PRECISION,
    support      INTEGER,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_eval_metric_run ON evaluation_metric(run_id);
CREATE INDEX IF NOT EXISTS idx_eval_metric_tag ON evaluation_metric(tag_name);
CREATE INDEX IF NOT EXISTS idx_eval_metric_name ON evaluation_metric(metric_name);
