"use strict";
const $ = (id) => document.getElementById(id),
  pct = (v) => (v === null ? "—" : `${(v * 100).toFixed(1)}%`);
let sid = "",
  version = 0,
  busy = false;
const base = new URL("./", document.baseURI);
async function api(path, body, method = "POST") {
  const r = await fetch(new URL("api/" + path, base), {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(sid ? { "X-OutcomeLab-Session": sid } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (!r.ok) {
    let message;
    try {
      message = (await r.json()).detail;
    } catch {
      message = `Request failed (${r.status})`;
    }
    throw Error(message);
  }
  return r.json();
}
function node(tag, text, cls) {
  const n = document.createElement(tag);
  if (text !== undefined) n.textContent = text;
  if (cls) n.className = cls;
  return n;
}
function buttons() {
  document
    .querySelectorAll("button[data-session],input[data-session]")
    .forEach((b) => (b.disabled = busy || !sid));
  $("rollback").disabled = busy || !sid || !version;
  $("reset").disabled = busy;
  for (const id of ["predict", "feedback"])
    $(id).disabled = busy || !sid || !$("ticket").value.trim();
}
async function run(fn) {
  busy = true;
  buttons();
  $("error").textContent = "";
  $("error").className = "";
  $("notice").textContent = "Computing…";
  try {
    await fn();
  } catch (e) {
    $("error").textContent = e.message;
    $("error").className = "error";
    $("notice").textContent = "";
  } finally {
    busy = false;
    buttons();
  }
}
function predict(p) {
  $("prediction").replaceChildren();
  const h = node("h4", "Routes to " + p.label);
  $("prediction").append(h);
  for (const [label, value] of Object.entries(p.probabilities)) {
    const row = node("div", undefined, "prob"),
      meter = node("meter");
    meter.min = 0;
    meter.max = 1;
    meter.value = value;
    row.append(node("span", label), meter, node("b", pct(value)));
    $("prediction").append(row);
  }
}
async function evaluate() {
  const e = await api(`sessions/current/evaluate`, {
    shift: $("shift").checked,
  });
  version = e.version;
  $("version").textContent = `MODEL v${version} · SEED 41`;
  $("shift-description").textContent =
    ($("shift").checked
      ? "Shifted set: each ticket includes an unrelated category keyword."
      : "Clean set: unseen phrasing using the same synthetic vocabulary.") +
    " 36 frozen examples; labels stay unchanged.";
  $("metrics").replaceChildren();
  for (const [key, title] of Object.entries({
    accuracy: "Accuracy ↑",
    log_loss: "Log loss ↓",
    brier: "Brier score ↓",
    ece: "Calibration error ↓",
  })) {
    const div = node("div", undefined, "metric"),
      fmt = (v) => (key === "accuracy" ? pct(v) : v.toFixed(3));
    div.append(
      node("small", title),
      node("strong", fmt(e.current[key])),
      node("span", "baseline " + fmt(e.baseline[key])),
    );
    $("metrics").append(div);
  }
  const table = node("table"),
    caption = node("caption", "Confusion matrix", "sr-only"),
    head = node("thead"),
    tr = node("tr");
  tr.append(node("th", "Actual / predicted"));
  e.current.labels.forEach((l) => tr.append(node("th", l)));
  head.append(tr);
  const tbody = node("tbody");
  e.current.matrix.forEach((row, i) => {
    const tr = node("tr");
    const label = node("th", e.current.labels[i]);
    label.scope = "row";
    tr.append(label);
    row.forEach((v, j) => tr.append(node("td", v, i === j ? "diagonal" : "")));
    tbody.append(tr);
  });
  table.append(caption, head, tbody);
  $("matrix").replaceChildren(table);
  $("bins").replaceChildren(
    ...e.current.calibration.map((b) =>
      node(
        "p",
        `${b.lower.toFixed(1)}–${(b.lower + 0.2).toFixed(1)}: ${b.count} examples · accuracy ${pct(b.accuracy)} · confidence ${pct(b.confidence)}`,
      ),
    ),
  );
}
async function reset() {
  if (sid) await api("sessions/current", undefined, "DELETE");
  sid = (await api("sessions")).session;
  $("prediction").replaceChildren();
  await evaluate();
  $("notice").textContent = "Fresh sandbox. Baseline restored.";
}
$("predict").onclick = () =>
  run(async () => {
    predict(await api(`sessions/current/predict`, { text: $("ticket").value }));
    $("notice").textContent =
      "Prediction computed. Scores are uncalibrated model probabilities.";
  });
$("feedback").onclick = () =>
  run(async () => {
    await api(`sessions/current/feedback`, {
      text: $("ticket").value,
      label: $("label").value,
    });
    predict(await api(`sessions/current/predict`, { text: $("ticket").value }));
    await evaluate();
    $("notice").textContent =
      "One real gradient update applied to your sandbox. Evaluation recomputed.";
  });
$("rollback").onclick = () =>
  run(async () => {
    await api(`sessions/current/rollback`);
    $("prediction").replaceChildren();
    await evaluate();
    $("notice").textContent = "Previous model version restored.";
  });
$("reset").onclick = () => run(reset);
$("shift").onchange = () =>
  run(async () => {
    await evaluate();
    $("notice").textContent = "Evaluation recomputed.";
  });
$("ticket").oninput = () => {
  $("prediction").replaceChildren();
  buttons();
};
document.querySelectorAll("[data-example]").forEach(
  (b) =>
    (b.onclick = () => {
      $("ticket").value = b.dataset.example;
      $("prediction").replaceChildren();
      buttons();
    }),
);
for (const format of ["json", "csv"])
  $(format).onclick = () =>
    run(async () => {
      const r = await fetch(
        new URL(`api/sessions/current/export?format=${format}`, base),
        { headers: { "X-OutcomeLab-Session": sid } },
      );
      if (!r.ok) throw Error("Export failed");
      const url = URL.createObjectURL(await r.blob()),
        a = node("a");
      a.href = url;
      a.download = `outcomelab-experiment.${format}`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      $("notice").textContent = "Export downloaded.";
    });
$("theme").onclick = () => {
  const dark = $("app").dataset.theme === "dark";
  $("app").dataset.theme = dark ? "light" : "dark";
  $("theme").textContent = dark ? "☾" : "☀";
};
run(reset);
