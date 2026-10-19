"""Optional offline baseline regeneration. The deployed application is entirely Java."""
import json
from pathlib import Path
from sklearn.feature_extraction.text import HashingVectorizer
from sklearn.linear_model import SGDClassifier
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

output=Path(__file__).resolve().parents[1]/"resources/baseline.json"
output.write_text(json.dumps({"coefficients":BASE.coef_.tolist(),"intercepts":BASE.intercept_.tolist(),"t":BASE.t_,"alpha":BASE.alpha,"seed":SEED,"features":4096,"classes":LABELS,"training":{"texts":TRAIN_X,"labels":TRAIN_Y},"validation":{"texts":VALID_X,"labels":VALID_Y},"test":{"texts":TEST_X,"labels":TEST_Y},"shifted":SHIFT_X},separators=(",",":")))
print("Wrote",output)
