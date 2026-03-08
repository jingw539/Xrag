-- 创建系统配置表
CREATE TABLE sys_config (
    config_id BIGINT PRIMARY KEY,
    config_key VARCHAR(64) NOT NULL UNIQUE,
    config_value VARCHAR(256) NOT NULL,
    description VARCHAR(256),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_config_user FOREIGN KEY (updated_by) REFERENCES sys_user(user_id)
);

-- 创建索引
CREATE UNIQUE INDEX idx_config_key ON sys_config(config_key);
CREATE INDEX idx_config_updated_by ON sys_config(updated_by);

-- 插入预置配置数据
INSERT INTO sys_config (config_id, config_key, config_value, description) VALUES
(1, 'retrieval_top_k', '5', '检索返回相似病例数量'),
(2, 'similarity_threshold', '0.75', '相似度阈值'),
(3, 'critical_labels', 'Pneumothorax,Pneumonia', '危急值病理标签列表'),
(4, 'ai_report_timeout', '30', 'AI报告生成超时秒数'),
(5, 'pacs_shared_dir', '/data/pacs', 'PACS影像共享目录路径(预留)'),
(6, 'his_api_url', 'http://his.hospital.com/api', 'HIS接口URL(预留)');

-- 添加注释
COMMENT ON TABLE sys_config IS '系统配置表';
COMMENT ON COLUMN sys_config.config_id IS '配置ID';
COMMENT ON COLUMN sys_config.config_key IS '配置键';
COMMENT ON COLUMN sys_config.config_value IS '配置值';
COMMENT ON COLUMN sys_config.description IS '配置描述';
COMMENT ON COLUMN sys_config.updated_by IS '更新人ID';
COMMENT ON COLUMN sys_config.updated_at IS '更新时间';
