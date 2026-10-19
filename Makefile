.PHONY: setup test lint build serve
setup:
	python3 -m venv .venv
	.venv/bin/pip install -r backend/requirements-dev.txt
	cd frontend && pnpm install --frozen-lockfile
test:
	cd backend && ../.venv/bin/python -m pytest -q
lint:
	.venv/bin/ruff check backend
	cd frontend && pnpm lint
build:
	cd frontend && pnpm build
serve:
	docker compose up --build -d
