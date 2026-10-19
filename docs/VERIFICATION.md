# Verification — 19 October 2026

Executed locally on macOS ARM64 / Python 3.12 / Node 24.19.0:

- Backend pytest: 9 passed; covers health/OpenAPI, partition separation, actual weight updates, session isolation, exact rollback, reproducible drift, input/evaluation rejection, expiry/capacity, CSV formula escaping, delete, request/rate limits and concurrent update cap.
- Ruff backend lint: passed.
- Frontend ESLint: passed.
- TypeScript compilation and Vite production build: passed; JS bundle approximately 230 kB before gzip, 72 kB gzip.
- Chromium Playwright: passed end-to-end experiment with prediction, correction, drift toggle, rollback, export, reset, theme switch and mobile overflow assertion.
- Production frontend dependency audit: no known vulnerabilities reported by pnpm at execution time. This is not a security guarantee or full backend audit.
- pip dependency compatibility check: passed.
- Desktop full-page screenshot visually inspected; mobile horizontal overflow checked programmatically. Full accessibility audit and load testing remain outstanding.

Warnings: Starlette's TestClient reports an httpx deprecation warning; tests still pass. Python transitive versions captured in requirements.lock; production requirements constrain to this snapshot. JavaScript dependencies are pinned by pnpm-lock.yaml.

Subsequent deployment checks: all 9 backend tests passed on Ubuntu x86_64 with Python 3.12.15. The same Chromium E2E test passed against https://outcomelab.zhongjiehuang.com over verified HTTPS. Nginx configuration test passed; the readiness endpoint and static frontend returned 200. Existing portfolio, IAFM and Marginloom endpoints returned 200 and their services remained active. Dedicated service had zero restarts and used approximately 103 MB.

Not executed: Docker build/Compose smoke (Docker unavailable), GitHub Actions hosted run. Production uses native systemd/Nginx instead of Docker to reduce overhead on this small shared host.

The user authorized the public repository zhjpersonal83-cell/outcomelab; authenticated repository ownership and push permissions were verified. Browser sign-in was completed and the public repository was created. Source synchronization is recorded by its verified branch commit.

Live baseline measurement: clean accuracy 100%, distractor-shift accuracy 55.56%, each on 36 toy examples. These are measured synthetic demonstration results, not production benchmarks. Live Chromium reported no page JavaScript errors.
