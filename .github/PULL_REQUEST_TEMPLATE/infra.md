## Summary
<!-- What does this change in the infrastructure, Docker setup, or CI? -->

## Changes
- 

## Affected areas
- [ ] docker-compose.yml
- [ ] Dockerfile(s)
- [ ] CI workflow (ci.yml)
- [ ] Traefik / middlewares
- [ ] Keycloak / oauth2-proxy
- [ ] Prometheus / Grafana
- [ ] Flamenco manager / worker
- [ ] .env / .env.example
- [ ] Dependabot / security config

## Breaking changes
<!-- Does this require re-creating volumes, updating .env, or re-running setup steps? -->
None

## Screenshots
<!-- Dashboard or monitoring changes can benefit from a screenshot. Not mandatory. -->

## Checklist
- [ ] Tested with `docker compose up --build`
- [ ] No secrets or credentials committed
- [ ] `.env.example` updated if new variables added
- [ ] Healthchecks still passing
- [ ] LF line endings on any shell scripts
