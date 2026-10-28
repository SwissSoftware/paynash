# Paynash POS

Restaurant point-of-sale: Spring Boot backend, PostgreSQL, Flutter UI (web first, Android/iOS later).
Planning lives in `openspec/changes/add-pos-ordering-system/`.

## Layout

| Path | Content |
|---|---|
| `backend/` | Java 21, Spring Boot, Maven, Flyway migrations (`src/main/resources/db/migration`) |
| `app/` | Flutter app (waiter and admin), built for web |
| `deploy/` | Docker Compose files |

## Local development

Prerequisites: Java 21 (`.java-version` pins it for jenv), Flutter, and a Docker-compatible runtime
(Docker Desktop or Podman with `docker-compose`).

```sh
# 1. Database
docker-compose -f deploy/docker-compose.dev.yml up -d

# 2. Backend on http://localhost:8081
cd backend && ./mvnw spring-boot:run

# 3. Web UI on http://localhost:5173
cd app && flutter run -d chrome --web-port 5173
```

Backend settings come from environment variables: `POS_DB_URL`, `POS_DB_USER`, `POS_DB_PASSWORD`,
`POS_PORT` (default 8081), `POS_CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`).
The app reads the backend URL from `--dart-define=API_BASE_URL=...` (default `http://localhost:8081`).

## Tests

```sh
cd backend && ./mvnw test   # needs the container runtime (Testcontainers)
cd app && flutter test
```

## API

The backend serves its OpenAPI document at `http://localhost:8081/v3/api-docs`. `OpenApiTest` validates it on
every test run and writes it to `backend/target/openapi.json`; a copy is kept in `docs/api/openapi.json`.

## First login

On an empty database the backend creates the user `admin` and prints a one-time PIN to its log once.
Log in and set a new PIN for `admin` in the staff management screen.
