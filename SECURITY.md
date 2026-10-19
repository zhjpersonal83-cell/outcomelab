# Security and data boundaries

This is a synthetic portfolio demo, not a certified production service. Report issues privately to the repository owner before publishing sensitive details. Do not attach private data or credentials.

OutcomeLab now runs Java 21 / Servlet 4 on Tomcat 9. Review docs/JAVA_WEB_MIGRATION.md for current deployment and authorization boundaries. Prior threat/deployment records describe the original runtime.

Sandboxes are random bearer capabilities, expire after inactivity and are bounded in count and update history. No persistent identities or shared learning are introduced. Body and peer rate limits remain. JSON/CSV exports contain the entered text; spreadsheet formulas are escaped in CSV.
