-- 创建操作日志表
CREATE TABLE sys_operation_log (
    log_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    operation_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64),
    detail TEXT,
    client_ip VARCHAR(64),
    api_path VARCHAR(256),
    elapsed_ms INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_user_id ON sys_operation_log(user_id);
CREATE INDEX idx_operation_type ON sys_operation_log(operation_type);
CREATE INDEX idx_created_at ON sys_operation_log(created_at DESC);

-- 复合索引用于常见查询组合
CREATE INDEX idx_user_id_time ON sys_operation_log(user_id, created_at DESC);
CREATE INDEX idx_operation_type_time ON sys_operation_log(operation_type, created_at DESC);

-- 添加表注释
COMMENT ON TABLE sys_operation_log IS '系统操作日志表';
COMMENT ON COLUMN sys_operation_log.log_id IS '日志唯一标识';
COMMENT ON COLUMN sys_operation_log.user_id IS '操作用户ID';
COMMENT ON COLUMN sys_operation_log.operation_type IS '操作类型：LOGIN=登录，CASE_VIEW=查看病例，CASE_CREATE=创建病例，CASE_UPDATE=更新病例，CASE_DELETE=删除病例，IMAGE_UPLOAD=上传影像，TYPICAL_MARK=标记典型病例';
COMMENT ON COLUMN sys_operation_log.target_id IS '操作目标ID（如病例ID、影像ID等）';
COMMENT ON COLUMN sys_operation_log.detail IS '操作详情描述';
COMMENT ON COLUMN sys_operation_log.client_ip IS '客户端IP地址';
COMMENT ON COLUMN sys_operation_log.api_path IS 'API请求路径';
COMMENT ON COLUMN sys_operation_log.elapsed_ms IS '操作耗时（毫秒）';
COMMENT ON COLUMN sys_operation_log.created_at IS '操作时间';
