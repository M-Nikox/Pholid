# Docker Secrets

This directory holds the secret files read by Docker Compose at startup.
**Never commit actual secret files to version control** — `*.txt` files in this
directory are excluded by `.gitignore`.

## Required secrets

| File | Used by | Description |
|---|---|---|
| `postgres_password.txt` | postgres, pangolin-backend | PostgreSQL password |
| `grafana_admin_password.txt` | grafana | Grafana admin password |

## Creating secret files

```bash
# PostgreSQL password
echo -n "your-strong-postgres-password" > secrets/postgres_password.txt

# Grafana admin password
echo -n "your-strong-grafana-password" > secrets/grafana_admin_password.txt

# Restrict permissions so only the owner can read them
chmod 600 secrets/postgres_password.txt secrets/grafana_admin_password.txt
```

> **Note:** Use `echo -n` to avoid a trailing newline in the secret file.
> The Spring Boot `SecretsEnvironmentPostProcessor` strips trailing whitespace,
> but the postgres image reads the file directly, so a trailing newline would
> be included in the password.
