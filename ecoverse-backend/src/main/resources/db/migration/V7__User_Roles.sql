-- ============================================================
-- V7: User Roles
-- Adds role column to users table with safe defaults.
-- All existing users become USER role.
-- ============================================================

-- Add role column with default 'USER' for new rows
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Ensure existing users have the default role (in case they were created before this migration)
UPDATE users SET role = 'USER' WHERE role IS NULL OR role = '';

-- Add check constraint to enforce valid role values
ALTER TABLE users ADD CONSTRAINT chk_users_role_valid
    CHECK (role IN ('USER', 'SELLER', 'ADMIN'));
