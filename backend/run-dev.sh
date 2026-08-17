#!/usr/bin/env bash
# Loads backend/.env into the real environment, then runs the app. Spring Boot does not
# auto-load .env files (verified empirically - see git history), so this is the terminal-side
# equivalent of the "envFile" the VS Code launch config uses for IDE runs.
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

./mvnw spring-boot:run
