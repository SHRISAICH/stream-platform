# StrataLive — Live Stream Management & VOD Platform

A production-grade, containerized streaming and video-on-demand platform engineered with Spring Boot 3, React 19, Simple Realtime Server (SRS 5), MinIO Object Storage, PostgreSQL 16, Redis 7, WebSockets (STOMP), and Nginx.

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Complete Features](#2-complete-features)
3. [System Architecture](#3-system-architecture)
4. [Technology Stack](#4-technology-stack)
5. [Frontend Architecture](#5-frontend-architecture)
6. [Backend Architecture](#6-backend-architecture)
7. [Database Schema](#7-database-schema)
8. [Comprehensive API Documentation](#8-comprehensive-api-documentation)
9. [Authentication & Authorization Flow](#9-authentication--authorization-flow)
10. [Live Streaming Flow](#10-live-streaming-flow)
11. [RTMP, SRS & HLS Packaging Flow](#11-rtmp-srs--hls-packaging-flow)
12. [VOD Video Upload Flow](#12-vod-video-upload-flow)
13. [MinIO S3 Storage Flow](#13-minio-s3-storage-flow)
14. [Real-Time WebSocket & Chat Flow](#14-real-time-websocket--chat-flow)
15. [Notification Engine Flow](#15-notification-engine-flow)
16. [Stream Scheduling Flow](#16-stream-scheduling-flow)
17. [Creator Analytics Engine](#17-creator-analytics-engine)
18. [Admin Management System](#18-admin-management-system)
19. [Docker Stack & Infrastructure Setup](#19-docker-stack--infrastructure-setup)
20. [Environment Variables Reference](#20-environment-variables-reference)
21. [Local Development & Setup Instructions](#21-local-development--setup-instructions)
22. [Testing & Verification Guide](#22-testing--verification-guide)
23. [Deployment Instructions](#23-deployment-instructions)

---

## 1. Project Overview

**StrataLive** is an enterprise-ready live broadcasting and Video-on-Demand (VOD) platform designed for content creators, audiences, and platform administrators.

- **Creators** can schedule broadcasts, launch low-latency RTMP streams using OBS Studio or FFmpeg, upload video content with automated MinIO cloud storage, inspect audience engagement metrics, and monitor real-time chat.
- **SRS (Simple Realtime Server)** ingests RTMP video, verifies stream keys against Spring Boot security webhooks, dynamically packages streams into HLS fragments, and reports viewer sessions.
- **Viewers** can discover public live streams and scheduled broadcasts, stream VODs with full byte-range seeking, participate in real-time chat, and receive platform notifications.
- **Administrators** have dedicated tools to oversee platform health, manage user accounts and roles, and moderate streams, videos, and chat messages.

---

## 2. Complete Features

- **JWT Authentication & Role-Based Access**: Stateless JWT bearer token authentication with BCrypt hashing, role enforcement (`USER`, `ADMIN`), and sensitive data masking.
- **Stream Lifecycle Management**: Full CRUD lifecycle (`OFFLINE`, `SCHEDULED`, `LIVE`, `ENDED`, `CANCELLED`) with cryptographically secure 16-byte hex stream keys.
- **Stream Scheduling**: Broadcasters can schedule future streams with validation, public discovery listings, and automated transitions to `LIVE` upon RTMP handshake.
- **Live RTMP Ingestion**: SRS 5 ingestion at `rtmp://localhost:1935/live/{streamKey}` with validation against backend authentication hooks.
- **HLS Video Delivery**: Low-latency HLS playlist (`.m3u8`) and segment (`.ts`) streaming through Nginx reverse proxy with Hls.js in-browser playback.
- **Audience Metrics & Viewer Counting**: Live concurrency tracking integrated with SRS HTTP APIs and connection session callbacks.
- **Real-Time Live Chat**: STOMP WebSocket-based chat engine with message persistence, moderation capabilities, and broadcast updates.
- **Real-Time Notification System**: User-targeted event alerts for stream lifecycle events (live, ended, scheduled), video processing, and unread counters.
- **Video on Demand (VOD) Uploads**: Multi-format video uploads (MP4, WebM, MKV, MOV) up to 500MB with automated MinIO object storage.
- **HTTP Range Video Seeking**: Full HTTP 206 Partial Content streaming supporting video scrubbing and instant seek positions.
- **Creator Analytics Dashboard**: Aggregated real-time metrics for total streams, duration, peak viewers, video views, and category distribution.
- **Administrator Console**: Platform overview metrics, user directory controls, role promotion/demotion, account suspension, and content moderation.

---

## 3. System Architecture

```text
                                 +--------------------------------------------+
                                 |            Broadcaster / Encoder           |
                                 |            (OBS Studio / FFmpeg)           |
                                 +---------------------+----------------------+
                                                       |
                                            RTMP Ingest (Port 1935)
                                                       v
+------------------------+       +--------------------------------------------+
|  Viewer / Web Browser  |       |          SRS 5.0 Media Server              |
|  - React 19 SPA        |<------+  - Ingestion (1935)                        |
|  - Hls.js Live Player  |  HLS  |  - HLS Remuxer & Fragment Engine (8080)    |
|  - HTML5 Video (Range) | Play  |  - Management API (1985)                   |
+-----------+------------+       +----------+----------------------+----------+
            |                               |                      ^
   HTTP/REST| WebSockets                    | HTTP Webhooks        | Viewer Query
   (Port 80)| (STOMP /ws)                   | (on_publish/stop)    | (/api/v1/streams)
            v                               v                      |
+------------------------------------------------------------------+----------+
|                       Nginx Reverse Proxy (Port 80)                         |
|   - SPA Static Asset Serving (/usr/share/nginx/html)                        |
|   - REST API & WebSockets Proxy -> Backend (Port 8081)                      |
|   - HLS Video Proxy -> SRS (Port 8080)                                      |
+-----------------------------------+-----------------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------------+
|                      Spring Boot 3.3 Backend API (Port 8081)                 |
|   - Security / JWT / Method Security (@PreAuthorize)                        |
|   - StreamService / VideoService / ChatService / NotificationService        |
|   - AnalyticsService / AdminService / SrsService / MinioStorageService      |
+-------------+----------------------+--------------------+-------------------+
              |                      |                    |
       JPA/SQL|               Redis 7|             S3 API |
              v                      v                    v
+-------------------------+  +---------------+  +-----------------------------+
|   PostgreSQL 16 DB      |  |  Redis Cache  |  |  MinIO Object Storage       |
|   (Port 5432)           |  |  (Port 6379)  |  |  (Port 9000 S3 / 9001 Web)  |
| - users, streams,       |  | - Viewer keys |  | - stratalive-videos bucket  |
|   videos, notifications,|  | - Sessions    |  | - S3 stream partial byte    |
|   chat_messages         |  | - Auth tokens |  |   reading                   |
+-------------------------+  +---------------+  +-----------------------------+
```

---

## 4. Technology Stack

### Backend
- **Language**: Java 21 LTS
- **Framework**: Spring Boot 3.3.x (Spring Web, Spring Security, Spring Data JPA, Spring Data Redis, Spring WebSocket / STOMP, Spring Boot Actuator, Jakarta Validation)
- **Database**: PostgreSQL 16
- **Cache & Concurrency**: Redis 7 Alpine
- **Storage Client**: MinIO Java SDK 8.5.10
- **Security**: JJWT (Java JWT) 0.12.5 & BCrypt Password Encoder
- **Build System**: Maven Wrapper (`./mvnw`)

### Media & Live Streaming
- **SRS (Simple Realtime Server) 5.0**: Ultra-low-latency RTMP ingestion, HLS fragment packaging, and HTTP webhook callbacks
- **FFmpeg**: Transcoding, test source simulation, and audio/video remuxing
- **Hls.js**: Production adaptive bitrate HLS client player

### Object Storage
- **MinIO**: High-performance S3-compatible object storage for VOD assets

### Frontend
- **Framework**: React 19 (Hooks, Functional Architecture)
- **Tooling**: Vite 8.2 (Fast Bundler & Dev Server)
- **Design Language**: Glassmorphic Dark Design System with Vanilla CSS tokens
- **Linter**: Oxlint
- **WebSocket Client**: `@stomp/stompjs`

### Deployment & Orchestration
- **Containers**: Docker Engine & Docker Compose v2
- **Reverse Proxy**: Nginx 1.31 (Alpine)

---

## 5. Frontend Architecture

The frontend is located in `stream-web/` and organized into focused, reusable components:
- `App.jsx`: Master application component coordinating global state, token handling, active navigation tabs, player modals, and STOMP WebSocket connections.
- `App.css`: Design system tokens (`--bg-primary`, `--glass-bg`, `--accent-primary`, etc.), grid layouts, responsive breakpoints, animations, and modal styles.
- `components/ScheduleStreamModal.jsx`: Modal for scheduling upcoming streams with future date-time picker and validation.
- `components/AnalyticsView.jsx`: Creator analytics dashboard featuring 6 KPI summary cards, category distribution progress bar, and recent stream logs.
- `components/AdminDashboardView.jsx`: Administrator console featuring Platform Overview KPIs, User Directory management, Stream Moderation, and Video Moderation tables.
- `components/NotificationBell.jsx`: Bell icon in top navigation with unread count badge and toggle dropdown.
- `components/NotificationPanel.jsx`: Dropdown panel displaying notifications, read/unread states, mark-all-as-read, and delete actions.
- `components/LiveChat.jsx`: Embedded stream chat box with live message stream, scroll lock, character limits, and author/moderator deletion.

---

## 6. Backend Architecture

The backend is structured under `stream-api/src/main/java/com/streamplatform/streamapi/`:
- **`config/`**:
  - `SecurityConfig.java`: Stateless JWT security filter chain, method security (`@EnableMethodSecurity`), CORS rules, and public route whitelist.
  - `WebSocketConfig.java`: STOMP message broker configuration (`/topic`, `/queue`, application prefix `/app`, endpoint `/ws`).
  - `MinioConfig.java`: MinIO S3 client singleton with automatic bucket creation.
  - `RedisConfig.java`: Lettuce connection factory and Redis template configurations.
- **`controller/`**:
  - `AuthController.java`: User registration and login endpoints.
  - `UserController.java`: Profile retrieval (`/api/users/me`).
  - `StreamController.java`: Stream CRUD, scheduling, cancellation, and public discovery.
  - `VideoController.java`: Video upload, metadata editing, deletion, and HTTP Range streaming.
  - `ChatController.java`: Stream message dispatch, history loading, and message deletion.
  - `NotificationController.java`: User notifications, unread counts, and read status management.
  - `AnalyticsController.java`: Creator audience and stream analytics.
  - `AdminController.java`: Platform admin metrics, user management, and content moderation.
  - `SrsWebhookController.java`: SRS HTTP callbacks (`on-publish`, `on-unpublish`, `on-play`, `on-stop`).
- **`service/` & `service/impl/`**:
  - Business logic implementations handling security checks, validations, database queries, and real-time broadcasts.
- **`repository/`**:
  - Spring Data JPA repositories with optimized queries (`JOIN FETCH`) eliminating N+1 performance bottlenecks.
- **`security/`**:
  - `JwtTokenProvider.java`, `JwtAuthenticationFilter.java`, `CustomUserDetails.java`.

---

## 7. Database Schema

```sql
-- 1. Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Streams Table
CREATE TABLE streams (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(255) NOT NULL,
    stream_key VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'OFFLINE',
    is_public BOOLEAN NOT NULL DEFAULT true,
    scheduled_start_time TIMESTAMP,
    scheduled_end_time TIMESTAMP,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    duration_seconds BIGINT,
    peak_viewers INTEGER DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Videos Table
CREATE TABLE videos (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    category VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    thumbnail_url VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'READY',
    is_public BOOLEAN NOT NULL DEFAULT true,
    views BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Chat Messages Table
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    content VARCHAR(500) NOT NULL,
    stream_id BIGINT NOT NULL REFERENCES streams(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Notifications Table
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    related_stream_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT false,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 8. Comprehensive API Documentation

### Authentication & Profile
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register new user account |
| `POST` | `/api/auth/login` | Public | Authenticate user and issue JWT |
| `GET` | `/api/users/me` | Authenticated | Retrieve authenticated user profile |

### Live Streams & Scheduling
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/streams/public` | Public | List all public live streams (stream keys hidden) |
| `GET` | `/api/streams/scheduled` | Public | List all scheduled upcoming streams |
| `GET` | `/api/streams/my` | Authenticated | List all streams owned by authenticated user |
| `GET` | `/api/streams/{id}` | Authenticated/Owner | Get specific stream details (includes stream key if owner) |
| `POST` | `/api/streams` | Authenticated | Create a new live stream channel |
| `POST` | `/api/streams/schedule` | Authenticated | Schedule a stream with future start/end time |
| `PUT` | `/api/streams/{id}` | Owner | Update stream metadata (title, category, visibility) |
| `PUT` | `/api/streams/{id}/cancel`| Owner | Cancel a scheduled stream |
| `DELETE`| `/api/streams/{id}` | Owner | Delete a stream channel |

### Video on Demand (VOD)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/videos/public` | Public | List public videos (supports `?category=&search=`) |
| `GET` | `/api/videos/my` | Authenticated | List videos uploaded by current user |
| `GET` | `/api/videos/{id}` | Public / Owner | Get video metadata (private requires ownership) |
| `GET` | `/api/videos/{id}/playback`| Public / Owner | Stream video byte content (supports HTTP Range headers) |
| `POST` | `/api/videos` | Authenticated | Upload video file (multipart form data, max 500MB) |
| `PUT` | `/api/videos/{id}` | Owner | Update video metadata |
| `DELETE`| `/api/videos/{id}` | Owner | Delete video from MinIO storage and database |

### Live Chat
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/streams/{streamId}/chat` | Public / Owner | Get 50 most recent chat messages for stream |
| `POST` | `/api/streams/{streamId}/chat` | Authenticated | Send new message to stream chat (max 500 chars) |
| `DELETE`| `/api/chat/{messageId}` | Author / Stream Owner / Admin | Delete chat message and broadcast removal event |

### Notifications
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/notifications` | Authenticated | Get current user's notifications (sorted newest first) |
| `GET` | `/api/notifications/unread-count`| Authenticated | Get number of unread notifications |
| `PUT` | `/api/notifications/{id}/read` | Authenticated/Owner | Mark specific notification as read |
| `PUT` | `/api/notifications/read-all` | Authenticated | Mark all notifications as read in bulk |
| `DELETE`| `/api/notifications/{id}` | Authenticated/Owner | Delete a notification |

### Creator Analytics
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/analytics/creator` | Authenticated | Retrieve calculated metrics for authenticated creator |

### Admin Console (`ROLE_ADMIN` Required)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/admin/stats` | Admin | Get platform-wide overview statistics |
| `GET` | `/api/admin/users` | Admin | List all registered user accounts |
| `PUT` | `/api/admin/users/{id}/status` | Admin | Enable or suspend user account |
| `PUT` | `/api/admin/users/{id}/role` | Admin | Update user role (`USER` or `ADMIN`) |
| `GET` | `/api/admin/streams` | Admin | List all platform streams |
| `DELETE`| `/api/admin/streams/{id}` | Admin | Delete stream channel as moderator |
| `GET` | `/api/admin/videos` | Admin | List all platform videos |
| `DELETE`| `/api/admin/videos/{id}` | Admin | Delete video from storage and database as moderator |

### SRS Media Server Webhooks
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/srs/on-publish` | SRS Server | Verifies stream key on publish; sets status to `LIVE` |
| `POST` | `/api/srs/on-unpublish` | SRS Server | Triggered when stream stops; sets status to `ENDED`/`OFFLINE` |
| `POST` | `/api/srs/on-play` | SRS Server | Tracks new viewer playback session |
| `POST` | `/api/srs/on-stop` | SRS Server | Removes viewer playback session |

---

## 9. Authentication & Authorization Flow

1. **User Registration**: Client posts username, email, full name, and password to `/api/auth/register`. Passwords are encrypted with BCrypt (10 rounds).
2. **User Login**: Client posts credentials to `/api/auth/login`. Upon authentication, a cryptographically signed HMAC-SHA256 JWT is generated with a 24-hour expiration.
3. **Stateless Bearer Tokens**: Subsequent requests pass the token via `Authorization: Bearer <token>`. `JwtAuthenticationFilter` validates token integrity and loads `CustomUserDetails`.
4. **Role Enforcement**: Normal users receive role `ROLE_USER`. Administrators have role `ROLE_ADMIN`. Protected admin endpoints use `@PreAuthorize("hasRole('ADMIN')")`.
5. **Self-Protection Guards**: Administrators cannot disable or demote their own account.

---

## 10. Live Streaming Flow

```text
[ Broadcaster: OBS Studio / FFmpeg ]
      |
      | 1. Connects via RTMP to rtmp://localhost:1935/live/<streamKey>
      v
[ SRS Media Server (Port 1935) ]
      |
      | 2. HTTP POST Webhook -> http://backend:8081/api/srs/on-publish
      v
[ Spring Boot Backend ]
      |
      | 3. Validates streamKey in PostgreSQL
      |    - If invalid: returns HTTP 403 Forbidden (SRS rejects client)
      |    - If valid: sets stream status = 'LIVE', records startedAt,
      |      dispatches 'STREAM_LIVE' notifications via WebSocket,
      |      returns 0 (SRS accepts RTMP stream)
      v
[ SRS Media Server ]
      |
      | 4. Demuxes FLV/RTMP and packages video into HLS fragments
      |    Generates /live/<streamKey>.m3u8 and .ts segments
      v
[ Nginx Reverse Proxy (Port 80) ]
      |
      | 5. Serves HLS playlist & video chunks at http://localhost/live/<streamKey>.m3u8
      v
[ Viewer Client Browser ]
      |
      | 6. Hls.js downloads playlist, buffers chunks, and plays live video
```

---

## 11. RTMP, SRS & HLS Packaging Flow

- **Ingestion Port**: 1935
- **SRS Conf**: Located at `stream-api/infrastructure/srs/srs.conf`:
  - Low latency HLS fragments: `hls_fragment 5`, `hls_window 30`.
  - HTTP Hooks:
    - `on_publish http://backend:8081/api/srs/on-publish;`
    - `on_unpublish http://backend:8081/api/srs/on-unpublish;`
    - `on_play http://backend:8081/api/srs/on-play;`
    - `on_stop http://backend:8081/api/srs/on-stop;`
- **Reverse Proxy Route**: Nginx forwards `/live/` requests to `http://srs:8080/live/` with CORS headers enabled.

---

## 12. VOD Video Upload Flow

1. Client selects a video file up to 500MB (MP4, WebM, MKV, MOV).
2. Client submits multipart form request to `POST /api/videos`.
3. Backend validates file extension and mime-type.
4. Generates a secure, collision-free object key: `videos/{userId}/{UUID}/{sanitizedFilename}`.
5. Streams input stream directly into MinIO bucket `stratalive-videos`.
6. Saves metadata record in PostgreSQL `videos` table.
7. Dispatches a `VIDEO_READY` notification to the uploader.
8. Returns `VideoResponse` with streaming playback URL `/api/videos/{id}/playback`.

---

## 13. MinIO S3 Storage Flow

- MinIO runs on port 9000 (S3 API) and port 9001 (Web Console).
- On application boot, `MinioConfig` automatically creates the bucket `stratalive-videos` if it does not already exist.
- When streaming video, `VideoServiceImpl.streamVideo()` parses the HTTP `Range` header:
  - If `Range: bytes=start-end` is present, it uses `getObject(GetObjectArgs.builder().offset(start).length(length))` to read only the requested slice and returns `HTTP 206 Partial Content`.
  - When playback starts at byte 0 (`start == 0`), the video view counter is incremented in PostgreSQL.

---

## 14. Real-Time WebSocket & Chat Flow

- **Endpoint**: `/ws` (with fallback to SockJS).
- **Broker**: Simple in-memory STOMP broker enabled for `/topic` and `/queue`.
- **Chat Topic**: `/topic/streams/{streamId}/chat`.
- **Message Dispatch**:
  - Authenticated user posts to `POST /api/streams/{streamId}/chat`.
  - Backend saves message in PostgreSQL and broadcasts `ChatEvent` of type `MESSAGE` to `/topic/streams/{streamId}/chat`.
- **Message Moderation**:
  - Stream owner, author, or platform admin can delete a message via `DELETE /api/chat/{messageId}`.
  - Message is removed from database and a `ChatEvent` of type `DELETE` is broadcasted to remove the message from all active viewers' chat streams instantly.

---

## 15. Notification Engine Flow

- **User Notification Topic**: `/topic/users/{userId}/notifications`.
- **Automated Lifecycle Events**:
  - `STREAM_LIVE`: Triggered when SRS `on_publish` confirms live broadcast. Broadcasts to stream owner and all registered platform users for public streams.
  - `STREAM_ENDED`: Triggered when SRS `on_unpublish` fires.
  - `STREAM_CREATED`: Triggered when creator sets up a channel.
  - `VIDEO_READY`: Triggered when VOD file finishes processing.
- **Client Synchronization**:
  - The notification bell displays an unread count badge.
  - New notifications arriving via STOMP increment the badge and prepend to the active list without page reloads.

---

## 16. Stream Scheduling Flow

1. Creator opens the "Schedule Stream" modal and inputs title, category, visibility, and a future `scheduledStartTime`.
2. Backend validates that `scheduledStartTime` is in the future.
3. Stream is created in PostgreSQL with status `SCHEDULED`.
4. Scheduled streams appear on the public feed with a `SCHEDULED` badge and countdown date/time.
5. When the creator begins broadcasting via RTMP with the stream's key, SRS triggers `on_publish`, safely promoting status from `SCHEDULED` to `LIVE`.
6. When the broadcast concludes, status transitions to `ENDED`, recording duration and peak viewers.

---

## 17. Creator Analytics Engine

The Analytics service computes real-time metrics directly from relational data:
- **Total Streams**: Count of streams created by user.
- **Live / Scheduled / Ended**: Breakdown of streams by lifecycle status.
- **Peak Concurrent Viewers**: Maximum peak viewers achieved across creator's streams.
- **Total Stream Duration**: Cumulative broadcast duration in seconds.
- **VOD Statistics**: Total uploaded videos, total views, and total storage consumption in bytes.
- **Category Distribution**: Percentage breakdown of broadcasts across platform categories.

---

## 18. Admin Management System

- Accessible via the `/admin` tab for users with `ROLE_ADMIN`.
- **Platform Overview**: Total users, active users, total streams, live streams, total videos, platform views, and storage consumption.
- **User Management**: Search and list users, toggle user account active/suspended status, promote or demote roles (`USER` <-> `ADMIN`).
- **Stream Moderation**: Review all platform streams, inspect live viewer counts, and terminate/delete inappropriate channels.
- **Video Moderation**: Review all uploaded VODs, inspect storage keys, and permanently delete videos from MinIO storage and the database.

---

## 19. Docker Stack & Infrastructure Setup

The platform is orchestrated via Docker Compose (`docker-compose.yml`):
- `postgres`: PostgreSQL 16 (Health check via `pg_isready`)
- `redis`: Redis 7 Alpine (Health check via `redis-cli ping`)
- `minio`: MinIO Object Storage (Health check via `mc ready local`)
- `backend`: Spring Boot 3.3 Application (Health check via `/actuator/health`)
- `srs`: SRS 5 Media Server (Ingest 1935, API 1985, HLS 8080)
- `nginx`: Nginx 1.31 Reverse Proxy (Port 80)

---

## 20. Environment Variables Reference

| Variable | Default Value | Description |
|---|---|---|
| `SERVER_PORT` | `8081` | Spring Boot HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/stream_db` | PostgreSQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `stream_user` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `stream_password` | Database password |
| `SPRING_DATA_REDIS_HOST` | `redis` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SRS_API_URL` | `http://srs:1985` | SRS HTTP Management API URL |
| `SRS_PLAYBACK_URL_PREFIX` | `/live` | URL prefix for HLS playback |
| `MINIO_URL` | `http://minio:9000` | MinIO S3 API URL |
| `MINIO_ROOT_USER` | `admin` | MinIO admin username / access key |
| `MINIO_ROOT_PASSWORD` | `admin12345` | MinIO admin password / secret key |
| `MINIO_BUCKET_NAME` | `stratalive-videos` | S3 bucket name for VOD storage |
| `JWT_SECRET` | `mySuperSecretKeyForJwtAuthentication123456789` | HMAC-SHA256 signing secret key |

---

## 21. Local Development & Setup Instructions

### Prerequisites
- Docker Engine & Docker Compose v2
- Java 21 LTS
- Node.js 20+ & npm

### Starting the Platform

1. **Clone repository and navigate to root**:
   ```bash
   cd stream-platform
   ```

2. **Build frontend production bundle**:
   ```bash
   cd stream-web
   npm install
   npm run build
   cd ..
   ```

3. **Deploy frontend assets to Nginx**:
   ```bash
   mkdir -p stream-api/infrastructure/nginx/html
   cp -r stream-web/dist/* stream-api/infrastructure/nginx/html/
   ```

4. **Build backend JAR**:
   ```bash
   cd stream-api
   ./mvnw clean package -DskipTests
   cd ..
   ```

5. **Start Docker Compose stack**:
   ```bash
   docker compose up -d --build
   ```

6. **Verify platform health**:
   - Web Application: `http://localhost/`
   - Backend Actuator Health: `http://localhost:8081/actuator/health`
   - MinIO Console: `http://localhost:9001/`

---

## 22. Testing & Verification Guide

### 1. Backend Test Suite
Run the 78 automated integration and unit tests:
```bash
cd stream-api
./mvnw clean test
```

### 2. Frontend Linter & Build Verification
Verify Oxlint rules and Vite bundle generation:
```bash
cd stream-web
npm run lint
npm run build
```

### 3. Docker Compose Configuration Check
```bash
docker compose config
```

### 4. Master 25-Feature End-to-End Test Suite
Executes end-to-end verification of all 25 features against live Docker services:
```bash
python3 scratch/test_master_25_features.py
```

Test verification covers:
1. User registration
2. User login & JWT issuance
3. User profile verification
4. Live stream creation
5. Stream editing
6. Stream deletion
7. Stream scheduling & public discovery
8. RTMP broadcast publishing via FFmpeg
9. SRS ingestion & `on_publish` webhook verification
10. HLS playback & `.ts` chunk generation
11. Live viewer count tracking (`on_play` / `on_stop`)
12. Real-time live chat send and history
13. Notification lifecycle (unread count, mark-as-read, read-all)
14. Stream termination & clean `on_unpublish`
15. Creator analytics computation
16. VOD video upload (multipart)
17. MinIO object storage persistence
18. Full video playback (200 OK)
19. Video seeking via HTTP Range headers (206 Partial Content)
20. Video metadata editing
21. Video deletion from storage and database
22. Public vs. private access isolation
23. Admin dashboard metrics
24. Admin user management & chat moderation
25. Unauthorized access rejection (401 / 403)

---

## 23. Deployment Instructions

For host-based container deployments:
1. Clone repository to deployment server.
2. Review environment variables in `stream-api/.env`.
3. Generate a secure random 32-byte JWT secret:
   ```bash
   openssl rand -hex 32
   ```
4. Execute deployment script:
   ```bash
   chmod +x deploy-ec2.sh
   ./deploy-ec2.sh
   ```
5. Ensure ports `80` (HTTP), `1935` (RTMP), and optionally `9001` (MinIO Web) are permitted through network security groups.
