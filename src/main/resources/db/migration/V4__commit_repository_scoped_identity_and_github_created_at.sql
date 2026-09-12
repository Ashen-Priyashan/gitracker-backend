-- Migration V4: Scope commit SHA uniqueness to repository and preserve GitHub account creation date

-- 1. Add github_created_at to github_users
ALTER TABLE github_users ADD COLUMN IF NOT EXISTS github_created_at TIMESTAMP;

-- 2. Ensure commit columns exist for synchronization
ALTER TABLE commits ADD COLUMN IF NOT EXISTS author_email VARCHAR(255);
ALTER TABLE commits ADD COLUMN IF NOT EXISTS additions INTEGER;
ALTER TABLE commits ADD COLUMN IF NOT EXISTS deletions INTEGER;

-- 3. Drop global uniqueness constraint and indexes on commits(sha) if present
ALTER TABLE commits DROP CONSTRAINT IF EXISTS uk_commits_sha;
ALTER TABLE commits DROP CONSTRAINT IF EXISTS uk9hdymug1w8yf7uqi6fh8nftfd;
ALTER TABLE commits DROP CONSTRAINT IF EXISTS commits_sha_key;

DROP INDEX IF EXISTS uk_commits_sha;
DROP INDEX IF EXISTS uk9hdymug1w8yf7uqi6fh8nftfd;
DROP INDEX IF EXISTS commits_sha_key;

-- 4. Create composite unique index on (repository_id, sha)
CREATE UNIQUE INDEX IF NOT EXISTS uk_commits_repository_sha ON commits (repository_id, sha);

-- 5. Ensure developer_scores columns align with application entity model
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS total_score INTEGER;
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS consistency_score INTEGER;
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS collaboration_score INTEGER;
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS diversity_score INTEGER;

