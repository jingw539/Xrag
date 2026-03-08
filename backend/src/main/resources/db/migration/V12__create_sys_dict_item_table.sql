-- 创建数据字典项表
CREATE TABLE sys_dict_item (
    item_id BIGINT PRIMARY KEY,
    dict_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(64) NOT NULL,
    sort_order INT,
    CONSTRAINT fk_dict_item_dict FOREIGN KEY (dict_id) REFERENCES sys_dict(dict_id)
);

-- 创建索引
CREATE INDEX idx_dict_item_dict_id ON sys_dict_item(dict_id);
CREATE INDEX idx_dict_item_code ON sys_dict_item(item_code);

-- 插入报告状态枚举项
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
(1, 1, 'NONE', '未生成', 1),
(2, 1, 'AI_DRAFT', 'AI草稿', 2),
(3, 1, 'EDITING', '编辑中', 3),
(4, 1, 'SIGNED', '已签发', 4);

-- 插入病理标签枚举项（CheXbert 14类）
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
(11, 2, 'Atelectasis', '肺不张', 1),
(12, 2, 'Cardiomegaly', '心脏肥大', 2),
(13, 2, 'Consolidation', '实变', 3),
(14, 2, 'Edema', '水肿', 4),
(15, 2, 'Enlarged Cardiomediastinum', '心纵隔增大', 5),
(16, 2, 'Fracture', '骨折', 6),
(17, 2, 'Lung Lesion', '肺部病变', 7),
(18, 2, 'Lung Opacity', '肺部阴影', 8),
(19, 2, 'No Finding', '无异常发现', 9),
(20, 2, 'Pleural Effusion', '胸腔积液', 10),
(21, 2, 'Pleural Other', '其他胸膜病变', 11),
(22, 2, 'Pneumonia', '肺炎', 12),
(23, 2, 'Pneumothorax', '气胸', 13),
(24, 2, 'Support Devices', '支持设备', 14);

-- 插入投照体位枚举项
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
(31, 3, 'PA', '后前位', 1),
(32, 3, 'AP', '前后位', 2),
(33, 3, 'LATERAL', '侧位', 3),
(34, 3, 'OBLIQUE', '斜位', 4);

-- 插入文件类型枚举项
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
(41, 4, 'JPG', 'JPEG图像', 1),
(42, 4, 'PNG', 'PNG图像', 2),
(43, 4, 'DCM', 'DICOM文件', 3),
(44, 4, 'BMP', 'BMP图像', 4);

-- 添加注释
COMMENT ON TABLE sys_dict_item IS '数据字典项表';
COMMENT ON COLUMN sys_dict_item.item_id IS '字典项ID';
COMMENT ON COLUMN sys_dict_item.dict_id IS '所属字典ID';
COMMENT ON COLUMN sys_dict_item.item_code IS '字典项编码';
COMMENT ON COLUMN sys_dict_item.item_name IS '字典项名称';
COMMENT ON COLUMN sys_dict_item.sort_order IS '排序序号';
