-- =============================================================
-- XRAG 数据库修复脚本（兼容 openGauss）
-- =============================================================

-- 1. term_correction.correction_time → created_at（已在上次执行中完成，跳过）

-- 2. 确保 image_annotation 表存在
CREATE TABLE IF NOT EXISTS image_annotation (
    annotation_id  BIGINT        PRIMARY KEY,
    image_id       BIGINT        NOT NULL,
    report_id      BIGINT,
    source         VARCHAR(10)   NOT NULL DEFAULT 'DOCTOR',
    anno_type      VARCHAR(20)   NOT NULL DEFAULT 'RECTANGLE',
    label          VARCHAR(100),
    remark         TEXT,
    x              DECIMAL(7,4)  NOT NULL,
    y              DECIMAL(7,4)  NOT NULL,
    width          DECIMAL(7,4)  NOT NULL,
    height         DECIMAL(7,4)  NOT NULL,
    color          VARCHAR(20),
    confidence     DECIMAL(5,3),
    created_by     BIGINT,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- 3. 补全 report_info 缺失字段
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
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='report_info' AND column_name='similar_case_ids') THEN
        ALTER TABLE report_info ADD COLUMN similar_case_ids TEXT;
    END IF;
END $$;

-- 4. 补全 eval_result 缺失字段
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
END $$;

-- 5. 修复 case_info.report_status 数据不一致
UPDATE case_info
SET report_status = 'NONE', updated_at = CURRENT_TIMESTAMP
WHERE report_status <> 'NONE'
  AND case_id NOT IN (SELECT case_id FROM report_info);

-- 6. 查看修复结果
SELECT
    ci.case_id,
    ci.exam_no,
    ci.report_status AS case_status,
    ri.report_id,
    ri.report_status AS report_status
FROM case_info ci
LEFT JOIN report_info ri ON ci.case_id = ri.case_id
ORDER BY ci.case_id;
