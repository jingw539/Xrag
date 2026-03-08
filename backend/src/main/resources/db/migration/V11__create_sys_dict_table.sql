-- 创建数据字典表
CREATE TABLE sys_dict (
    dict_id BIGINT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL UNIQUE,
    dict_name VARCHAR(64) NOT NULL
);

-- 创建索引
CREATE UNIQUE INDEX idx_dict_code ON sys_dict(dict_code);

-- 插入预置字典分类
INSERT INTO sys_dict (dict_id, dict_code, dict_name) VALUES
(1, 'REPORT_STATUS', '报告状态'),
(2, 'PATHOLOGY_LABEL', '病理标签'),
(3, 'VIEW_POSITION', '投照体位'),
(4, 'FILE_TYPE', '影像文件类型');

-- 添加注释
COMMENT ON TABLE sys_dict IS '数据字典表';
COMMENT ON COLUMN sys_dict.dict_id IS '字典ID';
COMMENT ON COLUMN sys_dict.dict_code IS '字典编码';
COMMENT ON COLUMN sys_dict.dict_name IS '字典名称';
