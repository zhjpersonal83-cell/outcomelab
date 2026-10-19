import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";
type Metrics = {
  accuracy: number;
  log_loss: number;
  brier: number;
  ece: number;
  n: number;
  matrix: number[][];
  labels: string[];
  calibration: {
    lower: number;
    count: number;
    accuracy: number | null;
    confidence: number | null;
  }[];
};
type Evaluation = {
  baseline: Metrics;
  current: Metrics;
  version: number;
  shift: boolean;
};
type Prediction = {
  label: string;
  probabilities: Record<string, number>;
  version: number;
};
async function api<T>(
  path: string,
  body?: unknown,
  method = "POST",
): Promise<T> {
  const r = await fetch("/api/" + path, {
    method,
    headers: { "Content-Type": "application/json" },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  if (!r.ok) {
    const text = await r.text();
    throw new Error(text.length < 250 ? text : `Request failed (${r.status})`);
  }
  return r.json();
}
const pct = (v: number) => `${(v * 100).toFixed(1)}%`;
function App() {
  const [sid, setSid] = useState(""),
    [text, setText] = useState("My parcel arrived but the invoice is wrong"),
    [prediction, setPrediction] = useState<Prediction | null>(null),
    [evaluation, setEvaluation] = useState<Evaluation | null>(null),
    [shift, setShift] = useState(false),
    [label, setLabel] = useState("billing"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [dark, setDark] = useState(true);
  async function evaluate(id: string, s: boolean) {
    setEvaluation(
      await api<Evaluation>(`sessions/${id}/evaluate`, { shift: s }),
    );
  }
  async function reset() {
    if (sid) await api(`sessions/${sid}`, undefined, "DELETE");
    const s = await api<{ session: string }>("sessions");
    setSid(s.session);
    setPrediction(null);
    await evaluate(s.session, shift);
    setNotice("Fresh sandbox. Baseline restored.");
  }
  async function run(fn: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    void run(reset);
  }, []); // A new private sandbox on every page load; no persistent identifier.
  async function exportFile(format: string) {
    const response = await fetch(
      `/api/sessions/${sid}/export?format=${format}`,
    );
    if (!response.ok) throw new Error("Export failed");
    const url = URL.createObjectURL(await response.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = `outcomelab-experiment.${format}`;
    a.click();
    URL.revokeObjectURL(url);
  }
  return (
    <div data-theme={dark ? "dark" : "light"}>
      <header>
        <a className="brand" href="#">
          ◈ <span>OutcomeLab</span>
        </a>
        <nav>
          <a href="#lab">Laboratory</a>
          <a href="#method">Method</a>
          <button
            onClick={() => setDark(!dark)}
            aria-label="Toggle colour theme"
          >
            {dark ? "☀" : "☾"}
          </button>
        </nav>
      </header>
      <main>
        <section className="hero">
          <div className="eyebrow">
            <i /> INTERACTIVE AI RELIABILITY LAB
          </div>
          <h1>
            Break it.
            <br />
            Teach it.
            <br />
            <em>Measure it.</em>
          </h1>
          <div className="hero-aside">
            <p>
              A model can be confident.
              <br />
              It can also be wrong.
            </p>
            <p className="muted">
              Find the edge of a support-ticket classifier. Give it a
              correction. See what learning actually changes.
            </p>
            <a className="primary" href="#lab">
              Take the 60-second challenge ↗
            </a>
            <small>No sign-up · Synthetic data · Real computation</small>
          </div>
        </section>
        <section className="steps" aria-label="Walkthrough">
          <div>
            <b>01 / BREAK</b>
            <p>Try a mixed or unusual ticket.</p>
          </div>
          <div>
            <b>02 / TEACH</b>
            <p>Choose its correct destination.</p>
          </div>
          <div>
            <b>03 / MEASURE</b>
            <p>Check the held-out result. Gains aren’t guaranteed.</p>
          </div>
        </section>
        <section id="lab">
          <div className="section-title">
            <div>
              <div className="eyebrow">YOUR PRIVATE SANDBOX</div>
              <h2>The experiment starts here.</h2>
            </div>
            <span className="pill">
              MODEL v{evaluation?.version ?? 0} · SEED 41
            </span>
          </div>
          <div role="alert" className={error ? "error" : ""}>
            {error}
          </div>
          <p role="status" className="muted">
            {busy ? "Computing…" : notice}
          </p>
          <div className="lab-grid">
            <article className="panel">
              <div className="panel-head">
                <h3>01 — Challenge the classifier</h3>
                <span>LIVE</span>
              </div>
              <label htmlFor="ticket">Synthetic support ticket</label>
              <textarea
                id="ticket"
                maxLength={1000}
                value={text}
                onChange={(e) => {
                  setText(e.target.value);
                  setPrediction(null);
                }}
              />
              <div className="examples">
                Try:{" "}
                <button
                  onClick={() => {
                    setText("I cannot log in to see my refund");
                    setPrediction(null);
                  }}
                >
                  Mixed intent
                </button>
                <button
                  onClick={() => {
                    setText("The box is still not here");
                    setPrediction(null);
                  }}
                >
                  Unfamiliar wording
                </button>
              </div>
              <button
                className="primary"
                disabled={busy || !sid || !text.trim()}
                onClick={() =>
                  void run(async () => {
                    setPrediction(
                      await api<Prediction>(`sessions/${sid}/predict`, {
                        text,
                      }),
                    );
                    setNotice(
                      "Prediction computed. Scores are uncalibrated model probabilities.",
                    );
                  })
                }
              >
                Run prediction ↗
              </button>
              {prediction && (
                <div className="prediction">
                  <h4>
                    Routes to <em>{prediction.label}</em>
                  </h4>
                  {Object.entries(prediction.probabilities).map(
                    ([name, value]) => (
                      <div className="prob" key={name}>
                        <span>{name}</span>
                        <meter min="0" max="1" value={value} />
                        <b>{pct(value)}</b>
                      </div>
                    ),
                  )}
                </div>
              )}
              <hr />
              <h3>02 — Teach one correction</h3>
              <label htmlFor="label">Correct destination</label>
              <select
                id="label"
                value={label}
                onChange={(e) => setLabel(e.target.value)}
              >
                {["account", "billing", "delivery"].map((x) => (
                  <option key={x}>{x}</option>
                ))}
              </select>
              <button
                disabled={busy || !sid || !text.trim()}
                onClick={() =>
                  void run(async () => {
                    await api(`sessions/${sid}/feedback`, { text, label });
                    setPrediction(
                      await api<Prediction>(`sessions/${sid}/predict`, {
                        text,
                      }),
                    );
                    await evaluate(sid, shift);
                    setNotice(
                      "One real gradient update applied to your sandbox. Evaluation recomputed.",
                    );
                  })
                }
              >
                Apply correction + evaluate
              </button>
              <small>
                Up to 20 corrections. Only your session changes. Use synthetic
                text; it remains in server memory until expiry.
              </small>
            </article>
            <article className="panel">
              <div className="panel-head">
                <h3>03 — Inspect the evidence</h3>
                <span>HELD OUT</span>
              </div>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={shift}
                  disabled={busy || !sid}
                  onChange={(e) => {
                    const v = e.target.checked;
                    setShift(v);
                    void run(() => evaluate(sid, v));
                  }}
                />{" "}
                Inject distracting vocabulary
              </label>
              <p className="muted">
                {shift
                  ? "Shifted set: each ticket includes an unrelated category keyword."
                  : "Clean set: unseen phrasing using the same synthetic vocabulary."}{" "}
                36 frozen examples; labels stay unchanged.
              </p>
              <div className="metric-grid">
                {evaluation &&
                  (["accuracy", "log_loss", "brier", "ece"] as const).map(
                    (key) => (
                      <div className="metric" key={key}>
                        <small>
                          {
                            {
                              accuracy: "Accuracy ↑",
                              log_loss: "Log loss ↓",
                              brier: "Brier score ↓",
                              ece: "Calibration error ↓",
                            }[key]
                          }
                        </small>
                        <strong>
                          {key === "accuracy"
                            ? pct(evaluation.current[key])
                            : evaluation.current[key].toFixed(3)}
                        </strong>
                        <span>
                          baseline{" "}
                          {key === "accuracy"
                            ? pct(evaluation.baseline[key])
                            : evaluation.baseline[key].toFixed(3)}
                        </span>
                      </div>
                    ),
                  )}
              </div>
              <h4>Where does it get confused?</h4>
              <p className="muted">Rows: actual · Columns: predicted</p>
              {evaluation && (
                <table>
                  <caption className="sr-only">Confusion matrix</caption>
                  <thead>
                    <tr>
                      <th scope="col">Actual / predicted</th>
                      {evaluation.current.labels.map((l) => (
                        <th scope="col" key={l}>
                          {l}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {evaluation.current.matrix.map((row, i) => (
                      <tr key={i}>
                        <th scope="row">{evaluation.current.labels[i]}</th>
                        {row.map((n, j) => (
                          <td key={j} className={i === j ? "diagonal" : ""}>
                            {n}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <details>
                <summary>Calibration bins & interpretation</summary>
                <p>
                  Expected calibration error uses five equal-width confidence
                  bins. Lower is better. This tiny synthetic set does not
                  establish real-world reliability.
                </p>
                {evaluation?.current.calibration.map((b) => (
                  <p key={b.lower}>
                    {b.lower.toFixed(1)}–{(b.lower + 0.2).toFixed(1)}: {b.count}{" "}
                    examples · accuracy{" "}
                    {b.accuracy === null ? "—" : pct(b.accuracy)} · confidence{" "}
                    {b.confidence === null ? "—" : pct(b.confidence)}
                  </p>
                ))}
              </details>
            </article>
          </div>
          <div className="toolbar">
            <button
              disabled={busy || !sid || !evaluation?.version}
              onClick={() =>
                void run(async () => {
                  await api(`sessions/${sid}/rollback`);
                  setPrediction(null);
                  await evaluate(sid, shift);
                  setNotice("Previous model version restored.");
                })
              }
            >
              ↶ Roll back one update
            </button>
            <button disabled={busy} onClick={() => void run(reset)}>
              Reset experiment
            </button>
            <button
              disabled={busy || !sid}
              onClick={() => void run(() => exportFile("json"))}
            >
              Export JSON ↓
            </button>
            <button
              disabled={busy || !sid}
              onClick={() => void run(() => exportFile("csv"))}
            >
              Corrections CSV ↓
            </button>
          </div>
        </section>
        <section id="method" className="method">
          <div>
            <div className="eyebrow">OPEN THE BLACK BOX</div>
            <h2>
              Small model.
              <br />
              Inspectable behaviour.
            </h2>
          </div>
          <div>
            <p>
              4096 hashed text features → linear classifier → three support
              queues. Corrections perform scikit-learn SGDClassifier partial_fit
              with log loss. No LLM or simulated training.
            </p>
            <p>
              54 training, 18 validation and 36 test examples use separate
              templates. Validation is reserved; no tuning is performed. The
              test set shares vocabulary with training and is intentionally
              easy. Repeatedly viewing scores can bias your choices: these are
              exploratory demo metrics, not independent research results.
            </p>
            <p>
              Each browser load creates an in-memory sandbox. Sessions expire
              after 30 minutes of inactivity or a server restart. No accounts,
              trackers, database or shared feedback model. Exports contain the
              text you entered.
            </p>
            <p>
              Personal, non-commercial portfolio and learning demonstration. No
              claims of production readiness or visa eligibility. Architecture,
              tests, limitations and deployment instructions accompany the
              source.
            </p>
          </div>
        </section>
      </main>
      <footer>
        <span>◈ OutcomeLab</span>
        <span>Break It. Teach It. Measure It.</span>
        <span>Illustrative metrics. Honest experiments.</span>
      </footer>
    </div>
  );
}
createRoot(document.getElementById("root")!).render(<App />);
