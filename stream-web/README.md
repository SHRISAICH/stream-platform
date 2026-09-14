# Stream Web — Frontend Client

A responsive, dark-themed React 19 web application for the **Live Stream Management System** (`stream-platform`).

---

## Features
- **Public Stream Discovery**: Browse live and offline public broadcasts without logging in.
- **In-Browser HLS Playback**: Low-latency video playback using `hls.js`.
- **Live Viewer Count**: Real-time audience metrics with an unaggressive 5-second polling interval.
- **Creator Studio**: Authenticated stream creation, updating, deletion, and stream key management.
- **Encoder Helpers**: One-click copy for OBS Studio server/key and auto-generated FFmpeg command lines.
- **Privacy Controls**: Masked stream keys on dashboard; private streams restricted to owner.

---

## Tech Stack
- **Framework**: React 19 + Vite 8
- **Player**: `hls.js`
- **Linter**: Oxlint
- **Styling**: Vanilla Modern CSS (glassmorphism & dark YouTube-style theme)

---

## Getting Started

### Prerequisites
- Node.js 18+ and npm

### Installation
```bash
cd stream-web
npm install
```

### Development Server
```bash
npm run dev
```
Runs the Vite dev server at `http://localhost:5173`.

### Production Build
```bash
npm run build
```
Generates production-ready static assets in the `dist/` directory.

### Code Quality / Linting
```bash
npm run lint
```
Runs Oxlint across frontend source files.

---

## Service Integrations
- **Backend API**: `http://localhost:8081` (Auth, Stream CRUD, Webhooks, Viewer Stats)
- **SRS HLS Stream**: `http://localhost:8080/live/<streamKey>.m3u8`
- **SRS RTMP Ingest**: `rtmp://localhost:1935/live/<streamKey>`
