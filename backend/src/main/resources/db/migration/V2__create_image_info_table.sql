-- 创建影像表
CREATE TABLE image_info (
    image_id BIGINT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL,
    view_position VARCHAR(32),
    img_width INT,
    img_height INT,
    shoot_time TIMESTAMP,
    dicom_uid VARCHAR(128),
    study_uid VARCHAR(128),
    series_uid VARCHAR(128),
    instance_uid VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_case_id FOREIGN KEY (case_id) REFERENCES case_info(case_id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_case_id ON image_info(case_id);
CREATE INDEX idx_dicom_uid ON image_info(dicom_uid);

-- 添加表注释
COMMENT ON TABLE image_info IS '影像信息表';
COMMENT ON COLUMN image_info.image_id IS '影像唯一标识';
COMMENT ON COLUMN image_info.case_id IS '所属病例ID';
COMMENT ON COLUMN image_info.file_path IS 'MinIO中的对象路径';
COMMENT ON COLUMN image_info.file_name IS '原始文件名';
COMMENT ON COLUMN image_info.file_type IS '文件类型：JPG/PNG/DICOM';
COMMENT ON COLUMN image_info.file_size IS '文件大小（字节）';
COMMENT ON COLUMN image_info.view_position IS '投照体位';
COMMENT ON COLUMN image_info.img_width IS '图像宽度（像素）';
COMMENT ON COLUMN image_info.img_height IS '图像高度（像素）';
COMMENT ON COLUMN image_info.shoot_time IS '拍摄时间';
COMMENT ON COLUMN image_info.dicom_uid IS 'DICOM UID，预留PACS对接';
COMMENT ON COLUMN image_info.study_uid IS 'Study UID，预留PACS对接';
COMMENT ON COLUMN image_info.series_uid IS 'Series UID，预留PACS对接';
COMMENT ON COLUMN image_info.instance_uid IS 'Instance UID，预留PACS对接';
COMMENT ON COLUMN image_info.created_at IS '创建时间';
