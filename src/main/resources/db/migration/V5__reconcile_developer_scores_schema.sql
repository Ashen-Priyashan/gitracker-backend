-- Migration V5: Reconcile developer_scores schema with application entity model

-- 1. Ensure user_id column exists (referencing users.id)
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- 2. Add application score columns if they do not exist
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS total_score INTEGER;
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS consistency_score INTEGER;
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS collaboration_score INTEGER;
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS diversity_score INTEGER;

-- 3. Ensure legacy columns exist so migration queries run safely regardless of schema origin
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS github_user_id BIGINT;
ALTER TABLE developer_scores ADD COLUMN IF NOT EXISTS overall_score DOUBLE PRECISION;

-- 4. Migrate relationship from legacy github_user_id to user_id via username matching
UPDATE developer_scores ds
SET user_id = u.id
FROM github_users gu
JOIN users u ON LOWER(u.github_username) = LOWER(gu.username)
WHERE ds.github_user_id = gu.id
  AND ds.user_id IS NULL;

-- 5. Migrate score values from legacy overall_score
UPDATE developer_scores
SET total_score = ROUND(overall_score)::INTEGER
WHERE total_score IS NULL AND overall_score IS NOT NULL;

-- 6. Drop legacy constraints and columns
ALTER TABLE developer_scores DROP CONSTRAINT IF EXISTS developer_scores_github_user_id_fkey;
ALTER TABLE developer_scores DROP CONSTRAINT IF EXISTS developer_scores_github_user_id_key;
ALTER TABLE developer_scores DROP COLUMN IF EXISTS github_user_id;
ALTER TABLE developer_scores DROP COLUMN IF EXISTS overall_score;
ALTER TABLE developer_scores DROP COLUMN IF EXISTS impact_score;

-- 7. Ensure foreign key and unique index on user_id
ALTER TABLE developer_scores DROP CONSTRAINT IF EXISTS fk_developer_scores_user_id;
ALTER TABLE developer_scores DROP CONSTRAINT IF EXISTS fkhwlmyonayggpxot786h3900m6;
ALTER TABLE developer_scores ADD CONSTRAINT fk_developer_scores_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_developer_scores_user_id ON developer_scores(user_id) WHERE user_id IS NOT NULL;
