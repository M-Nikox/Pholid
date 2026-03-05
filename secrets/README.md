# Docker Secrets

This directory holds the secret files read by Docker Compose at startup.
**Never commit actual secret files to version control** — `*.txt` files in this
directory are excluded by `.gitignore`.

> **Quickstart:** Run `scripts/init-secrets.sh` from the repository root to
> generate all required secret files automatically.

## Required secrets by profile

### `simple` profile
Three secret files are needed:

| File | Used by | Description |
|---|---|---|
| `postgres_password.txt` | postgres, pangolin-backend | PostgreSQL password |
| `grafana_admin_password.txt` | grafana | Grafana admin password |
| `smtp_password.txt` | pangolin-backend | SMTP password (can be empty) |

### `local` and `production` profiles
All six secret files are required:

| File | Used by | Description |
|---|---|---|
| `postgres_password.txt` | postgres, pangolin-backend, authentik | PostgreSQL password |
| `authentik_secret_key.txt` | authentik-server, authentik-worker | Authentik secret key (50+ char hex string) |
| `grafana_admin_password.txt` | grafana | Grafana admin password |
| `oidc_client_secret.txt` | pangolin-backend | OIDC client secret for Authentik |
| `grafana_oidc_client_secret.txt` | grafana | OIDC client secret for Grafana ↔ Authentik |
| `smtp_password.txt` | pangolin-backend | SMTP password (can be empty) |

## Creating secret files manually

```bash
# PostgreSQL password (all profiles)
echo -n "your-strong-postgres-password" > secrets/postgres_password.txt

# Grafana admin password (all profiles)
echo -n "your-strong-grafana-password" > secrets/grafana_admin_password.txt

# SMTP password for email notifications (all profiles — leave empty if not using email)
echo -n "your-smtp-password" > secrets/smtp_password.txt

# --- The files below are only required for the 'local' and 'production' profiles ---

# OIDC client secret for Pangolin (copy from Authentik after creating the application)
echo -n "your-oidc-client-secret" > secrets/oidc_client_secret.txt

# OIDC client secret for Grafana (copy from Authentik after creating the Grafana application)
echo -n "your-grafana-oidc-client-secret" > secrets/grafana_oidc_client_secret.txt

# Authentik secret key (generate a cryptographically secure key, 50+ characters recommended)
echo -n "$(openssl rand -hex 50)" > secrets/authentik_secret_key.txt

# Restrict permissions so only the owner can read them
chmod 600 secrets/*.txt
```

> **Note:** Use `echo -n` to avoid a trailing newline in the secret file.
> The Spring Boot `SecretsEnvironmentPostProcessor` strips trailing whitespace,
> but the postgres image reads the file directly, so a trailing newline would
> be included in the password.

## Authentik secret key

`authentik_secret_key.txt` is a Docker secret consumed by both `authentik-server`
and `authentik-worker` via the `AUTHENTIK_SECRET_KEY__FILE` environment variable.
Authentik reads the key directly from the file path — the `AUTHENTIK_SECRET_KEY`
variable in `.env` is **not** used and can be ignored.

Generate the key before the first start (**required for `local` and `production` profiles only**):

```bash
echo -n "$(openssl rand -hex 50)" > secrets/authentik_secret_key.txt
chmod 600 secrets/authentik_secret_key.txt
```
