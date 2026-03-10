#!/bin/sh
ENV_PATH="/app/.env"
if [ -f "$ENV_PATH" ]; then
  echo "Loading container env vars from $ENV_PATH"
  set -o allexport
  . "$ENV_PATH"
  set +o allexport
fi

echo "Starting Schema Sync Microservice..."
exec java $JAVA_OPTS -jar app.jar
