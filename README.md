# GitTracker Backend

The GitTracker backend is a Spring Boot REST API that fetches public GitHub profile and repository data, stores it in PostgreSQL, caches results in Redis, and provides analytics for the GitTracker frontend.

## What It Does

- Retrieves public GitHub user profiles and repositories through the GitHub REST API
- Stores user and repository data in PostgreSQL
- Caches application data with Redis
- Calculates totals for repositories, stars, forks, and most-used languages
- Returns profile summaries, repository details, README content, and analytics through REST endpoints
- Provides interactive API documentation with Swagger UI
- Allows the frontend at `http://localhost:5173` through CORS

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Web MVC and Spring Data JPA
- PostgreSQL 17
- Redis 8
- Maven Wrapper
- Springdoc OpenAPI / Swagger UI
- JUnit and H2 for testing
- Docker Compose for PostgreSQL and Redis

## Prerequisites

- Java 21
- Docker Desktop

Maven does not need to be installed separately; the Maven Wrapper is included.

## Run Locally

1. Start PostgreSQL and Redis:

   ```bash
   docker compose up -d
   ```

   This starts:

   | Service | Host port | Container port |
   | --- | --- | --- |
   | PostgreSQL | `5433` | `5432` |
   | Redis | `6379` | `6379` |

2. Configure environment variables. The defaults match the Docker Compose configuration, but a GitHub token is recommended to avoid low unauthenticated GitHub API limits.

   PowerShell example:

   ```powershell
   $env:DB_URL = "jdbc:postgresql://localhost:5433/gitracker"
   $env:DB_USERNAME = "postgres"
   $env:DB_PASSWORD = "postgres"
   $env:REDIS_HOST = "localhost"
   $env:REDIS_PORT = "6379"
   $env:GITHUB_API_BASE_URL = "https://api.github.com"
   $env:GITHUB_API_TOKEN = "your_github_personal_access_token"
   ```

   `.env.example` lists the same variables. Do not commit a real GitHub personal access token.

3. Start the API:

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

The API runs at `http://localhost:8080`.

## API Documentation

When the application is running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Main Endpoints

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/health` | Checks whether the API is running. |
| `GET` | `/api/profile/{username}` | Returns a combined profile summary, repositories, and analytics. |
| `GET` | `/api/users/{username}` | Returns a GitHub user profile. |
| `GET` | `/api/users/{username}/repositories` | Returns public repositories for a user. |
| `GET` | `/api/users/{username}/repositories/{repoName}/readme` | Returns repository README content. |
| `GET` | `/api/users/{username}/language-analytics` | Returns language analytics for a user. |
| `GET` | `/api/users/{username}/repository-analytics` | Returns repository analytics for a user. |
| `GET` | `/api/analytics/{username}` | Generates user-level analytics. |
| `GET` | `/api/github/authorize` | Initiates GitHub App authorization flow. |
| `GET` | `/api/github/callback` | Handles OAuth callback from GitHub. |
| `GET` | `/api/github/status` | Returns active session GitHub connection status. |
| `POST` | `/api/github/disconnect` | Disconnects active GitHub account connection. |
| `GET` | `/api/github/user/repositories` | Returns authenticated user's repositories (including private repos). |

Example:

```text
GET http://localhost:8080/api/profile/octocat
```

## GitHub App Setup & Authorization

Gitracker allows users to connect their GitHub account via a GitHub App to access authorized data and private repositories.

### Creating the GitHub App

1. Go to **GitHub Settings** -> **Developer Settings** -> **GitHub Apps** -> **New GitHub App**.
2. Configure App settings:
   - **App name**: `Gitracker Local` (or your preferred name)
   - **Homepage URL**: `http://localhost:5173`
   - **Callback URL**: `http://localhost:8080/api/github/callback`
   - **Expire user authorization tokens**: Optional (Checked recommended)
3. Permissions:
   - **Repository permissions**:
     - `Contents`: Read-only (for repository code and READMEs)
     - `Metadata`: Read-only (for repository metadata, stars, topics)
   - **Account permissions**:
     - `User permissions` -> `Email addresses`: Read-only
     - `User permissions` -> `Profile`: Read-only
4. Save the app and generate:
   - **Client ID**
   - **Client Secret**
   - **Private Key** (`.pem` file)

### Required Environment Variables

Set the following environment variables in your `.env` or system environment:

```env
GITHUB_APP_ID=your_github_app_id
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret
GITHUB_PRIVATE_KEY="-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----"
GITHUB_REDIRECT_URI=http://localhost:8080/api/github/callback
FRONTEND_URL=http://localhost:5173
```

> [!CAUTION]
> Never commit real secrets, client secrets, or private keys to source control.

### Connecting GitHub Account Flow

1. Start both backend (`http://localhost:8080`) and frontend (`http://localhost:5173`).
2. Navigate to `http://localhost:5173/settings`.
3. Under **GitHub Account Connection**, click **Connect GitHub**.
4. Authorize Gitracker on the GitHub authorization page.
5. GitHub will redirect back to `http://localhost:8080/api/github/callback`, which establishes the session and redirects to `http://localhost:5173/settings?connected=true`.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5433/gitracker` | PostgreSQL JDBC connection URL. |
| `DB_USERNAME` | `postgres` | PostgreSQL username. |
| `DB_PASSWORD` | `postgres` | PostgreSQL password. |
| `REDIS_HOST` | `localhost` | Redis host. |
| `REDIS_PORT` | `6379` | Redis port. |
| `GITHUB_API_BASE_URL` | `https://api.github.com` | GitHub REST API base URL. |
| `GITHUB_API_TOKEN` | Empty | GitHub personal access token. |
| `GITHUB_APP_ID` | Empty | GitHub App ID. |
| `GITHUB_CLIENT_ID` | Empty | GitHub App OAuth Client ID. |
| `GITHUB_CLIENT_SECRET` | Empty | GitHub App OAuth Client Secret. |
| `GITHUB_PRIVATE_KEY` | Empty | GitHub App Private Key. |
| `GITHUB_REDIRECT_URI` | `http://localhost:8080/api/github/callback` | OAuth redirect URI. |
| `FRONTEND_URL` | `http://localhost:5173` | Frontend application URL. |


## Testing and Build

Run the test suite:

```bash
./mvnw test
```

On Windows:

```powershell
.\mvnw.cmd test
```

Tests use an in-memory H2 database configuration.

Create a production JAR:

```bash
./mvnw clean package
```

## Project Structure

```text
src/main/java/com/gitracker/
├── client/       # GitHub API client
├── config/       # Cache, CORS, and OpenAPI configuration
├── controller/   # REST endpoints
├── dto/          # API request and response models
├── entity/       # JPA entities
├── exception/    # Error handling
├── repository/   # Spring Data repositories
└── service/      # GitHub integration and analytics logic
```

## Stop the Supporting Services

```bash
docker compose down
```

This stops PostgreSQL and Redis while preserving the named PostgreSQL volume. Use `docker compose down -v` only if you intentionally want to remove the local database data.
