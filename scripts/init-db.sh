#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# scripts/init-db.sh
#
# One-time setup: creates the af_novadesk database inside the shared infra
# postgres instance for any environment (dit/sit/uat/prod).
#
# The postgres superuser is already created by af-infra-core.
# This script creates the dedicated 'af_novadesk' database so that Flyway
# can connect to it and run schema migrations on first deploy.
#
# Run via GitHub Actions:
#   Actions → Init Database → Select environment → Run workflow
#
# Or manually on the server:
#   ENV=dit \
#   POSTGRES_USER="postgres_user" \
#   POSTGRES_PASSWORD="secret" \
#   ./scripts/init-db.sh
# ─────────────────────────────────────────────────────────────────────────────

set -e

ENV="${ENV:-dit}"

# ── Validate required env vars ──────────────────────────────────────────────
if [ -z "${POSTGRES_USER}" ]; then
  echo "❌ ERROR: POSTGRES_USER is not set."
  exit 1
fi
if [ -z "${POSTGRES_PASSWORD}" ]; then
  echo "❌ ERROR: POSTGRES_PASSWORD is not set."
  exit 1
fi

# ── Derive env-specific values ───────────────────────────────────────────────
POSTGRES_CONTAINER="af-postgres-${ENV}"    # container_name set by af-infra-core
TARGET_DB="af_novadesk"

echo "🚀 Initialising NovaDesk database"
echo "   Environment: ${ENV}"
echo "   Container  : ${POSTGRES_CONTAINER}"
echo "   Database   : ${TARGET_DB}"
echo "   User       : ${POSTGRES_USER}"
echo ""

# ── Create database (idempotent) ─────────────────────────────────────────────
# Pipe via stdin so \gexec meta-command is interpreted correctly by psql.
echo "SELECT 'CREATE DATABASE ${TARGET_DB} OWNER ${POSTGRES_USER}' \
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${TARGET_DB}')\gexec" \
| docker exec -i -e PGPASSWORD="${POSTGRES_PASSWORD}" "${POSTGRES_CONTAINER}" \
  psql -U "${POSTGRES_USER}" -d postgres

echo ""
echo "✅ Database '${TARGET_DB}' is ready [${ENV}]."
echo ""
echo "Next step: trigger the deploy workflow for ${ENV}, or push to develop (for dit)."
echo "Flyway will automatically create and migrate the af_novadesk schema on first startup."
