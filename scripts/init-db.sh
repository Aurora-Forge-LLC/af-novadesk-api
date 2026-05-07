#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# init-db.sh — NovaDesk API Database Initialisation
#
# PLACEHOLDER — No database schema defined yet.
#
# When a database is provisioned for af-novadesk-api, update this script to:
#   1. Create the application schema (e.g. af_novadesk)
#   2. Create the application user with appropriate privileges
#   3. Grant necessary permissions
#
# Environment variables required (injected by init-db.yml workflow):
#   ENV              — Target environment (dit / sit / prod)
#   POSTGRES_USER    — Admin postgres user
#   POSTGRES_PASSWORD — Admin postgres password
#
# Example usage:
#   ENV=dit POSTGRES_USER=postgres POSTGRES_PASSWORD=secret ./init-db.sh
# -----------------------------------------------------------------------------
set -euo pipefail
echo "========================================"
echo " AF NovaDesk API — DB Init (PLACEHOLDER)"
echo " Environment : ${ENV:-unknown}"
echo "========================================"
echo ""
echo "WARNING: No database schema configured yet for af-novadesk-api."
echo "Update this script once a database instance is provisioned."
echo ""
echo "Placeholder complete. No changes made to the database."
