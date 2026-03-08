-- 创建病例表
CREATE TABLE case_info (
    case_id BIGINT PRIMARY KEY,
    exam_no VARCHAR(64) NOT NULL UNIQUE,
    patient_anon_id VARCHAR(64) NOT NULL,
    gender CHAR(1),
    age INT,
    exam_time TIMESTAMP NOT NULL,
    body_part VARCHAR(64) NOT NULL,
    department VARCHAR(64),
    report_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    is_typical SMALLINT NOT NULL DEFAULT 0,
    typical_tags VARCHAR(256),
    typical_remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_exam_no ON case_info(exam_no);
CREATE INDEX idx_exam_time ON case_info(exam_time DESC);
CREATE INDEX idx_patient_anon_id ON case_info(patient_anon_id);
CREATE INDEX idx_report_status ON case_info(report_status);
CREATE INDEX idx_is_typical ON case_info(is_typical);

-- 复合索引用于常见查询组合
CREATE INDEX idx_exam_time_status ON case_info(exam_time DESC, report_status);
CREATE INDEX idx_department_time ON case_info(department, exam_time DESC);

-- 添加表注释
COMMENT ON TABLE case_info IS '病例信息表';
COMMENT ON COLUMN case_info.case_id IS '病例唯一标识，使用雪花算法生成';
COMMENT ON COLUMN case_info.exam_no IS '检查号，与HIS/RIS对接的关键字段';
COMMENT ON COLUMN case_info.patient_anon_id IS '患者匿名ID，保护隐私';
COMMENT ON COLUMN case_info.gender IS '性别：M=男，F=女';
COMMENT ON COLUMN case_info.age IS '年龄';
COMMENT ON COLUMN case_info.exam_time IS '检查时间';
COMMENT ON COLUMN case_info.body_part IS '检查部位';
COMMENT ON COLUMN case_info.department IS '科室';
COMMENT ON COLUMN case_info.report_status IS '报告状态：NONE=未生成，AI_DRAFT=AI草稿，EDITING=编辑中，SIGNED=已签发';
COMMENT ON COLUMN case_info.is_typical IS '是否典型病例：0=否，1=是';
COMMENT ON COLUMN case_info.typical_tags IS '典型病例标签，支持多标签（逗号分隔）';
COMMENT ON COLUMN case_info.typical_remark IS '典型病例备注说明';
COMMENT ON COLUMN case_info.created_at IS '创建时间';
COMMENT ON COLUMN case_info.updated_at IS '更新时间';
