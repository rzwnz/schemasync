#!/usr/bin/env sh
# schema-sync-bot/docker/entrypoint.sh

# Load env if mounted
if [ -f /app/.env ]; then
  echo "Loading .env"
  set -o allexport
  . /app/.env
  set +o allexport
fi

echo "Starting SchemaSyncBot..."
exec java $JAVA_OPTS -jar app.jar
