#!/usr/bin/env bash
set -e

ENV_FILE=".env"
if [ "$1" = "--env-file" ] && [ -n "$2" ]; then
    ENV_FILE="$2"
fi

if [ -f "$ENV_FILE" ]; then
    echo "Loading environment variables from $ENV_FILE"
    set -o allexport
    source "$ENV_FILE"
    set +o allexport
else
    echo "Warning: env file '$ENV_FILE' not found; ensure necessary env vars are set in environment"
fi

IMAGE_NAME="schema-sync-microservice:latest"
CONTAINER_NAME="schema-sync"

echo "==> Building JAR with Maven"
mvn clean package -DskipTests=false

echo "==> Building Docker image $IMAGE_NAME"
docker build -t "$IMAGE_NAME" .

if docker ps -a --format '{{.Names}}' | grep -qw "$CONTAINER_NAME"; then
    echo "==> Removing existing container $CONTAINER_NAME"
    docker rm -f "$CONTAINER_NAME"
fi

if [ -z "$HOST_DIFF_STORE" ]; then
    HOST_DIFF_STORE="/srv/schema-diffs"
fi
echo "==> Ensuring host diff-store directory: $HOST_DIFF_STORE"
mkdir -p "$HOST_DIFF_STORE"

echo "==> Running container $CONTAINER_NAME"
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --network ci-network \
  -v "$HOST_DIFF_STORE":/diff-store \
  --env-file "$ENV_FILE" \
  -e DIFF_STORE_PATH=/diff-store \
  -p 9090:8080 \
  "$IMAGE_NAME"

echo "Container started. Logs:"
docker logs -f "$CONTAINER_NAME"
