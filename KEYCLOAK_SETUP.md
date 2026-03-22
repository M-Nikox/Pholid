# Keycloak Setup — Pholid v2 (HTTP Dev Mode)

This guide covers the full Keycloak configuration for `docker-compose.dev.yml`.
All URLs are HTTP, no domain or TLS required.

---

## Service URLs (dev mode)

| Service | URL |
|---------|-----|
| Pholid | `http://localhost:8080` |
| Keycloak admin console | `http://localhost:8180` |
| Grafana | `http://localhost:3000` |
| Flamenco | `http://localhost:8280` |
| Prometheus | `http://localhost:9090` |

---

## How `PHOLID_AUTH_ENABLED` Works

**`false` (default):**
- All requests are permitted without authentication
- Spring never contacts Keycloak at startup, the app starts even if Keycloak is down
- `getCurrentUsername()` returns `"anonymous"`, `isAdmin()` returns `true`

**`true`:**
- All endpoints require authentication except health checks, static assets, and error pages
- Spring validates OIDC configuration at startup, if Keycloak is unreachable or
  credentials are wrong, Pholid **will fail to start**
- Login is recorded in the audit log
- Admin access requires membership in the `pholid-admins` Keycloak group

**Keep `PHOLID_AUTH_ENABLED=false` in `.env` until Step 8.**

---

## Step 1 — Start Bootstrap Services

Bring up only PostgreSQL and Keycloak first. Do not start the full stack until
Keycloak is fully configured and all client secrets are in `.env`.

```bash
docker compose -f docker-compose.dev.yml up -d postgresql keycloak
```

Wait for Keycloak to be healthy, first boot takes 60–90 seconds:

```bash
docker compose -f docker-compose.dev.yml logs -f keycloak
```

Wait for this line before proceeding:

```
Running the server in development mode.
```

Then open the admin console: `http://localhost:8180`

Log in with `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` from your `.env`.

---

## Step 2 — Create the Realm

1. Top-left dropdown (shows **Keycloak**) → **Create realm**
2. **Realm name:** `pholid`
   - Must exactly match `KEYCLOAK_REALM` in `.env` (default: `pholid`)
   - Case-sensitive
3. **Enabled:** On
4. Click **Create**

Verify the top-left dropdown now shows **pholid** before continuing. Every
subsequent step is performed inside this realm.

---

## Step 3 — Create the Pholid Client

This client handles user login for the Spring Boot backend. The client ID must
be exactly `pholid` - it is hardcoded in `SecurityConfig.java` as part of the
logout URL.

### Create the client

1. Left sidebar → **Clients** → **Create client**
2. **Client type:** OpenID Connect
3. **Client ID:** `pholid`
4. Click **Next**

### Capability config

5. **Standard flow:** On
6. **Direct access grants:** Off
7. Click **Next**

### Login settings

8. **Root URL:** `http://localhost:8080`
9. **Home URL:** `http://localhost:8080`
10. **Valid redirect URIs:** `http://localhost:8080/*`
    - Covers `/login/oauth2/code/keycloak` — Spring's OIDC callback path
11. **Valid post logout redirect URIs:** `http://localhost:8080/*`
    - Required for the post-logout redirect back to Pholid after logout.
      Without this Keycloak will reject the `post_logout_redirect_uri` parameter
      that `SecurityConfig` sends.
12. **Web origins:** `http://localhost:8080`
13. Click **Save**

### Get the client secret

14. Open the `pholid` client → **Credentials** tab
15. Copy the **Client secret**
16. Paste into `.env` as `OIDC_CLIENT_SECRET`

---

## Step 4 — Add the Groups Mapper

Pholid reads a `groups` claim from the OIDC token to determine admin access.
Without this mapper the claim is absent and `isAdmin()` returns `false` for
every authenticated user — the admin panel will be inaccessible to everyone.

The mapper goes on the **dedicated scope**, not the client directly.

1. Open the `pholid` client → **Client scopes** tab
2. Click `pholid-dedicated`
3. **Add mapper** → **By configuration** → **Group Membership**
4. Configure exactly as follows:
   - **Name:** `groups`
   - **Token claim name:** `groups`
   - **Full group path:** **Off**
     — if On, the claim value becomes `/pholid-admins` (with a leading slash),
     which will never match the configured `pholid-admins` value and admin
     detection will silently fail for every user
   - **Add to ID token:** On
   - **Add to access token:** On
   - **Add to userinfo:** On
5. Click **Save**

---

## Step 5 — Create the Grafana Client

Grafana uses its own OAuth integration. This client is only needed if you want
Keycloak SSO for Grafana. If you want to skip Grafana SSO for now, you can log
into Grafana with `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` directly and
come back to this step later.

1. Left sidebar → **Clients** → **Create client**
2. **Client type:** OpenID Connect
3. **Client ID:** `grafana`
4. Click **Next**
5. **Standard flow:** On
6. **Direct access grants:** Off
7. Click **Next**
8. **Valid redirect URIs:** `http://localhost:3000/login/generic_oauth`
9. **Web origins:** `http://localhost:3000`
10. Click **Save**

### Add the groups mapper to Grafana's dedicated scope

Grafana's role mapping expression also reads the `groups` claim.

11. Open the `grafana` client → **Client scopes** tab → click `grafana-dedicated`
12. **Add mapper** → **By configuration** → **Group Membership**
13. Same config as Step 4: name `groups`, claim name `groups`, full group path Off, all tokens On
14. Click **Save**

### Get the client secret

15. **Credentials** tab → copy **Client secret**
16. Paste into `.env` as `GRAFANA_OIDC_CLIENT_SECRET`
17. Set `GRAFANA_OAUTH_ENABLED=true` in `.env`

---

## Step 6 — Create the Admin Group

1. Left sidebar → **Groups** → **Create group**
2. **Name:** `pholid-admins`
   - Must exactly match `pholid.auth.admin-group` in `application.properties`
   - Case-sensitive
3. Click **Save**

---

## Step 7 — Create a Test User

1. Left sidebar → **Users** → **Add user**
2. Fill in:
   - **Username:** your choice, stored in job records and shown in audit log
   - **Email:** your choice
   - **First name / Last name:** your choice, shown in the Pholid avatar dropdown
   - **Email verified:** On
3. Click **Create**
4. **Credentials** tab → **Set password** → enter a password → **Temporary: Off** → **Save password**

### Assign to the admin group

5. **Groups** tab on the user page → **Join group** → select `pholid-admins` → **Join**

Verify by going to **Groups** → `pholid-admins` and confirming the user is listed.

---

## Step 8 — Enable Auth and Bring Up the Full Stack

All client secrets are now in `.env`. Update it:

```
PHOLID_AUTH_ENABLED=true
```

### Verify `.env` before continuing

```
KEYCLOAK_REALM=pholid
OIDC_CLIENT_ID=pholid
OIDC_CLIENT_SECRET=<from step 3>
GRAFANA_OIDC_CLIENT_SECRET=<from step 5, if doing Grafana SSO>
GRAFANA_OAUTH_ENABLED=true   # only if doing Grafana SSO
PHOLID_AUTH_ENABLED=true
```

### Bring up the full stack

```bash
docker compose -f docker-compose.dev.yml up -d
```

Watch Pholid start:

```bash
docker compose -f docker-compose.dev.yml logs -f pholid-backend
```

It is ready when you see the Spring startup banner with no OIDC errors.

---

## Step 9 — Smoke Test Checklist

| # | Check | URL | Expected |
|---|-------|-----|----------|
| 1 | Pholid loads | `http://localhost:8080` | Redirects to Keycloak login at `http://localhost:8180` |
| 2 | Login works | — | Returns to Pholid, avatar initials visible top-right |
| 3 | Full name in dropdown | — | Shows first + last name from Keycloak, not just username |
| 4 | Admin panel accessible | `http://localhost:8080/admin` | Page loads (user is in `pholid-admins`) |
| 5 | Audit log entry | `/admin` | `USER_LOGIN` entry visible for your login |
| 6 | Logout works | — | Redirects to Keycloak, then back to Pholid login page |
| 7 | Non-admin user blocked | — | Create a second user without `pholid-admins`, log in, confirm `/admin` returns 403 |
| 8 | Job submission | — | Submit a test job, confirm it appears in history under your username |
| 9 | Grafana SSO (optional) | `http://localhost:3000` | Redirects to Keycloak, logs in, role is Admin for `pholid-admins` members |

---

## Troubleshooting

### Pholid redirects to Keycloak but returns an error after login

**Client secret mismatch** — `OIDC_CLIENT_SECRET` in `.env` doesn't match the
`pholid` client Credentials tab. Regenerate in Keycloak, update `.env`, restart Pholid.

**Redirect URI mismatch** — in the `pholid` client Settings tab, confirm
**Valid redirect URIs** includes `http://localhost:8080/*`. Keycloak shows
`invalid_redirect_uri` in the URL bar if this is wrong.

**Realm mismatch** — confirm `KEYCLOAK_REALM` in `.env` matches the realm name exactly.

---

### Admin panel returns 403 after login

Work through in order:

1. Confirm the user is in `pholid-admins`: **Users** → select user → **Groups** tab
2. Confirm the groups mapper exists on `pholid-dedicated` with **Full group path: Off**
3. If you want to inspect the token directly, open browser dev tools → Network tab
   during login → find the callback request → copy the `id_token` value and decode
   it at [jwt.io](https://jwt.io). Confirm a `groups` claim is present containing
   `pholid-admins` (no leading slash)

---

### Logout doesn't redirect back to Pholid

Confirm **Valid post logout redirect URIs** on the `pholid` client includes
`http://localhost:8080/*`. Without this Keycloak drops the
`post_logout_redirect_uri` parameter silently and lands on its own logged-out page.

---

### Pholid fails to start after enabling auth

```bash
docker compose -f docker-compose.dev.yml logs pholid-backend
```

- **`OIDC_CLIENT_SECRET` is empty** — Spring rejects an empty secret
- **Realm doesn't exist** — all OIDC URIs return 404. Confirm the `pholid`
  realm was saved in Keycloak
- **Keycloak still starting** — Docker Compose will restart the container
  automatically once Keycloak is healthy. Wait and check logs again.

---

### Grafana SSO login fails

Confirm the redirect URI in the `grafana` client is exactly
`http://localhost:3000/login/generic_oauth` — no trailing slash, no variation.
Also confirm `GRAFANA_OAUTH_ENABLED=true` is set in `.env`.
