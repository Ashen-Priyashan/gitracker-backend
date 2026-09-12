-- Migration V3: Deduplicate github_users records and create case-insensitive unique index

-- 1. Create temporary mapping table for canonical user ID (lowest id for each lowercase username)
CREATE TEMP TABLE user_dedup_map AS
SELECT id AS old_id,
       FIRST_VALUE(id) OVER (PARTITION BY LOWER(username) ORDER BY id ASC) AS new_id
FROM github_users;

-- 2. Consolidate user_analytics (keep latest record per canonical user)
CREATE TEMP TABLE analytics_to_keep AS
SELECT id AS keep_id, new_id
FROM (
    SELECT ua.id, m.new_id,
           ROW_NUMBER() OVER (PARTITION BY m.new_id ORDER BY ua.calculated_at DESC NULLS LAST, ua.id DESC) AS rn
    FROM user_analytics ua
    JOIN user_dedup_map m ON ua.github_user_id = m.old_id
) sub
WHERE rn = 1;

DELETE FROM user_analytics
WHERE github_user_id IN (SELECT old_id FROM user_dedup_map)
  AND id NOT IN (SELECT keep_id FROM analytics_to_keep);

UPDATE user_analytics
SET github_user_id = (SELECT k.new_id FROM analytics_to_keep k WHERE k.keep_id = user_analytics.id)
WHERE id IN (SELECT keep_id FROM analytics_to_keep)
  AND github_user_id <> (SELECT k.new_id FROM analytics_to_keep k WHERE k.keep_id = user_analytics.id);

-- 3. Repoint repositories from duplicate users to canonical user ID
UPDATE repositories
SET github_user_id = (SELECT m.new_id FROM user_dedup_map m WHERE m.old_id = repositories.github_user_id)
WHERE github_user_id IN (SELECT old_id FROM user_dedup_map WHERE old_id <> new_id);

-- 4. Deduplicate repositories for the same repo_id and owner (EXCLUDING NULL repo_id)
CREATE TEMP TABLE repo_dedup_map AS
SELECT id AS old_id,
       FIRST_VALUE(id) OVER (PARTITION BY repo_id, github_user_id ORDER BY id ASC) AS new_id
FROM repositories
WHERE repo_id IS NOT NULL;

-- 5. Repoint child tables of repositories
-- Commit deduplication: remove duplicate commits from old repository if canonical repository already has the same SHA
DELETE FROM commits c_old
WHERE c_old.repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id)
  AND c_old.sha IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM commits c_canon
      JOIN repo_dedup_map r ON r.new_id = c_canon.repository_id
      WHERE r.old_id = c_old.repository_id
        AND c_canon.sha = c_old.sha
  );

UPDATE commits
SET repository_id = (SELECT r.new_id FROM repo_dedup_map r WHERE r.old_id = commits.repository_id)
WHERE repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id);

-- If multiple commits with the same (repository_id, sha) exist, retain lowest id
DELETE FROM commits
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY repository_id, sha ORDER BY id ASC) as rn
        FROM commits
        WHERE sha IS NOT NULL
    ) sub
    WHERE sub.rn > 1
);

-- Issue deduplication
DELETE FROM issues i_old
WHERE i_old.repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id)
  AND i_old.issue_number IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM issues i_canon
      JOIN repo_dedup_map r ON r.new_id = i_canon.repository_id
      WHERE r.old_id = i_old.repository_id
        AND i_canon.issue_number = i_old.issue_number
  );

UPDATE issues
SET repository_id = (SELECT r.new_id FROM repo_dedup_map r WHERE r.old_id = issues.repository_id)
WHERE repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id);

-- Pull request deduplication
DELETE FROM pull_requests pr_old
WHERE pr_old.repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id)
  AND pr_old.pr_number IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM pull_requests pr_canon
      JOIN repo_dedup_map r ON r.new_id = pr_canon.repository_id
      WHERE r.old_id = pr_old.repository_id
        AND pr_canon.pr_number = pr_old.pr_number
  );

UPDATE pull_requests
SET repository_id = (SELECT r.new_id FROM repo_dedup_map r WHERE r.old_id = pull_requests.repository_id)
WHERE repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id);

-- Language statistics deduplication
DELETE FROM language_statistics ls_old
WHERE ls_old.repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id)
  AND EXISTS (
      SELECT 1 FROM language_statistics ls_canon
      JOIN repo_dedup_map r ON r.new_id = ls_canon.repository_id
      WHERE r.old_id = ls_old.repository_id
        AND ls_canon.language = ls_old.language
  );

UPDATE language_statistics
SET repository_id = (SELECT r.new_id FROM repo_dedup_map r WHERE r.old_id = language_statistics.repository_id)
WHERE repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id);

-- 6. Preserve repository topics from duplicate repositories into canonical repository
INSERT INTO repository_topics (repository_id, topic)
SELECT DISTINCT (SELECT m.new_id FROM repo_dedup_map m WHERE m.old_id = rt.repository_id), rt.topic
FROM repository_topics rt
WHERE rt.repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id)
  AND NOT EXISTS (
      SELECT 1 FROM repository_topics existing
      WHERE existing.repository_id = (SELECT m.new_id FROM repo_dedup_map m WHERE m.old_id = rt.repository_id)
        AND existing.topic = rt.topic
  );

-- Delete topics belonging to duplicate repo rows
DELETE FROM repository_topics
WHERE repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id);

-- 7. Delete duplicate repositories
DELETE FROM repositories
WHERE id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id);

-- 8. Delete duplicate github_users records
DELETE FROM github_users
WHERE id IN (SELECT old_id FROM user_dedup_map WHERE old_id <> new_id);

-- Clean up temporary tables
DROP TABLE IF EXISTS analytics_to_keep;
DROP TABLE IF EXISTS repo_dedup_map;
DROP TABLE IF EXISTS user_dedup_map;

-- 9. Constraints & Indexing
ALTER TABLE github_users DROP CONSTRAINT IF EXISTS ukfi4b0a6nteoj83kd8uu6b0df0;
DROP INDEX IF EXISTS idx_github_users_username;
DROP INDEX IF EXISTS uk_github_users_username_lower;

CREATE UNIQUE INDEX IF NOT EXISTS uk_github_users_username_lower ON github_users (LOWER(username));
