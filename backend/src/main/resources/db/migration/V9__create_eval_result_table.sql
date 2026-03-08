-- 创建评测结果表
CREATE TABLE eval_result (
    eval_id BIGINT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    model_id BIGINT,
    model_version VARCHAR(64),
    ai_labels TEXT,
    ref_labels TEXT,
    precision_score NUMERIC(5,4),
    recall_score NUMERIC(5,4),
    f1_score NUMERIC(5,4),
    per_label_f1 TEXT,
    eval_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eval_report FOREIGN KEY (report_id) REFERENCES report_info(report_id),
    CONSTRAINT fk_eval_model FOREIGN KEY (model_id) REFERENCES ai_model_info(model_id)
);

-- 创建索引
CREATE INDEX idx_eval_report_id ON eval_result(report_id);
CREATE INDEX idx_eval_model_id ON eval_result(model_id);
CREATE INDEX idx_eval_time ON eval_result(eval_time);

-- 添加注释
COMMENT ON TABLE eval_result IS '评测结果表';
COMMENT ON COLUMN eval_result.eval_id IS '评测ID';
COMMENT ON COLUMN eval_result.report_id IS '所属报告ID';
COMMENT ON COLUMN eval_result.model_id IS '使用的模型ID';
COMMENT ON COLUMN eval_result.model_version IS '模型版本描述';
COMMENT ON COLUMN eval_result.ai_labels IS 'AI报告标签(JSON，14类病理)';
COMMENT ON COLUMN eval_result.ref_labels IS '参考报告标签(JSON)';
COMMENT ON COLUMN eval_result.precision_score IS '宏平均精确率';
COMMENT ON COLUMN eval_result.recall_score IS '宏平均召回率';
COMMENT ON COLUMN eval_result.f1_score IS '宏平均F1';
COMMENT ON COLUMN eval_result.per_label_f1 IS '各病理类别F1明细(JSON)';
COMMENT ON COLUMN eval_result.eval_time IS '评测时间';
