.PHONY: test build verify
test:
	mvn test
build:
	mvn package
verify:
	mvn verify

.PHONY: reproduce
reproduce:
	python3 src/main/Python/reproduce_baseline.py
