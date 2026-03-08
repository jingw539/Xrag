-- Widen target_id column to avoid truncation errors when logging operations
-- whose first arg is a DTO or long string
ALTER TABLE sys_operation_log ALTER COLUMN target_id TYPE VARCHAR(256);
