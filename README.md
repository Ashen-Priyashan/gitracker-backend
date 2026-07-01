[![Backend CI](https://github.com/Ashen-Priyashan/gitracker-backend/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Ashen-Priyashan/gitracker-backend/actions/workflows/backend-ci.yml)

# GitTracker Backend

Backend service for **GitTracker**, a GitHub Profile Analytics platform built with **Spring Boot**. It integrates with the GitHub REST API to fetch user data, stores information in PostgreSQL, caches responses with Redis, and provides analytics through REST APIs.

---

## ✨ Features

- Retrieve GitHub user profile information
- Retrieve GitHub repositories
- Store GitHub users and repositories in PostgreSQL
- Generate repository analytics
  - Total repositories
  - Total stars
  - Total forks
  - Most used programming language
- Redis caching for improved performance
- Global exception handling
- Interactive Swagger/OpenAPI documentation
- Unit and integration tests
- GitHub Actions Continuous Integration (CI)

---

## 🛠 Tech Stack

- Java 21
- Spring Boot 4
- Spring Data JPA
- PostgreSQL
- Redis
- Docker
- Maven
- Swagger / OpenAPI
- JUnit 5
- Mockito
- GitHub Actions

---

## 🏗 Architecture

```
                +------------------+
                |   React Frontend |
                +---------+--------+
                          |
                          v
                +------------------+
                | Spring Boot API  |
                +---------+--------+
                          |
          +---------------+---------------+
          |                               |
          v                               v
+-------------------+          +-------------------+
| PostgreSQL        |          | Redis Cache       |
+-------------------+          +-------------------+
                          |
                          v
                 GitHub REST API
```

---

## 📚 API Documentation

Swagger UI is available after starting the application:

```
http://localhost:8080/swagger-ui/index.html
```

OpenAPI specification:

```
http://localhost:8080/v3/api-docs
```

---

## 🚀 Getting Started

### Prerequisites

- Java 21
- Maven
- Docker Desktop

### Start PostgreSQL and Redis

```bash
docker start gitracker-postgres
docker start gitracker-redis
```

### Run the application

```bash
./mvnw spring-boot:run
```

---

## 🧪 Running Tests

Run all tests:

```bash
./mvnw test
```

---

## ⚙️ Continuous Integration

GitHub Actions automatically:

- Builds the project
- Runs all unit tests
- Runs all integration tests
- Verifies the application on every push and pull request

---

## 📁 Project Structure

```
src
├── client
├── config
├── controller
├── dto
├── entity
├── exception
├── repository
└── service
```

---

## 👨‍💻 Author

**Ashen Priyashan**

GitHub: https://github.com/Ashen-Priyashan