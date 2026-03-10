#!/usr/bin/env bash
set -e

# 1) Load environment variables from .env if it exists
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

# 2) Build the bot JAR
echo "==> Building Telegram bot JAR"
mvn -f pom.xml clean package -DskipTests=false

# 3) Build Docker image
IMAGE_NAME="schema-sync-telegram-bot:latest"
echo "==> Building Docker image $IMAGE_NAME"
docker build -t "$IMAGE_NAME" .

# 4) Remove any old container
CONTAINER_NAME="telegram-bot"
if docker ps -a --format '{{.Names}}' | grep -qw "$CONTAINER_NAME"; then
  echo "==> Removing existing container $CONTAINER_NAME"
  docker rm -f "$CONTAINER_NAME"
fi

# 5) Launch new container
echo "==> Running container $CONTAINER_NAME"
docker run -d \
  --name "$CONTAINER_NAME" \
  --env-file .env \
  --restart unless-stopped \
  --network ci-network \
  "$IMAGE_NAME"

echo "✅ Bot started. To tail logs: docker logs -f $CONTAINER_NAME"
