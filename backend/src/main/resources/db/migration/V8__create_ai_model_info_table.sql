-- 创建AI模型信息表
CREATE TABLE ai_model_info (
    model_id BIGINT PRIMARY KEY,
    model_name VARCHAR(128) NOT NULL,
    model_type VARCHAR(64) NOT NULL,
    version VARCHAR(64) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_model_name_version UNIQUE (model_name, version)
);

-- 创建索引
CREATE INDEX idx_model_type ON ai_model_info(model_type);
CREATE UNIQUE INDEX idx_model_name_version ON ai_model_info(model_name, version);

-- 插入预置模型数据
INSERT INTO ai_model_info (model_id, model_name, model_type, version, description) VALUES
(1, 'CheXbert', 'CHEXBERT', 'v1.0', 'CheXbert标签提取与评测模型'),
(2, 'MedCLIP', 'RETRIEVER', 'v1.0', '医学影像检索模型'),
(3, 'GPT-4o', 'REPORT_GEN', 'v1.0', '报告生成模型');

-- 添加注释
COMMENT ON TABLE ai_model_info IS 'AI模型信息表';
COMMENT ON COLUMN ai_model_info.model_id IS '模型ID';
COMMENT ON COLUMN ai_model_info.model_name IS '模型名称';
COMMENT ON COLUMN ai_model_info.model_type IS '模型类型：REPORT_GEN/RETRIEVER/CHEXBERT';
COMMENT ON COLUMN ai_model_info.version IS '模型版本';
COMMENT ON COLUMN ai_model_info.description IS '模型描述';
COMMENT ON COLUMN ai_model_info.created_at IS '创建时间';
