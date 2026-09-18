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

# Validate the production configuration without evaluating values from .env as shell code.
get_env_value() {
    local variable_name="$1"
    awk -v name="$variable_name" '
        index($0, name "=") == 1 {
            print substr($0, length(name) + 2)
            exit
        }
    ' stream-api/.env
}

REQUIRED_ENV_VARS=(
    SPRING_DATASOURCE_URL
    SPRING_DATASOURCE_USERNAME
    SPRING_DATASOURCE_PASSWORD
    MINIO_ROOT_USER
    MINIO_ROOT_PASSWORD
    MINIO_BUCKET_NAME
    JWT_SECRET
    SRS_PLAYBACK_URL_PREFIX
)

for ENV_VAR_NAME in "${REQUIRED_ENV_VARS[@]}"; do
    ENV_VAR_VALUE="$(get_env_value "$ENV_VAR_NAME")"
    if [[ -z "$ENV_VAR_VALUE" || "$ENV_VAR_VALUE" == replace_with_* ]]; then
        echo "ERROR: $ENV_VAR_NAME is missing or still uses an example placeholder in stream-api/.env."
        exit 1
    fi
done

RDS_JDBC_URL="$(get_env_value "SPRING_DATASOURCE_URL")"
if [[ "$RDS_JDBC_URL" != jdbc:postgresql://*.rds.amazonaws.com:*/* ]]; then
    echo "ERROR: SPRING_DATASOURCE_URL must be an Amazon RDS PostgreSQL JDBC URL for this deployment."
    exit 1
fi

if [[ "$(get_env_value "SRS_PLAYBACK_URL_PREFIX")" != "/live" ]]; then
    echo "ERROR: SRS_PLAYBACK_URL_PREFIX must be /live so Nginx can proxy HLS playback."
    exit 1
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

# Step 4: Launch Complete Container Stack via Docker Compose
# The multi-stage Dockerfile builds the backend from source inside the image build.
echo "=== [4/5] Validating and Starting Full Docker Compose Stack ==="
cd stream-api
docker compose config > /dev/null
docker compose up -d --build

# Step 5: Health Check & Verification
echo "=== [5/5] Waiting for Platform Services to Initialize ==="
MAX_RETRIES=30
RETRY_COUNT=0
HEALTH_OK=false

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    RETRY_COUNT=$((RETRY_COUNT+1))
    if curl -s http://localhost/actuator/health | grep -q '"status":"UP"'; then
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
    echo "  • Backend API & Actuator:  http://<EC2-HOST>/actuator/health"
    echo "  • SRS RTMP Publishing:     rtmp://<EC2-HOST>:1935/live/<STREAM_KEY>"
    echo "  • HLS Live Stream:         http://<EC2-HOST>/live/<STREAM_KEY>.m3u8"
    echo "  • MinIO Console:           http://<EC2-HOST>:9001"
    echo "======================================================================"
else
    echo "⚠️ Warning: Backend health check timed out. Inspecting container logs:"
    docker compose logs --tail=40 backend
    exit 1
fi
