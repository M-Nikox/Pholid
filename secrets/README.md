# Docker Secrets

This directory holds the secret files read by Docker Compose at startup.
**Never commit actual secret files to version control** — `*.txt` files in this
directory are excluded by `.gitignore`.

## Required secrets

| File | Used by | Description |
|---|---|---|
| `postgres_password.txt` | postgres, pangolin-backend | PostgreSQL password |
| `grafana_admin_password.txt` | grafana | Grafana admin password |
| `oidc_client_secret.txt` | pangolin-backend | OIDC client secret for Authentik (required when `PANGOLIN_AUTH_ENABLED=true`) |
| `grafana_oidc_client_secret.txt` | grafana | OIDC client secret for Grafana ↔ Authentik (required when `PANGOLIN_AUTH_ENABLED=true`) |

> **Note:** `oidc_client_secret.txt` and `grafana_oidc_client_secret.txt` must exist even when
> `PANGOLIN_AUTH_ENABLED=false` because Docker Compose declares them as secrets. Create them with a
> placeholder value if you are not using OIDC.

## Creating secret files

```bash
# PostgreSQL password
echo -n "your-strong-postgres-password" > secrets/postgres_password.txt

# Grafana admin password
echo -n "your-strong-grafana-password" > secrets/grafana_admin_password.txt

# OIDC client secret for Pangolin (copy from Authentik after creating the application)
echo -n "your-oidc-client-secret" > secrets/oidc_client_secret.txt

# OIDC client secret for Grafana (copy from Authentik after creating the Grafana application)
echo -n "your-grafana-oidc-client-secret" > secrets/grafana_oidc_client_secret.txt

# Restrict permissions so only the owner can read them
chmod 600 secrets/*.txt
```

> **Note:** Use `echo -n` to avoid a trailing newline in the secret file.
> The Spring Boot `SecretsEnvironmentPostProcessor` strips trailing whitespace,
> but the postgres image reads the file directly, so a trailing newline would
> be included in the password.

## Authentik secret key

`AUTHENTIK_SECRET_KEY` is passed as an environment variable (not a Docker secret) because
Authentik reads it via its own configuration mechanism. Set it in your `.env` file:

```bash
# Generate a cryptographically secure key (50+ characters recommended)
echo "AUTHENTIK_SECRET_KEY=$(openssl rand -hex 50)" >> .env
```
