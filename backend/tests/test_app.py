import copy
from concurrent.futures import ThreadPoolExecutor

import numpy as np
import pytest
from fastapi.testclient import TestClient

import app as module


@pytest.fixture(autouse=True)
def clean():
    module.sessions.clear()
    module.rates.clear()


client = TestClient(module.app)


def session():
    return client.post("/api/sessions").json()["session"]


def evaluate(sid, shift=False):
    return client.post(f"/api/sessions/{sid}/evaluate", json={"shift": shift}).json()


def test_health_and_schema():
    assert client.get("/api/health").json()["status"] == "ok"
    assert client.get("/api/ready").status_code == 200
    assert "/api/sessions" in client.get("/openapi.json").json()["paths"]


def test_partitions_are_disjoint_and_balanced():
    parts = [set(module.dataset(p)[0]) for p in ["train", "validation", "test"]]
    assert not (parts[0] & parts[1] or parts[0] & parts[2] or parts[1] & parts[2])
    for p in ["train", "validation", "test"]:
        _, y = module.dataset(p)
        assert len({y.count(label) for label in module.LABELS}) == 1


def test_learning_is_real_isolated_and_reversible():
    a, b = session(), session()
    baseline = evaluate(a)
    weights = copy.deepcopy(module.sessions[a]["model"].coef_)
    response = client.post(
        f"/api/sessions/{a}/feedback",
        json={"text": "The box vanished again", "label": "delivery"},
    )
    assert response.json()["version"] == 1
    assert not np.array_equal(weights, module.sessions[a]["model"].coef_)
    assert np.array_equal(weights, module.sessions[b]["model"].coef_)
    assert evaluate(b) == baseline
    assert client.post(f"/api/sessions/{a}/rollback").json()["version"] == 0
    assert evaluate(a) == baseline


def test_shift_is_reproducible_and_changes_scores():
    a = session()
    assert evaluate(a, True) == evaluate(a, True)
    assert evaluate(a)["current"] != evaluate(a, True)["current"]
    assert sum(map(sum, evaluate(a)["current"]["matrix"])) == 36


def test_reject_reserved_text_and_bad_labels():
    a = session()
    for text in [module.TEST_X[0], module.VALID_X[0], module.SHIFT_X[0]]:
        assert (
            client.post(
                f"/api/sessions/{a}/feedback", json={"text": text, "label": "billing"}
            ).status_code
            == 422
        )
    assert (
        client.post(f"/api/sessions/{a}/feedback", json={"text": "hi", "label": "bad"}).status_code
        == 422
    )
    assert client.post(f"/api/sessions/{a}/predict", json={"text": "a" * 1001}).status_code == 422
    assert client.post("/api/sessions", content=b"x" * 8193).status_code == 413


def test_expiry_capacity_and_limit():
    a = session()
    module.sessions[a]["expires"] = 0
    assert client.post(f"/api/sessions/{a}/evaluate", json={}).status_code == 404
    for _ in range(module.MAX_SESSIONS):
        session()
    assert client.post("/api/sessions").status_code == 503


def test_export_and_delete():
    a = session()
    client.post(f"/api/sessions/{a}/feedback", json={"text": "=SUM(1,2)", "label": "billing"})
    data = client.get(f"/api/sessions/{a}/export").json()
    assert data["corrections"][0]["text"] == "=SUM(1,2)"
    assert "'=SUM" in client.get(f"/api/sessions/{a}/export?format=csv").text
    client.delete(f"/api/sessions/{a}")
    assert client.get(f"/api/sessions/{a}/export").status_code == 404


def test_rate_limit():
    for _ in range(180):
        assert client.get("/api/health").status_code == 200
    assert client.get("/api/health").status_code == 429


def test_training_cap_and_concurrency():
    a = session()

    def update(_):
        return client.post(
            f"/api/sessions/{a}/feedback",
            json={"text": "missing box", "label": "delivery"},
        ).status_code

    with ThreadPoolExecutor(max_workers=4) as pool:
        assert list(pool.map(update, range(20))) == [200] * 20
    assert update(21) == 409
    assert evaluate(a)["version"] == 20
