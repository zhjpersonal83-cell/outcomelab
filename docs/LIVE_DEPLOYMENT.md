# Verified live deployment — 19 October 2026

Public URL: https://outcomelab.zhongjiehuang.com

## Placement
- Dedicated root: `/opt/outcomelab`
- Release: `/opt/outcomelab/releases/20261019T103016Z`
- Retained previous release: `/opt/outcomelab/releases/20261019T102630Z`
- Current symlink: `/opt/outcomelab/current`
- Service: `outcomelab.service`, unprivileged `outcomelab` user
- Backend binds only `127.0.0.1:8011`; frontend is served by existing Nginx
- Site configuration: `/etc/nginx/sites-available/outcomelab`
- Original Nginx backup: `/opt/outcomelab/backups/nginx-before-20261019`
- Certificate: dedicated Let's Encrypt certificate, expires 17 January 2027; certbot timer and hostname-specific Nginx renewal hook installed

The host already runs MySQL, Tomcat/IAFM, portfolio and Marginloom. No unrelated application source or service was modified. Only a new Nginx virtual host was enabled, followed by syntax-checked graceful reloads. The host had no Docker runtime, so no Docker daemon was installed. A private Python 3.12.15 runtime was installed under `/opt/outcomelab/python` with uv under `/opt/outcomelab/tools`; system Python was retained.

## Production limits
Service account cannot log in. Read-only system protection, private temporary directory, no Linux capabilities, 50% CPU quota, 256 MB memory-high / 320 MB memory maximum, 32 tasks, one BLAS thread and one Uvicorn worker. Session capacity is 16 on this host (`MAX_SESSIONS`, default source cap 128). Every sandbox retains at most 20 corrections. Nginx limits clients to 60 API requests/minute and 5 new sessions/minute, with bounded bursts. Backend peer rate limit is shared behind Nginx and provides an additional aggregate guard. No access logs; application logs omit text and session paths.

## Operational checks
```sh
systemctl status outcomelab --no-pager
```
```sh
curl --fail https://outcomelab.zhongjiehuang.com/api/ready
```
```sh
journalctl -u outcomelab --since '10 minutes ago' --no-pager
```
```sh
nginx -t
```

## Rollback
The initial verified release is retained at `/opt/outcomelab/releases/20261019T102630Z` for application rollback. To disable only this site, stop/disable `outcomelab`, move its enabled Nginx symlink into `/opt/outcomelab/backups/`, syntax-check Nginx, then reload. Keep source, Python environment, certificate and backups for recovery. Do not restore the entire original Nginx directory over unrelated later changes.

For future updates, prepare a new release directory and venv; test before switching `current`. Preserve the previous symlink target. To roll back, switch `current` to that target, restart only `outcomelab`, syntax-check/reload Nginx only if its site configuration changed, then repeat HTTPS and browser checks. A service restart intentionally clears ephemeral sessions.

## Remaining release work
Public source repository: https://github.com/zhjpersonal83-cell/outcomelab. Public deployment is complete. Hosted Docker build/start/readiness checks and CI have passed. A full accessibility audit and stress testing remain separate outstanding checks.

Frontend mobile-layout patch: `0cbd18252b82d2fdd9dd6cc34b646151f171e4ae`. The prior static build is retained at `/opt/outcomelab/backups/frontend-before-0cbd182`. New assets were copied before atomically replacing the HTML entry point; the backend stayed running.
