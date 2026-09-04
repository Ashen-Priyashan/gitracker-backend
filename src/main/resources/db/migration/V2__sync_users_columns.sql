-- Ensure users table columns exist for authentication
ALTER TABLE users ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email);
