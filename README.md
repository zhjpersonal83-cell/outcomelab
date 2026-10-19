# OutcomeLab — Java Web

Break it. Teach it. Measure it.

[Live application](https://outcomelab.zhongjiehuang.com) · [Source](https://github.com/zhjpersonal83-cell/outcomelab) · [Migration and verification](docs/JAVA_WEB_MIGRATION.md)

## Stack and structure

Aligned with IAFM: **Java 21, Maven WAR, Servlet 4 (`javax.servlet`), Tomcat 9, HTML/CSS/native JavaScript**, with JDBC/MySQL. OutcomeLab reads its immutable baseline from MySQL; corrections remain in ephemeral memory. There is no Node, React, TypeScript, FastAPI, Cloudflare or SQLite application runtime. Optional offline Python research tools are kept separately, as in IAFM.

```text
pom.xml
src/main/JavaWeb/Java/
  Controller/       # HTTP/API and validation boundary
  Model/            # Classifier/reliability and application logic
  DatabaseService/  # JDBC model registry, schema and driver lifecycle
src/main/JavaWeb/WebAPP/
  index.html
  assets/css/app.css
  assets/js/app.js
  WEB-INF/web.xml
src/main/resources/ # Trained model and frozen evaluation data
src/main/Python/    # Optional offline research/reproduction only
src/test/java/     # Numeric parity, isolation and real Tomcat tests
src/test/resources/
docs/
deploy/
```

## Existing capabilities

OutcomeLab preserves the original 4096-feature hashing transform, exported trained coefficients, normalized one-vs-rest probabilities and real logistic SGD corrections. The Java implementation has fixture parity against the original Python implementation at baseline and after successive updates, including Unicode inputs. Frozen train/validation/test templates, distractor evaluation, 20-update cap, isolated 30-minute sandboxes, exact rollback and JSON/CSV exports remain available. A MySQL registry holds only the checksum-verified baseline. Experiment text, learned corrections and model snapshots remain ephemeral.


## Build and test

Install Java 21 and Maven 3.9+, then run:

```sh
mvn verify
```

Deploy `target/OutcomeLab.war` to Tomcat 9. Maven runs independent numeric fixtures and real HTTP requests against embedded Tomcat. H2 in MySQL mode is used only by tests; live applications use MySQL. Native browser scripts require no frontend build step.

Optional baseline regeneration: install src/main/Python/requirements.txt in a virtual environment and run python src/main/Python/reproduce_baseline.py. This overwrites the reference model; run all parity tests before accepting it.

## Boundaries

Synthetic, non-commercial research and engineering demonstration, developed with AI assistance. Existing limitations remain: synthetic metrics do not establish real-world safety, interactive threshold exploration is not independent validation, and human reviews do not rewrite benchmark labels. No fabricated learning or benchmark improvement.

Historical deployment records describe the superseded implementations. Current operation is documented in [Java Web migration](docs/JAVA_WEB_MIGRATION.md). First-party licensing and retained third-party notices remain in force.
