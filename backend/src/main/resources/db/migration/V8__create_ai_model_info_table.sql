-- Create AI model info table
CREATE TABLE ai_model_info (
    model_id BIGINT PRIMARY KEY,
    model_name VARCHAR(128) NOT NULL,
    model_type VARCHAR(64) NOT NULL,
    version VARCHAR(64) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_model_name_version UNIQUE (model_name, version)
);

-- Create indexes
CREATE INDEX idx_model_type ON ai_model_info(model_type);
CREATE UNIQUE INDEX idx_model_name_version ON ai_model_info(model_name, version);

-- Seed data
INSERT INTO ai_model_info (model_id, model_name, model_type, version, description) VALUES
  (2, 'MedCLIP', 'RETRIEVER', 'v1.0', 'Medical image retrieval model'),
  (3, 'GPT-4o', 'REPORT_GEN', 'v1.0', 'Report generation model');

-- Comments
COMMENT ON TABLE ai_model_info IS 'AI model info';
COMMENT ON COLUMN ai_model_info.model_id IS 'Model ID';
COMMENT ON COLUMN ai_model_info.model_name IS 'Model name';
COMMENT ON COLUMN ai_model_info.version IS 'Model version';
COMMENT ON COLUMN ai_model_info.description IS 'Model description';
COMMENT ON COLUMN ai_model_info.created_at IS 'Created at';
