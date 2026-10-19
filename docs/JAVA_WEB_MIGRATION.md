# Java Web migration — 19 October 2026

## Requested scope

Reimplement OutcomeLab using the same code layout and application technology as IAFM while retaining its functionality. This is a Java application rewrite, not a Java wrapper around the original runtime. The original implementations remain in Git history and in preserved deployment releases.

OutcomeLab preserves the original 4096-feature hashing transform, exported trained coefficients, normalized one-vs-rest probabilities and real logistic SGD corrections. The Java implementation has fixture parity against the original Python implementation at baseline and after successive updates, including Unicode inputs. Frozen train/validation/test templates, distractor evaluation, 20-update cap, isolated 30-minute sandboxes, exact rollback and JSON/CSV exports remain available. It has no database because the existing product deliberately uses ephemeral experiments.


## Architecture

Browser HTML/CSS/JavaScript → Servlet controller → Java model/domain logic → per-sandbox in-memory model snapshots.

Java 21 and Maven package a WAR for Servlet 4 / Tomcat 9. The Java source and WebAPP paths match the IAFM Maven configuration. Controllers use explicit input validation and bounded bodies. Responses do not cache capability-scoped data. Database queries use parameters and workspace constraints. Only trusted loopback reverse proxies may supply forwarded identity/scheme headers.

The frontend uses standard DOM APIs and same-origin fetch; supplied text is rendered with textContent, not HTML injection. It retains the existing product operations and responsive views; React component internals are replaced by native browser code. The visual layout has been adapted where needed for this implementation.

## Build

```sh
mvn verify
```

`target/OutcomeLab.war` is the application. `target/surefire-reports/` records tests. The test-scoped embedded Tomcat/H2 dependencies are excluded from production WARs. Optional Python experiments do not serve requests.

## Verification

- Numeric parity fixtures were captured from the original implementations before migration.
- Classifier probabilities, hashed Unicode features, baseline/shifted metrics and successive SGD corrections compare to Python within 1e-12. Exact snapshot rollback and session isolation are tested. Concurrent updates stop at 20.
- Real embedded Tomcat requests verify static resources, HTTP methods, input validation and end-to-end flows under a non-root context path.
- Real server Tomcat checks cover readiness, per-sandbox prediction, correction, held-out evaluation and rollback.
- Browser checks cover interactive prediction/policy/review/vision behavior and responsive layout. API tests separately inspect report contents; download-button activation alone is not treated as proof of local file saving.

## Tomcat deployment

Use Tomcat 9 with Java 21. For the existing shared server, deploy `OutcomeLab.war` as `/OutcomeLab` and configure Nginx to proxy the existing domain to that context. Preserve the domain and TLS configuration. Browser URLs remain on the existing domain root; Nginx strips the internal context from cookie paths and redirects. Enable trusted-loopback RemoteIpValve at the application Context so HTTPS cookies/origin validation use the original public scheme.

OutcomeLab needs no MySQL schema. MAX_SESSIONS bounds ephemeral sandboxes. Browser requests send the capability in X-OutcomeLab-Session to sessions/current so routine server access logs do not record capability values. The original session-ID route remains compatible for API clients; deployments should avoid logging those legacy capability URLs.

## Migration and rollback

Existing ephemeral experiments cannot survive any application restart; users can export before a restart and start a fresh sandbox afterward. The original Python release/service remains available as a rollback target.

No IAFM database, portfolio source, or unrelated service is changed by this migration.

## Documentation references

[Tomcat 9 documentation](https://tomcat.apache.org/tomcat-9.0-doc/) defines the Servlet 4 container configuration. Historical files elsewhere in docs/ remain dated evidence for earlier implementations and must not be used as current build instructions.

## Verified live cutover

On 19 October 2026, the existing HTTPS domain was switched to the Java WAR in the shared Tomcat service. Health reports Java 21 / Servlet 4. Browser operations were checked through the public domain. IAFM and the portfolio remained available (HTTP 200), and their Tomcat service was not restarted. Original application services were stopped and disabled, with their releases retained for rollback.

WAR SHA-256: `b1c771d373e10b569ce06f50c4dbcc8419129b20b41238499c8f7edf478efbf2`.

Maven verification passed 4 JUnit tests, including numeric parity and real Tomcat HTTP flows. Live prediction and correction reached model version 1 with recomputed evaluation. Original in-memory sessions are not transferable across the runtime replacement; fresh sandboxes retain the original experiment behavior.
