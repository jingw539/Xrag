-- 创建用户表
CREATE TABLE sys_user (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    real_name VARCHAR(64),
    role_id BIGINT NOT NULL,
    department VARCHAR(64),
    status SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role(role_id)
);

-- 创建索引
CREATE UNIQUE INDEX idx_username ON sys_user(username);
CREATE INDEX idx_role_id ON sys_user(role_id);
CREATE INDEX idx_status ON sys_user(status);

-- 添加注释
COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.user_id IS '用户ID';
COMMENT ON COLUMN sys_user.username IS '登录账号';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希值';
COMMENT ON COLUMN sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN sys_user.role_id IS '所属角色ID';
COMMENT ON COLUMN sys_user.department IS '所属科室';
COMMENT ON COLUMN sys_user.status IS '账号状态：1=启用，0=禁用';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '更新时间';
