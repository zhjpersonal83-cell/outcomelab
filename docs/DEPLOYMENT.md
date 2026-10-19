# Deployment and rollback runbook

The initial archive contained no server target. The user subsequently authorized deployment to outcomelab.zhongjiehuang.com and creation of zhjpersonal83-cell/outcomelab. Deployment is now verified; see LIVE_DEPLOYMENT.md. The public source repository is https://github.com/zhjpersonal83-cell/outcomelab. The generic procedure below remains useful for another server.

1. Obtain the server target through the user's secure sign-in/SSH mechanism; never paste passwords into chat. Verify hostname, ownership, disk, ports, existing services and backup policy. Use a new `/opt/outcomelab/releases/<release>` directory and dedicated unprivileged deployment account; do not alter unrelated applications.
2. Inspect existing reverse proxy. Install Docker Engine/Compose if authorized. Copy source and `.env.example` to `.env`; set ALLOWED_ORIGINS to the exact HTTPS origin. Build with `docker compose build`; run tests and dependency audit. Keep prior release/images intact.
3. Start `docker compose up -d --wait`. Confirm `curl --fail http://127.0.0.1:8080/api/ready` and frontend. Port 8080 is loopback only; if occupied choose another loopback port in Compose.
4. User configures the chosen DNS record. Verify its public resolution. Add a dedicated TLS virtual host at the existing proxy. Example Caddy snippet (replace placeholder):

```caddy
outcomelab.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

Configure per-client request rate limits at the outer proxy; the built-in proxy rate limit groups clients arriving through it. Keep access logs disabled/redacted to avoid session token logging. Restrict firewall to SSH and HTTPS/HTTP as appropriate; never expose backend 8000.
5. Test HTTPS certificate, redirect policy, `/api/ready`, create/predict/correct/evaluate/rollback/export/reset in a real browser, mobile layout and keyboard access. Check sanitized logs, restart behavior, resource consumption and existing services. Only then report deployment success.
6. Backups: retain source ZIP, pinned dependencies, Compose/env configuration (secrets encrypted separately), release image digests and proxy configuration. There is no durable feedback data to back up. Inform users that restart loses sessions.
7. Rollback: restore prior source/images and proxy configuration; run `docker compose up -d --no-build --wait` from the previous release after stopping current stack. Never use volume deletion or overwrite another project. Smoke-test HTTPS again. Model rollback in the UI only changes the current session.

## GitHub handoff
Use a secure GitHub sign-in and verify the account plus exact user-owned target. Creating a repository needs explicit user authorization. Inspect existing branches/files first. Initialize a new local Git repository only for this product, commit reviewed source, add the verified remote, and push a new branch without force. Record `git rev-parse HEAD` and remote URL, and verify the remote commit. No remote URL or push success is implied by this source ZIP.

## Release checklist
- [ ] Verify server/domain/repository targets and authorization
- [ ] Run pinned backend checks, frontend lint/types/build and browser E2E
- [ ] Run Docker Compose build + health smoke on target
- [ ] Audit dependencies, load-test limits and inspect logs
- [ ] Confirm HTTPS/DNS and live end-to-end experiment
- [ ] Save previous release and rehearse rollback
- [ ] Verify remote commit SHA if publishing source
