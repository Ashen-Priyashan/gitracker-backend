-- Migration V3: Deduplicate github_users records and create case-insensitive unique index

-- 1. Create a temporary mapping table to identify canonical user ID for each lowercase username
CREATE TEMP TABLE user_dedup_map AS
SELECT id AS old_id,
       FIRST_VALUE(id) OVER (PARTITION BY LOWER(username) ORDER BY id ASC) AS new_id
FROM github_users;

-- 2. Consolidate user_analytics
-- For each group of duplicate users, pick the best user_analytics record to keep
CREATE TEMP TABLE analytics_to_keep AS
SELECT DISTINCT ON (m.new_id) ua.id AS keep_id, m.new_id
FROM user_analytics ua
JOIN user_dedup_map m ON ua.github_user_id = m.old_id
ORDER BY m.new_id, ua.calculated_at DESC NULLS LAST, ua.id DESC;

-- Delete all user_analytics records for duplicate groups EXCEPT the chosen keep_id
DELETE FROM user_analytics
WHERE github_user_id IN (SELECT old_id FROM user_dedup_map WHERE old_id IN (SELECT old_id FROM user_dedup_map GROUP BY old_id))
  AND id IN (SELECT ua.id FROM user_analytics ua JOIN user_dedup_map m ON ua.github_user_id = m.old_id WHERE m.old_id <> m.new_id OR m.new_id IN (SELECT new_id FROM user_dedup_map WHERE old_id <> new_id))
  AND id NOT IN (SELECT keep_id FROM analytics_to_keep);

-- Update the kept user_analytics to point to the canonical user
UPDATE user_analytics ua
SET github_user_id = k.new_id
FROM analytics_to_keep k
WHERE ua.id = k.keep_id AND ua.github_user_id <> k.new_id;

-- 3. Repoint repositories from duplicate users to canonical user ID
UPDATE repositories r
SET github_user_id = m.new_id
FROM user_dedup_map m
WHERE r.github_user_id = m.old_id AND m.old_id <> m.new_id;

-- 4. Deduplicate repositories for the same repo_id and same owner
CREATE TEMP TABLE repo_dedup_map AS
SELECT id AS old_id,
       FIRST_VALUE(id) OVER (PARTITION BY repo_id, github_user_id ORDER BY id ASC) AS new_id
FROM repositories;

-- Repoint child tables of repositories
UPDATE commits c
SET repository_id = r.new_id
FROM repo_dedup_map r
WHERE c.repository_id = r.old_id AND r.old_id <> r.new_id;

UPDATE issues i
SET repository_id = r.new_id
FROM repo_dedup_map r
WHERE i.repository_id = r.old_id AND r.old_id <> r.new_id;

UPDATE pull_requests pr
SET repository_id = r.new_id
FROM repo_dedup_map r
WHERE pr.repository_id = r.old_id AND r.old_id <> r.new_id;

UPDATE language_statistics ls
SET repository_id = r.new_id
FROM repo_dedup_map r
WHERE ls.repository_id = r.old_id AND r.old_id <> r.new_id;

-- Delete repository topics belonging to duplicate repo rows
DELETE FROM repository_topics rt
WHERE rt.repository_id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id);

-- Delete duplicate repositories
DELETE FROM repositories
WHERE id IN (SELECT old_id FROM repo_dedup_map WHERE old_id <> new_id);

-- 5. Delete duplicate github_users records
DELETE FROM github_users
WHERE id IN (SELECT old_id FROM user_dedup_map WHERE old_id <> new_id);

-- Clean up temporary tables
DROP TABLE IF EXISTS analytics_to_keep;
DROP TABLE IF EXISTS repo_dedup_map;
DROP TABLE IF EXISTS user_dedup_map;

-- 6. Drop any existing conflicting indexes/constraints and create case-insensitive unique index
ALTER TABLE github_users DROP CONSTRAINT IF EXISTS ukfi4b0a6nteoj83kd8uu6b0df0;
DROP INDEX IF EXISTS idx_github_users_username;
DROP INDEX IF EXISTS uk_github_users_username_lower;

CREATE UNIQUE INDEX IF NOT EXISTS uk_github_users_username_lower ON github_users (LOWER(username));
