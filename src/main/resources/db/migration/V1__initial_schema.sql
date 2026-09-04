-- Initial Schema Migration for Gitracker

CREATE TABLE IF NOT EXISTS github_app_connections (
    id BIGSERIAL PRIMARY KEY,
    github_id BIGINT,
    username VARCHAR(255),
    avatar_url VARCHAR(255),
    access_token VARCHAR(1000),
    token_type VARCHAR(255),
    scope VARCHAR(255),
    connected_at TIMESTAMP,
    last_synced_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    github_username VARCHAR(255),
    github_connection_id BIGINT REFERENCES github_app_connections(id) ON DELETE SET NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS github_users (
    id BIGSERIAL PRIMARY KEY,
    github_id BIGINT,
    username VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    avatar_url VARCHAR(255),
    bio TEXT,
    followers INTEGER,
    following INTEGER,
    public_repos INTEGER,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repositories (
    id BIGSERIAL PRIMARY KEY,
    repo_id BIGINT,
    name VARCHAR(255),
    description TEXT,
    language VARCHAR(255),
    stars INTEGER,
    forks INTEGER,
    watchers INTEGER,
    default_branch VARCHAR(255),
    open_issues INTEGER,
    license_name VARCHAR(255),
    html_url VARCHAR(255),
    size INTEGER,
    archived BOOLEAN,
    github_user_id BIGINT REFERENCES github_users(id) ON DELETE CASCADE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repository_topics (
    repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    topic VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS user_analytics (
    id BIGSERIAL PRIMARY KEY,
    total_repositories INTEGER,
    total_stars INTEGER,
    total_forks INTEGER,
    most_used_language VARCHAR(255),
    calculated_at TIMESTAMP,
    github_user_id BIGINT UNIQUE REFERENCES github_users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS commits (
    id BIGSERIAL PRIMARY KEY,
    sha VARCHAR(255),
    message TEXT,
    author_name VARCHAR(255),
    committed_at TIMESTAMP,
    repository_id BIGINT REFERENCES repositories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pull_requests (
    id BIGSERIAL PRIMARY KEY,
    pr_number BIGINT,
    title VARCHAR(1024),
    state VARCHAR(255),
    created_at TIMESTAMP,
    closed_at TIMESTAMP,
    repository_id BIGINT REFERENCES repositories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS issues (
    id BIGSERIAL PRIMARY KEY,
    issue_number BIGINT,
    title VARCHAR(1024),
    state VARCHAR(255),
    created_at TIMESTAMP,
    closed_at TIMESTAMP,
    repository_id BIGINT REFERENCES repositories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS language_statistics (
    id BIGSERIAL PRIMARY KEY,
    language VARCHAR(255),
    bytes BIGINT,
    repository_id BIGINT REFERENCES repositories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS developer_scores (
    id BIGSERIAL PRIMARY KEY,
    overall_score DOUBLE PRECISION,
    activity_score DOUBLE PRECISION,
    quality_score DOUBLE PRECISION,
    impact_score DOUBLE PRECISION,
    calculated_at TIMESTAMP,
    github_user_id BIGINT UNIQUE REFERENCES github_users(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_github_users_username ON github_users(username);
CREATE INDEX IF NOT EXISTS idx_repositories_user ON repositories(github_user_id);
CREATE INDEX IF NOT EXISTS idx_commits_repo ON commits(repository_id);
CREATE INDEX IF NOT EXISTS idx_pull_requests_repo ON pull_requests(repository_id);
CREATE INDEX IF NOT EXISTS idx_issues_repo ON issues(repository_id);
