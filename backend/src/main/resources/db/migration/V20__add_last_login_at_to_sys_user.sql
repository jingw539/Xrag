ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

COMMENT ON COLUMN sys_user.last_login_at IS 'Last login time';
