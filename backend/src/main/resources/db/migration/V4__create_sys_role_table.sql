-- 创建角色表
CREATE TABLE sys_role (
    role_id BIGINT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    remark VARCHAR(256)
);

-- 创建索引
CREATE UNIQUE INDEX idx_role_code ON sys_role(role_code);

-- 插入预置角色数据
INSERT INTO sys_role (role_id, role_code, role_name, remark) VALUES
(1, 'DOCTOR', '放射科医生', '负责查看病例、编辑和签发报告'),
(2, 'QC', '管理与质控', '负责质控统计、典型病例管理'),
(3, 'ADMIN', '系统管理员', '负责系统配置、用户管理');

-- 添加注释
COMMENT ON TABLE sys_role IS '角色表';
COMMENT ON COLUMN sys_role.role_id IS '角色ID';
COMMENT ON COLUMN sys_role.role_code IS '角色编码';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.remark IS '角色说明';
