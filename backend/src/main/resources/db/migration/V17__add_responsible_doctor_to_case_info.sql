ALTER TABLE case_info
    ADD COLUMN IF NOT EXISTS responsible_doctor_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_case_info_responsible_doctor
    ON case_info(responsible_doctor_id);

COMMENT ON COLUMN case_info.responsible_doctor_id IS '责任医生ID';
