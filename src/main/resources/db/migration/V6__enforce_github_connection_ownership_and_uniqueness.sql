-- Migration V6: Enforce GitHub Connection Ownership and Uniqueness
-- Ensures at most one GitHubAppConnection per GitHub account (github_id)
-- and at most one GitTracker user owner per GitHub connection (github_connection_id)

-- 0. Reconcile schema: ensure all GitHubAppConnection JPA entity columns exist
ALTER TABLE github_app_connections ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE github_app_connections ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- 1. Defensively deduplicate any pre-existing duplicate github_app_connections by github_id
-- Keeps the most recently synced or connected record
DELETE FROM github_app_connections
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY github_id
            ORDER BY COALESCE(last_synced_at, connected_at, NOW()) DESC, id DESC
        ) as rn
        FROM github_app_connections
        WHERE github_id IS NOT NULL
    ) dup
    WHERE dup.rn > 1
);

-- 2. Defensively reconcile any pre-existing duplicate user associations to the same connection
-- Keeps the association for the most recently updated or created user, setting others to NULL
UPDATE users
SET github_connection_id = NULL
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY github_connection_id
            ORDER BY COALESCE(updated_at, created_at, NOW()) DESC, id DESC
        ) as rn
        FROM users
        WHERE github_connection_id IS NOT NULL
    ) dup
    WHERE dup.rn > 1
);

-- 3. Drop legacy or Hibernate-generated random constraints if present
ALTER TABLE github_app_connections DROP CONSTRAINT IF EXISTS uk4rlmo9j1jqgcdxb8nuwa5ipju;
ALTER TABLE github_app_connections DROP CONSTRAINT IF EXISTS uk_github_app_connections_github_id;
ALTER TABLE users DROP CONSTRAINT IF EXISTS ukjwnb3rbwps19r8cme7lbdifbx;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_github_connection_id;

DROP INDEX IF EXISTS uk_github_app_connections_github_id;
DROP INDEX IF EXISTS uk_users_github_connection_id;

-- 4. Create explicit unique index on github_app_connections(github_id)
CREATE UNIQUE INDEX uk_github_app_connections_github_id ON github_app_connections (github_id);

-- 5. Create explicit unique index on users(github_connection_id) for non-null values
CREATE UNIQUE INDEX uk_users_github_connection_id ON users (github_connection_id) WHERE github_connection_id IS NOT NULL;
