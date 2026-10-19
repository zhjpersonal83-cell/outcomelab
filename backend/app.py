import copy
import csv
import io
import json
import logging
import os
import secrets
import threading
import time
from collections import OrderedDict, deque

import numpy as np
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from sklearn.feature_extraction.text import HashingVectorizer
from sklearn.linear_model import SGDClassifier
from sklearn.metrics import accuracy_score, confusion_matrix, log_loss

LABELS = ["account", "billing", "delivery"]
SEED = 41
VECT = HashingVectorizer(n_features=4096, alternate_sign=False, norm="l2")
WORDS = {
    "account": ["password", "login", "profile", "access", "username", "authentication"],
    "billing": ["invoice", "payment", "charge", "refund", "receipt", "subscription"],
    "delivery": ["parcel", "shipment", "courier", "tracking", "dispatch", "package"],
}
TEMPLATES = [
    "Please help with my {}",
    "I have a problem with {}",
    "Can you check the {}",
    "My {} needs attention",
    "Question about {}",
    "Please investigate {}",
]


def dataset(partition):
    # Disjoint templates and vocabulary combinations, frozen before any feedback.
    template_ids = {"train": [0, 1, 2], "validation": [3], "test": [4, 5]}[partition]
    texts, labels = [], []
    for label in LABELS:
        for word in WORDS[label]:
            for tid in template_ids:
                texts.append(TEMPLATES[tid].format(word))
                labels.append(label)
    return texts, labels


TRAIN_X, TRAIN_Y = dataset("train")
VALID_X, VALID_Y = dataset("validation")
TEST_X, TEST_Y = dataset("test")
SHIFT_X = [
    f"{x}; ignore the unrelated {WORDS[LABELS[(LABELS.index(y) + 1) % 3]][0]} reference"
    for x, y in zip(TEST_X, TEST_Y)
]
BASE = SGDClassifier(loss="log_loss", random_state=SEED, alpha=0.001)
BASE.fit(VECT.transform(TRAIN_X), TRAIN_Y)
app = FastAPI(title="OutcomeLab", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("ALLOWED_ORIGINS", "http://localhost:5173").split(","),
    allow_methods=["GET", "POST", "DELETE"],
    allow_headers=["Content-Type"],
)
lock = threading.RLock()
sessions = OrderedDict()
rates = {}
TTL = 1800
MAX_SESSIONS = max(1, min(128, int(os.getenv("MAX_SESSIONS", "128"))))
logging.basicConfig(level=logging.INFO, format="%(message)s")


@app.middleware("http")
async def guard(request: Request, call_next):
    start = time.monotonic()
    # ASGI stream cap also handles chunked requests without Content-Length.
    size = 0
    chunks = []
    async for chunk in request.stream():
        size += len(chunk)
        if size > 8192:
            return Response("Request too large", status_code=413)
        chunks.append(chunk)
    body = b"".join(chunks)
    request._body = body
    # Direct peer only. Reverse proxy supplies a separate per-client limit.
    peer = request.client.host if request.client else "unknown"
    with lock:
        now = time.monotonic()
        for key in list(rates):
            if not rates[key] or rates[key][-1] < now - 60:
                del rates[key]
        if peer not in rates and len(rates) >= 4096:
            return Response("Capacity reached", status_code=429)
        q = rates.setdefault(peer, deque())
        while q and q[0] < now - 60:
            q.popleft()
        if len(q) >= 180:
            return Response("Rate limit reached", status_code=429)
        q.append(now)
    response = await call_next(request)
    response.headers["Cache-Control"] = "no-store"
    response.headers["X-Content-Type-Options"] = "nosniff"
    logging.getLogger("outcomelab").info(
        json.dumps(
            {
                "event": "request",
                "method": request.method,
                "status": response.status_code,
                "duration_ms": round((time.monotonic() - start) * 1000),
            }
        )
    )
    return response


class Prediction(BaseModel):
    text: str = Field(min_length=1, max_length=1000)


class Feedback(Prediction):
    label: str = Field(pattern="^(account|billing|delivery)$")


class Experiment(BaseModel):
    shift: bool = False


def get_session(sid):
    item = sessions.get(sid)
    if not item or item["expires"] < time.monotonic():
        sessions.pop(sid, None)
        raise HTTPException(404, "Session expired. Start a new sandbox.")
    item["expires"] = time.monotonic() + TTL
    return item


def metrics(model, shifted=False):
    x = VECT.transform(SHIFT_X if shifted else TEST_X)
    p = model.predict_proba(x)
    pred = model.classes_[p.argmax(axis=1)]
    confidence = p.max(axis=1)
    correct = pred == np.array(TEST_Y)
    ece = 0.0
    bins = []
    for lo in np.arange(0, 1, 0.2):
        mask = (confidence >= lo) & (confidence < lo + 0.2 + 1e-12)
        n = int(mask.sum())
        acc = float(correct[mask].mean()) if n else None
        conf = float(confidence[mask].mean()) if n else None
        if n:
            ece += n / len(TEST_Y) * abs(acc - conf)
        bins.append(
            {
                "lower": round(float(lo), 1),
                "count": n,
                "accuracy": acc,
                "confidence": conf,
            }
        )
    onehot = np.array([[int(y == c) for c in model.classes_] for y in TEST_Y])
    return {
        "n": len(TEST_Y),
        "accuracy": float(accuracy_score(TEST_Y, pred)),
        "log_loss": float(log_loss(TEST_Y, p, labels=model.classes_)),
        "brier": float(np.mean(np.sum((p - onehot) ** 2, axis=1))),
        "ece": float(ece),
        "calibration": bins,
        "matrix": confusion_matrix(TEST_Y, pred, labels=LABELS).tolist(),
        "labels": LABELS,
    }


@app.get("/api/health")
def health():
    return {"status": "ok"}


@app.get("/api/ready")
def ready():
    return {"status": "ready", "features": BASE.n_features_in_}


@app.post("/api/sessions")
def create(response: Response):
    with lock:
        now = time.monotonic()
        for key in list(sessions):
            if sessions[key]["expires"] < now:
                del sessions[key]
        if len(sessions) >= MAX_SESSIONS:
            raise HTTPException(503, "Sandbox capacity reached. Try later.")
        sid = secrets.token_urlsafe(32)
        sessions[sid] = {
            "model": copy.deepcopy(BASE),
            "history": [],
            "versions": [copy.deepcopy(BASE)],
            "expires": now + TTL,
        }
        return {"session": sid, "seed": SEED, "labels": LABELS}


@app.post("/api/sessions/{sid}/predict")
def predict(sid: str, body: Prediction):
    with lock:
        s = get_session(sid)
        p = s["model"].predict_proba(VECT.transform([body.text]))[0]
        return {
            "label": str(s["model"].classes_[p.argmax()]),
            "probabilities": dict(zip(s["model"].classes_.tolist(), p.tolist())),
            "version": len(s["history"]),
        }


@app.post("/api/sessions/{sid}/feedback")
def feedback(sid: str, body: Feedback):
    with lock:
        s = get_session(sid)
        if len(s["history"]) >= 20:
            raise HTTPException(409, "20 corrections reached. Reset to continue.")
        # Never allow exact evaluation samples to become training samples.
        if body.text.strip().casefold() in {x.casefold() for x in TEST_X + VALID_X + SHIFT_X}:
            raise HTTPException(422, "Reserved evaluation example. Write your own example.")
        s["model"].partial_fit(VECT.transform([body.text]), [body.label])
        s["history"].append(body.model_dump())
        s["versions"].append(copy.deepcopy(s["model"]))
        return {"version": len(s["history"])}


@app.post("/api/sessions/{sid}/evaluate")
def evaluate(sid: str, body: Experiment):
    with lock:
        s = get_session(sid)
        return {
            "baseline": metrics(BASE, body.shift),
            "current": metrics(s["model"], body.shift),
            "shift": body.shift,
            "version": len(s["history"]),
        }


@app.post("/api/sessions/{sid}/rollback")
def rollback(sid: str):
    with lock:
        s = get_session(sid)
        if s["history"]:
            s["history"].pop()
            s["versions"].pop()
            s["model"] = copy.deepcopy(s["versions"][-1])
        return {"version": len(s["history"])}


@app.delete("/api/sessions/{sid}")
def delete(sid: str):
    with lock:
        sessions.pop(sid, None)
    return {"deleted": True}


@app.get("/api/sessions/{sid}/export")
def export(sid: str, format: str = "json"):
    with lock:
        s = get_session(sid)
        if format == "csv":
            out = io.StringIO()
            w = csv.writer(out)
            w.writerow(["text", "label"])
            for row in s["history"]:
                # Spreadsheet formula injection protection.
                w.writerow(
                    [
                        "'" + row["text"]
                        if row["text"].startswith(("=", "+", "-", "@", "\t", "\r"))
                        else row["text"],
                        row["label"],
                    ]
                )
            return Response(out.getvalue(), media_type="text/csv")
        if format != "json":
            raise HTTPException(422, "Use json or csv")
        return {
            "seed": SEED,
            "model": "SGDClassifier log_loss / HashingVectorizer 4096",
            "version": len(s["history"]),
            "corrections": s["history"],
            "clean": metrics(s["model"]),
            "shifted": metrics(s["model"], True),
        }
