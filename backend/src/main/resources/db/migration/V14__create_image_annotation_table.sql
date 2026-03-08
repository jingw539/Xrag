-- V14: 病灶标注表（AI自动标注 + 医生手动标注）
CREATE TABLE IF NOT EXISTS image_annotation (
    annotation_id  BIGINT        PRIMARY KEY,
    image_id       BIGINT        NOT NULL,
    report_id      BIGINT,
    source         VARCHAR(10)   NOT NULL DEFAULT 'DOCTOR',  -- AI / DOCTOR
    anno_type      VARCHAR(20)   NOT NULL DEFAULT 'RECTANGLE', -- RECTANGLE / CIRCLE
    label          VARCHAR(100),
    remark         TEXT,
    x              DECIMAL(7,4)  NOT NULL,   -- 归一化左上角 X，0~1
    y              DECIMAL(7,4)  NOT NULL,   -- 归一化左上角 Y，0~1
    width          DECIMAL(7,4)  NOT NULL,
    height         DECIMAL(7,4)  NOT NULL,
    color          VARCHAR(20),
    confidence     DECIMAL(5,3),
    created_by     BIGINT,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_annotation_image  ON image_annotation(image_id);
CREATE INDEX IF NOT EXISTS idx_annotation_report ON image_annotation(report_id);
