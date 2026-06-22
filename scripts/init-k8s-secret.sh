#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# scripts/init-k8s-secret.sh
#
# One-time setup for a specific environment — creates dedicated Kubernetes
# Secret for af-novadesk-api with ALL infrastructure service credentials:
#   - PostgreSQL (db-username, db-password) — dedicated user provisioned in DB
#   - RabbitMQ   (rabbitmq-username, rabbitmq-password) — mirrors shared rabbitmq-secret
#   - MinIO      (minio-access-key, minio-secret-key) — mirrors shared minio-secret
#   - AuthHub    (authhub-base-url) — internal URL
#   - App URL    (app-base-url) — external URL
#
# Also provisions the PostgreSQL database and user with proper grants.
#
# Usage:
#   ENV=dit NAMESPACE_INFRA=af-dit NAMESPACE_APP=af-novadesk-dit ./scripts/init-k8s-secret.sh
#   ENV=sit NAMESPACE_INFRA=af-sit NAMESPACE_APP=af-novadesk-sit ./scripts/init-k8s-secret.sh
#   ENV=prod NAMESPACE_INFRA=af-prod NAMESPACE_APP=af-novadesk ./scripts/init-k8s-secret.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

ENV="${ENV:-dit}"
NAMESPACE_INFRA="${NAMESPACE_INFRA:-af-dit}"
NAMESPACE_APP="${NAMESPACE_APP:-af-novadesk-dit}"

DB_USER="af_novadesk_user"
DB_NAME="af_novadesk"

echo "============================================================"
echo "  Initializing af-novadesk-api dedicated secrets"
echo "  Environment:     ${ENV}"
echo "  Infra Namespace: ${NAMESPACE_INFRA}"
echo "  App Namespace:   ${NAMESPACE_APP}"
echo "============================================================"
echo ""

# ── Derive env-specific values ──────────────────────────────────────────────
case "${ENV}" in
  dit)
    AUTHHUB_BASE_URL="http://af-authhub.af-infra-core-dit:8080"
    APP_BASE_URL="https://novadesk-api.dit.auroraforge.co"
    ;;
  sit)
    AUTHHUB_BASE_URL="http://af-authhub.af-infra-core-sit:8080"
    APP_BASE_URL="https://novadesk-api.sit.auroraforge.co"
    ;;
  prod)
    AUTHHUB_BASE_URL="http://af-authhub.af-infra-core-prod:8080"
    APP_BASE_URL="https://novadesk-api.auroraforge.co"
    ;;
  *)
    echo "Unknown environment: ${ENV}"
    exit 1
    ;;
esac

# ═════════════════════════════════════════════════════════════════════════════
# PostgreSQL — dedicated user provisioned in the shared database
# ═════════════════════════════════════════════════════════════════════════════
echo "─── PostgreSQL ──────────────────────────────────────────────"

echo "[1/5] Getting postgres admin password..."
PGPASSWORD=$(kubectl get secret postgresql-secret -n "$NAMESPACE_INFRA" \
  -o jsonpath='{.data.postgres-password}' | base64 -d)
echo "  ✓ Got postgres admin password"

echo "[2/5] Generating and creating dedicated secret..."
DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)

# Get RabbitMQ password from shared secret
RABBITMQ_PASSWORD=$(kubectl get secret rabbitmq-secret -n "$NAMESPACE_INFRA" \
  -o jsonpath='{.data.rabbitmq-password}' | base64 -d)

# Get MinIO credentials from shared secret
MINIO_ACCESS_KEY=$(kubectl get secret minio-secret -n "$NAMESPACE_INFRA" \
  -o jsonpath='{.data.root-user}' 2>/dev/null | base64 -d || echo "admin")
MINIO_SECRET_KEY=$(kubectl get secret minio-secret -n "$NAMESPACE_INFRA" \
  -o jsonpath='{.data.root-password}' 2>/dev/null | base64 -d || \
  openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)

kubectl create secret generic af-novadesk-api-secret \
  -n "$NAMESPACE_APP" \
  --from-literal=db-username="$DB_USER" \
  --from-literal=db-password="$DB_PASSWORD" \
  --from-literal=rabbitmq-username="af_user" \
  --from-literal=rabbitmq-password="$RABBITMQ_PASSWORD" \
  --from-literal=minio-access-key="$MINIO_ACCESS_KEY" \
  --from-literal=minio-secret-key="$MINIO_SECRET_KEY" \
  --from-literal=authhub-base-url="$AUTHHUB_BASE_URL" \
  --from-literal=app-base-url="$APP_BASE_URL" \
  --dry-run=client -o yaml | kubectl apply -f -
echo "  ✓ Secret 'af-novadesk-api-secret' created"

echo "[3/5] Provisioning database and user..."
PG_POD=$(kubectl get pod -n "$NAMESPACE_INFRA" -l app.kubernetes.io/name=postgresql \
  -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "postgresql-0")

kubectl exec -n "$NAMESPACE_INFRA" "$PG_POD" -- \
  env PGPASSWORD="$PGPASSWORD" createdb -U postgres "$DB_NAME" 2>/dev/null || \
  echo "  (database already exists, skipping)"

kubectl exec -n "$NAMESPACE_INFRA" "$PG_POD" -- \
  env PGPASSWORD="$PGPASSWORD" psql -U postgres -c \
  "DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DB_USER') THEN CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD'; ELSE ALTER USER $DB_USER WITH PASSWORD '$DB_PASSWORD'; END IF; END \$\$;"

kubectl exec -n "$NAMESPACE_INFRA" "$PG_POD" -- \
  env PGPASSWORD="$PGPASSWORD" psql -U postgres -c \
  "GRANT CONNECT ON DATABASE $DB_NAME TO $DB_USER;"

kubectl exec -n "$NAMESPACE_INFRA" "$PG_POD" -- \
  env PGPASSWORD="$PGPASSWORD" psql -U postgres -c \
  "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"

kubectl exec -n "$NAMESPACE_INFRA" "$PG_POD" -- \
  env PGPASSWORD="$PGPASSWORD" psql -U postgres -d "$DB_NAME" -c \
  "GRANT ALL ON SCHEMA public TO $DB_USER;"
echo "  ✓ PostgreSQL database and user provisioned"

# ═════════════════════════════════════════════════════════════════════════════
# Summary
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "============================================================"
echo "  Done! Secret created in ${NAMESPACE_APP}"
echo "============================================================"
echo "  Secret:      af-novadesk-api-secret"
echo "  DB Username: ${DB_USER}"
echo "  DB Password: ${DB_PASSWORD}"
echo "  RabbitMQ:    af_user / ${RABBITMQ_PASSWORD}"
echo "  MinIO:       ${MINIO_ACCESS_KEY} / ${MINIO_SECRET_KEY}"
echo "  AuthHub URL: ${AUTHHUB_BASE_URL}"
echo "  App Base URL: ${APP_BASE_URL}"
echo "============================================================"
