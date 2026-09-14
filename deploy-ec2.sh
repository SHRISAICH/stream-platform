#!/usr/bin/env bash
# ==============================================================================
# StrataLive — Complete AWS EC2 Automated Deployment Script
# ==============================================================================
# Deploys the containerized StrataLive platform:
# - PostgreSQL 16 (Relational Database)
# - Redis 7 (Low-latency viewer counters & cache)
# - MinIO (S3-compatible video object storage)
# - Spring Boot 3 Backend API (Java 21, REST, VOD streaming, SRS callbacks)
# - SRS 5 Media Server (RTMP ingestion, HLS packaging)
# - Nginx (Reverse proxy, SPA static serving, Range request streaming)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================================================"
echo "⚡ Starting StrataLive Deployment on Host..."
echo "======================================================================"

# Step 1: Ensure Docker and Docker Compose Plugin are installed
echo "=== [1/5] Verifying Docker & Docker Compose installation ==="
if ! command -v docker &> /dev/null; then
    echo "Docker not found. Installing Docker Engine & Compose Plugin..."
    sudo apt-get update -y
    sudo apt-get install -y ca-certificates curl gnupg lsb-release
    sudo mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg --yes
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt-get update -y
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    sudo usermod -aG docker "$USER" || true
    echo "Docker installed successfully."
fi

# Step 2: Configure Environment Variables
echo "=== [2/5] Configuring Environment Variables ==="
if [ ! -f "stream-api/.env" ]; then
    echo "Creating stream-api/.env from .env.example..."
    cp stream-api/.env.example stream-api/.env
    # Generate random 32-byte hex secret for JWT if openssl is available
    if command -v openssl &> /dev/null; then
        RANDOM_JWT_SECRET=$(openssl rand -hex 32)
        sed -i.bak "s|JWT_SECRET=.*|JWT_SECRET=${RANDOM_JWT_SECRET}|" stream-api/.env
        rm -f stream-api/.env.bak
        echo "Generated secure random JWT secret in stream-api/.env"
    fi
fi

# Step 3: Build Frontend Production Bundle (if Node is present on host)
echo "=== [3/5] Checking Frontend Assets ==="
if [ -d "stream-web" ] && command -v npm &> /dev/null; then
    echo "Building frontend from source..."
    cd stream-web
    npm install
    npm run build
    mkdir -p ../stream-api/infrastructure/nginx/html
    cp -r dist/* ../stream-api/infrastructure/nginx/html/
    cd ..
    echo "Frontend build assets copied to Nginx directory."
else
    echo "Pre-built frontend assets in stream-api/infrastructure/nginx/html will be used."
fi

# Step 4: Verify or Build Backend JAR
echo "=== [4/6] Checking Backend JAR Artifact ==="
if ! ls stream-api/target/stream-api-*.jar 1> /dev/null 2>&1; then
    echo "Pre-built backend JAR not found in target/. Building with Maven wrapper..."
    cd stream-api
    ./mvnw clean package -DskipTests
    cd ..
    echo "Backend JAR built successfully."
else
    echo "Pre-built backend JAR found in stream-api/target/."
fi

# Step 5: Launch Complete Container Stack via Docker Compose
echo "=== [5/6] Building & Starting Full Docker Compose Stack ==="
cd stream-api
docker compose up -d --build

# Step 6: Health Check & Verification
echo "=== [6/6] Waiting for Platform Services to Initialize ==="
MAX_RETRIES=30
RETRY_COUNT=0
HEALTH_OK=false

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    RETRY_COUNT=$((RETRY_COUNT+1))
    if curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"'; then
        HEALTH_OK=true
        break
    fi
    echo "Waiting for Spring Boot backend health check... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 3
done

if [ "$HEALTH_OK" = true ]; then
    echo "======================================================================"
    echo "✅ StrataLive Platform Successfully Deployed & Healthy!"
    echo "======================================================================"
    docker compose ps
    echo ""
    echo "Public Endpoints:"
    echo "  • Web Application / SPA:   http://<EC2-HOST>/"
    echo "  • Backend API & Actuator:  http://<EC2-HOST>:8081/actuator/health"
    echo "  • SRS RTMP Publishing:     rtmp://<EC2-HOST>:1935/live/<STREAM_KEY>"
    echo "  • HLS Live Stream:         http://<EC2-HOST>/live/<STREAM_KEY>.m3u8"
    echo "  • MinIO Console:           http://<EC2-HOST>:9001"
    echo "======================================================================"
else
    echo "⚠️ Warning: Backend health check timed out. Inspecting container logs:"
    docker compose logs --tail=40 backend
    exit 1
fi
