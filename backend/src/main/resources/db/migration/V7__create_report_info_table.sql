-- 创建报告表
CREATE TABLE report_info (
    report_id BIGINT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    report_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ai_findings TEXT,
    ai_impression TEXT,
    final_findings TEXT,
    final_impression TEXT,
    similar_case_ids TEXT,
    doctor_id BIGINT,
    sign_time TIMESTAMP,
    ai_generate_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_case FOREIGN KEY (case_id) REFERENCES case_info(case_id),
    CONSTRAINT fk_report_doctor FOREIGN KEY (doctor_id) REFERENCES sys_user(user_id)
);

-- 创建索引
CREATE INDEX idx_report_case_id ON report_info(case_id);
CREATE INDEX idx_report_doctor_id ON report_info(doctor_id);
CREATE INDEX idx_report_status ON report_info(report_status);
CREATE INDEX idx_report_case_doctor ON report_info(case_id, doctor_id);

-- 添加注释
COMMENT ON TABLE report_info IS '报告表';
COMMENT ON COLUMN report_info.report_id IS '报告ID';
COMMENT ON COLUMN report_info.case_id IS '所属病例ID';
COMMENT ON COLUMN report_info.report_status IS '报告状态：NONE/AI_DRAFT/EDITING/SIGNED';
COMMENT ON COLUMN report_info.ai_findings IS 'AI生成的影像所见';
COMMENT ON COLUMN report_info.ai_impression IS 'AI生成的影像印象';
COMMENT ON COLUMN report_info.final_findings IS '医生最终影像所见';
COMMENT ON COLUMN report_info.final_impression IS '医生最终影像印象';
COMMENT ON COLUMN report_info.similar_case_ids IS '相似病例ID列表(JSON)';
COMMENT ON COLUMN report_info.doctor_id IS '签发医生ID';
COMMENT ON COLUMN report_info.sign_time IS '签发时间';
COMMENT ON COLUMN report_info.ai_generate_time IS 'AI报告生成时间';
COMMENT ON COLUMN report_info.created_at IS '创建时间';
COMMENT ON COLUMN report_info.updated_at IS '更新时间';
