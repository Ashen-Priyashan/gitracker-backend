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
| `GET` | `/actuator/health` | Spring Boot Actuator comprehensive health check. |
| `GET` | `/actuator/health/liveness` | Kubernetes/Docker liveness probe. |
| `GET` | `/actuator/health/readiness` | Kubernetes/Docker readiness probe. |
| `GET` | `/api/profile/{username}` | Returns a combined profile summary, repositories, and analytics. |
| `GET` | `/api/users/{username}` | Returns a GitHub user profile. |
| `GET` | `/api/users/{username}/repositories` | Returns public repositories for a user. |
| `GET` | `/api/users/{username}/repositories/{repoName}/readme` | Returns repository README content. |
| `GET` | `/api/repositories/{username}/{repoName}` | Returns single repository details. |
| `GET` | `/api/users/{username}/language-analytics` | Returns language analytics for a user. |
| `GET` | `/api/users/{username}/repository-analytics` | Returns repository analytics for a user. |
| `GET` | `/api/analytics/{username}` | Generates user-level analytics. |
| `GET` | `/api/auth/login` | User session login endpoint. |
| `GET` | `/api/auth/signup` | User account registration. |
| `POST` | `/api/auth/logout` | Terminates active user session (requires CSRF). |
| `GET` | `/api/auth/me` | Current authenticated user profile and issues XSRF-TOKEN cookie. |
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
GITHUB_CLIENT_ID=your_github_app_client_id
GITHUB_CLIENT_SECRET=your_github_app_client_secret
GITHUB_PRIVATE_KEY="-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----"
GITHUB_REDIRECT_URI=http://localhost:8080/api/github/callback
FRONTEND_URL=http://localhost:5173
ENCRYPTION_SECRET_KEY=your_secure_32_character_encryption_key_here!
```

> [!CAUTION]
> Never commit real secrets, client secrets, or private keys to source control.

## Database Migrations (Flyway)

Database schema migrations are managed automatically by Flyway in `src/main/resources/db/migration/`.
- `V1__initial_schema.sql`: Establishes initial PostgreSQL tables (users, github_users, repositories, user_analytics, repository_analytics, language_analytics) and indexes.
- In production (`prod` profile), Hibernate DDL is set to `validate`, ensuring no unverified schema modifications occur.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile (`dev` or `prod`). |
| `DB_URL` | `jdbc:postgresql://localhost:5433/gitracker` | PostgreSQL JDBC connection URL. |
| `DB_USERNAME` | `postgres` | PostgreSQL username. |
| `DB_PASSWORD` | `postgres` | PostgreSQL password. |
| `CACHE_TYPE` | `none` | Cache backend provider (`none` or `redis`). |
| `REDIS_HOST` | `localhost` | Redis host. |
| `REDIS_PORT` | `6379` | Redis port. |
| `ENCRYPTION_SECRET_KEY` | Empty | AES-256-GCM 32-character secret key for encrypting OAuth tokens. |
| `GITHUB_API_BASE_URL` | `https://api.github.com` | GitHub REST API base URL. |
| `GITHUB_API_TOKEN` | Empty | GitHub personal access token. |
| `GITHUB_API_CONNECT_TIMEOUT_SECONDS` | `5` | Connect timeout for GitHub API HTTP requests. |
| `GITHUB_API_READ_TIMEOUT_SECONDS` | `10` | Read timeout for GitHub API HTTP requests. |
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
$env:MAVEN_OPTS = "-Xms128m -Xmx384m"
.\mvnw.cmd test "-DargLine=-Xmx256m"
```

Tests use an in-memory H2 database configuration and mock external GitHub calls.

Create a production JAR:

```bash
./mvnw clean package -DskipTests
```

## Running the Full Stack with Docker Compose

To build and run all services (PostgreSQL, Redis, Backend, and Frontend) with full healthchecks:

```bash
docker compose up --build -d
```

| Service | Host Port | Internal Port | Healthcheck |
| --- | --- | --- | --- |
| `postgres` | `5433` | `5432` | `pg_isready` |
| `redis` | `6379` | `6379` | `redis-cli ping` |
| `backend` | `8080` | `8080` | `/actuator/health` |
| `frontend` | `5173` | `80` | Nginx SPA |

To stop the services:

```bash
docker compose down
```
