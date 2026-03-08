-- V13: 补充缺失的数据表和列，修正 V7/V9 migration 遗漏的字段
-- 使用 IF NOT EXISTS，对已用 1.sql 初始化的库也安全可重入

-- ============================================================
-- 1. sys_refresh_token (JWT刷新令牌，V5之后缺失)
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_refresh_token (
    token_id   BIGINT       PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    revoked    SMALLINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES sys_user(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id ON sys_refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash    ON sys_refresh_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON sys_refresh_token(expires_at);

-- ============================================================
-- 2. retrieval_log (RAG检索日志，V8之后缺失)
-- ============================================================
CREATE TABLE IF NOT EXISTS retrieval_log (
    retrieval_id        BIGINT    PRIMARY KEY,
    case_id             BIGINT    NOT NULL,
    query_image_id      BIGINT,
    retriever_model_id  BIGINT,
    top_k               INT       NOT NULL DEFAULT 3,
    similar_case_ids    TEXT,
    similarity_scores   TEXT,
    all_above_threshold SMALLINT  NOT NULL DEFAULT 1,
    elapsed_ms          INT,
    retrieval_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_retrieval_case  FOREIGN KEY (case_id)            REFERENCES case_info(case_id),
    CONSTRAINT fk_retrieval_image FOREIGN KEY (query_image_id)     REFERENCES image_info(image_id),
    CONSTRAINT fk_retrieval_model FOREIGN KEY (retriever_model_id) REFERENCES ai_model_info(model_id)
);

CREATE INDEX IF NOT EXISTS idx_retrieval_case_id ON retrieval_log(case_id);
CREATE INDEX IF NOT EXISTS idx_retrieval_time    ON retrieval_log(retrieval_time DESC);

-- ============================================================
-- 3. 补全 report_info 缺失字段 (V7 遗漏)
-- ============================================================
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='report_info' AND column_name='gen_model_id') THEN
        ALTER TABLE report_info ADD COLUMN gen_model_id BIGINT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='report_info' AND column_name='retrieval_log_id') THEN
        ALTER TABLE report_info ADD COLUMN retrieval_log_id BIGINT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='report_info' AND column_name='ai_prompt') THEN
        ALTER TABLE report_info ADD COLUMN ai_prompt TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='report_info' AND column_name='quality_grade') THEN
        ALTER TABLE report_info ADD COLUMN quality_grade CHAR(1);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='report_info' AND column_name='model_confidence') THEN
        ALTER TABLE report_info ADD COLUMN model_confidence NUMERIC(5,4);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_report_gen_model') THEN
        ALTER TABLE report_info ADD CONSTRAINT fk_report_gen_model
            FOREIGN KEY (gen_model_id) REFERENCES ai_model_info(model_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_report_retrieval') THEN
        ALTER TABLE report_info ADD CONSTRAINT fk_report_retrieval
            FOREIGN KEY (retrieval_log_id) REFERENCES retrieval_log(retrieval_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_report_quality   ON report_info(quality_grade);
CREATE INDEX IF NOT EXISTS idx_report_sign_time ON report_info(sign_time DESC);
CREATE INDEX IF NOT EXISTS idx_report_gen_time  ON report_info(ai_generate_time DESC);

-- ============================================================
-- 4. report_edit_history (报告修改历史，审核留痕)
-- ============================================================
CREATE TABLE IF NOT EXISTS report_edit_history (
    history_id        BIGINT       PRIMARY KEY,
    report_id         BIGINT       NOT NULL,
    editor_id         BIGINT       NOT NULL,
    findings_before   TEXT,
    findings_after    TEXT,
    impression_before TEXT,
    impression_after  TEXT,
    edit_note         VARCHAR(512),
    edit_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hist_report FOREIGN KEY (report_id) REFERENCES report_info(report_id),
    CONSTRAINT fk_hist_editor FOREIGN KEY (editor_id) REFERENCES sys_user(user_id)
);

CREATE INDEX IF NOT EXISTS idx_hist_report_id ON report_edit_history(report_id);
CREATE INDEX IF NOT EXISTS idx_hist_edit_time ON report_edit_history(edit_time DESC);

-- ============================================================
-- 5. critical_alert (危急值预警)
-- ============================================================
CREATE TABLE IF NOT EXISTS critical_alert (
    alert_id        BIGINT       PRIMARY KEY,
    case_id         BIGINT       NOT NULL,
    report_id       BIGINT       NOT NULL,
    label_type      VARCHAR(64)  NOT NULL,
    label_prob      NUMERIC(5,4) NOT NULL,
    alert_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    responder_id    BIGINT,
    response_action VARCHAR(16),
    response_time   TIMESTAMP,
    response_note   VARCHAR(512),
    alert_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_case      FOREIGN KEY (case_id)      REFERENCES case_info(case_id),
    CONSTRAINT fk_alert_report    FOREIGN KEY (report_id)    REFERENCES report_info(report_id),
    CONSTRAINT fk_alert_responder FOREIGN KEY (responder_id) REFERENCES sys_user(user_id)
);

CREATE INDEX IF NOT EXISTS idx_alert_case_id ON critical_alert(case_id);
CREATE INDEX IF NOT EXISTS idx_alert_status  ON critical_alert(alert_status);
CREATE INDEX IF NOT EXISTS idx_alert_time    ON critical_alert(alert_time DESC);

-- ============================================================
-- 6. term_correction (术语规范化纠错)
-- ============================================================
CREATE TABLE IF NOT EXISTS term_correction (
    correction_id    BIGINT       PRIMARY KEY,
    report_id        BIGINT       NOT NULL,
    original_term    VARCHAR(128),
    suggested_term   VARCHAR(256),
    context_sentence TEXT,
    is_accepted      SMALLINT     NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_term_report FOREIGN KEY (report_id) REFERENCES report_info(report_id)
);

CREATE INDEX IF NOT EXISTS idx_term_report_id ON term_correction(report_id);

-- ============================================================
-- 7. 补全 eval_result 缺失字段 (V9 遗漏)
-- ============================================================
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='eval_result' AND column_name='eval_type') THEN
        ALTER TABLE eval_result ADD COLUMN eval_type VARCHAR(16) DEFAULT 'AI' NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='eval_result' AND column_name='bleu4_score') THEN
        ALTER TABLE eval_result ADD COLUMN bleu4_score NUMERIC(5,4);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='eval_result' AND column_name='rouge_l_score') THEN
        ALTER TABLE eval_result ADD COLUMN rouge_l_score NUMERIC(5,4);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='eval_result' AND column_name='quality_grade') THEN
        ALTER TABLE eval_result ADD COLUMN quality_grade CHAR(1);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='eval_result' AND column_name='missing_labels') THEN
        ALTER TABLE eval_result ADD COLUMN missing_labels TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='eval_result' AND column_name='extra_labels') THEN
        ALTER TABLE eval_result ADD COLUMN extra_labels TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='eval_result' AND column_name='elapsed_ms') THEN
        ALTER TABLE eval_result ADD COLUMN elapsed_ms INT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='eval_result' AND column_name='created_at') THEN
        ALTER TABLE eval_result ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_eval_grade ON eval_result(quality_grade);
