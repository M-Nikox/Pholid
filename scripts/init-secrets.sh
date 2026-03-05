#!/usr/bin/env bash
# =============================================================================
# init-secrets.sh  –  Generate Docker secret files for the Pangolin stack
# =============================================================================
# Run this script once before starting the stack for the first time:
#
#   bash scripts/init-secrets.sh [PROFILE]
#
# PROFILE can be 'simple' (default), 'local', or 'production'.
# The script creates only the secret files required by the chosen profile
# and leaves existing files untouched.
#
# Requirements: openssl (available on virtually all Linux/macOS systems).
# =============================================================================

set -euo pipefail

SECRETS_DIR="$(cd "$(dirname "$0")/.." && pwd)/secrets"
PROFILE="${1:-simple}"

mkdir -p "$SECRETS_DIR"

# ---------------------------------------------------------------------------
# Helper: create a secret file only if it doesn't exist yet
# ---------------------------------------------------------------------------
create_secret() {
    local file="$SECRETS_DIR/$1"
    local value="$2"
    if [ -f "$file" ]; then
        echo "  [skip]   $1  (already exists)"
    else
        printf '%s' "$value" > "$file"
        chmod 600 "$file"
        echo "  [create] $1"
    fi
}

echo ""
echo "Initialising secrets for profile: $PROFILE"
echo "Output directory: $SECRETS_DIR"
echo ""

# ---------------------------------------------------------------------------
# Secrets required by ALL profiles
# ---------------------------------------------------------------------------
create_secret "postgres_password.txt"      "$(openssl rand -base64 32)"
create_secret "grafana_admin_password.txt" "$(openssl rand -base64 32)"
# smtp_password.txt is intentionally empty unless you configure an SMTP server
create_secret "smtp_password.txt"          ""

# ---------------------------------------------------------------------------
# Secrets required only by the 'local' and 'production' profiles
# ---------------------------------------------------------------------------
if [ "$PROFILE" = "local" ] || [ "$PROFILE" = "production" ]; then
    create_secret "authentik_secret_key.txt"       "$(openssl rand -hex 50)"
    # OIDC client secrets must be copied from Authentik after setup.
    # Placeholder values are written so Docker Compose can start; replace them
    # with the real secrets from the Authentik admin UI.
    create_secret "oidc_client_secret.txt"         "REPLACE_WITH_OIDC_CLIENT_SECRET_FROM_AUTHENTIK"
    create_secret "grafana_oidc_client_secret.txt" "REPLACE_WITH_GRAFANA_OIDC_CLIENT_SECRET_FROM_AUTHENTIK"
    echo ""
    echo "NOTE: oidc_client_secret.txt and grafana_oidc_client_secret.txt contain"
    echo "      placeholder values. Replace them with the real secrets from Authentik"
    echo "      after completing the initial Authentik setup."
fi

echo ""
echo "Done. Secret files are in $SECRETS_DIR"
echo ""
echo "Next steps:"
echo "  1. Copy .env.example to .env and set COMPOSE_PROFILES=$PROFILE"
echo "  2. Run: docker compose up -d --build"
if [ "$PROFILE" = "local" ]; then
    echo "  3. Visit https://localhost:9443/if/flow/initial-setup/ to create the Authentik admin account"
    echo "  4. Create OIDC applications in Authentik and update the oidc_client_secret.txt files"
elif [ "$PROFILE" = "production" ]; then
    echo "  3. Visit https://\$AUTHENTIK_DOMAIN/if/flow/initial-setup/ to create the Authentik admin account"
    echo "  4. Create OIDC applications in Authentik and update the oidc_client_secret.txt files"
fi
