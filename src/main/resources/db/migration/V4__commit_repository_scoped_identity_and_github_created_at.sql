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

-- 4. Defensively deduplicate any pre-existing duplicate (repository_id, sha) commits
DELETE FROM commits
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY repository_id, sha ORDER BY id ASC) as rn
        FROM commits
        WHERE sha IS NOT NULL
    ) dup
    WHERE dup.rn > 1
);

-- 5. Create composite unique index on (repository_id, sha)
CREATE UNIQUE INDEX IF NOT EXISTS uk_commits_repository_sha ON commits (repository_id, sha);
