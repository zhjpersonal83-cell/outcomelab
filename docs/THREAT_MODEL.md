> Historical record from before the Java Web migration. For the current runtime, structure, build, tests and deployment, see [Java Web migration](JAVA_WEB_MIGRATION.md).

# Threat model

Assets: service availability, independent sandbox weights, synthetic feedback confidentiality. Assume malicious unauthenticated visitors. Random 256-bit session capabilities resist guessing; no global mutable learner is exposed. Do not share exported text or session URLs. Capability tokens are in request paths, so disable access logs at every proxy. Application logs contain method/status/duration only; no text, address, token or full path. Edge infrastructure may log client addresses; review its settings before release.

Controls: 8KiB streamed body cap, 1000-character text cap, fixed label enum, 20 updates, bounded sessions/rate maps, 180 requests/minute per direct peer, nginx 60/minute per direct peer, serialized model operations, no deserialization/uploads/code execution, strict same-origin frontend, React text escaping, CSP, non-root read-only containers, loopback published port, resource caps. No database or disk persistence of feedback. No trackers or external fonts. Exports are user-triggered; CSV values starting with spreadsheet formula prefixes are escaped.

Behind TLS proxy, nginx sees the proxy address and limits all clients together. The outer proxy must independently rate-limit original clients; do not trust arbitrary forwarded headers. Single process is required; multiple workers would create inconsistent sandbox routing. Session TTL is checked on use and purged on session creation; inactive entries may remain in memory until a later creation or restart. There is no guaranteed immediate zeroization. Do not enter personal information.

Remaining risks: resource exhaustion within caps, token exposure through infrastructure logs, malicious corrections harming the visitor's own model, adaptive test leakage, dependency vulnerabilities, no formal security audit. Review dependency advisories and perform load/security tests before public production use.
