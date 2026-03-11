CREATE TABLE IF NOT EXISTS case_info (
    case_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_no VARCHAR(50),
    patient_anon_id VARCHAR(50),
    gender VARCHAR(10),
    age INT,
    exam_time TIMESTAMP,
    body_part VARCHAR(50),
    department VARCHAR(50),
    report_status VARCHAR(20),
    is_typical INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS image_info (
    image_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT,
    file_path VARCHAR(255),
    file_name VARCHAR(255),
    file_type VARCHAR(50),
    file_size BIGINT,
    view_position VARCHAR(50),
    img_width INT,
    img_height INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    pixel_spacing_x_mm DOUBLE,
    pixel_spacing_y_mm DOUBLE
);

CREATE TABLE IF NOT EXISTS retrieval_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_image_id BIGINT,
    created_at TIMESTAMP
);
