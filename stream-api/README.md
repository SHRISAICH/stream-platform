# Stream API — Backend Service & Infrastructure

The core backend service for the **Live Stream Management System** (`stream-platform`). Built with Spring Boot 3.3 / Java 21, providing JWT authentication, stream lifecycle and metadata management, SRS media server webhook callbacks, and live viewer metric aggregation.

---

## Architecture Overview
- **REST Controllers**:
  - `AuthController`: User registration and JWT issuance (`/api/auth/register`, `/api/auth/login`).
  - `UserController`: Authenticated user profile retrieval (`/api/users/me`).
  - `StreamController`: Public stream discovery (`/api/streams/public`) and authenticated creator stream CRUD (`/api/streams`).
  - `SrsWebhookController`: Webhook callbacks from SRS (`/api/srs/on-publish`, `/api/srs/on-unpublish`, `/api/srs/on-play`, `/api/srs/on-stop`).
- **Services**:
  - `AuthService` / `AuthServiceImpl`: BCrypt password hashing and authentication tokens.
  - `StreamService` / `StreamServiceImpl`: Stream CRUD, random 16-byte hex streamKey generation, and status mapping.
  - `SrsService` / `SrsServiceImpl`: SRS HTTP API querying (`GET /api/v1/streams/`), publisher deduction, and session tracking.
- **Security**:
  - `SecurityConfig`: Configures stateless `SessionCreationPolicy.STATELESS`, CORS, custom authentication entry point, and access denial handler.
  - `JwtAuthenticationFilter`: Extracts and verifies `Authorization: Bearer <token>` headers.
  - `JwtService`: HMAC-SHA256 signing and claims validation.
- **Data Layer**:
  - `UserRepository` & `StreamRepository` (Spring Data JPA with PostgreSQL).
  - PostgreSQL 16 on port 5432 (`stream_db`).
  - Redis 7 on port 6379 for caching.

---

## Quick Start

### 1. Start Infrastructure Containers
```bash
docker compose up -d
```
Starts PostgreSQL, Redis, MinIO, SRS, and Nginx.

### 2. Run the Application
```bash
./mvnw spring-boot:run
```
Starts Spring Boot on port 8081.

### 3. Run Automated Tests
```bash
./mvnw clean test
```
Executes all unit and MockMvc integration tests (38 tests).

---

## Configuration

Default properties are set in `src/main/resources/application.properties` and can be overridden via environment variables:

| Environment Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8081` | Spring Boot server port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/stream_db` | PostgreSQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `stream_user` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `stream_password` | Database password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SRS_API_URL` | `http://localhost:1985` | SRS management HTTP API endpoint |
| `JWT_SECRET` | `mySuperSecretKeyForJwtAuthentication123456789` | Signing key (min 256 bits) |
