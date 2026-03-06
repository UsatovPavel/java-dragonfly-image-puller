.PHONY: help setup build test diagnostics

DFDAEMON_ADDR ?= unix:///var/run/dragonfly/dfdaemon.sock
DRAGONFLY_TEST ?= ru.hse.dragonfly.puller.DragonflyImagePullerLocalIntegrationTest
DRAGONFLY_NS ?= dragonfly-system
DFDAEMON_SOCKET ?= /var/run/dragonfly/dfdaemon.sock

help:
	@echo "Targets:"
	@echo "  make setup        - install CI-like local prerequisites"
	@echo "  make build           - start local Dragonfly in minikube"
	@echo "  make test         - run Dragonfly local integration test"
	@echo "  make run          - local-up + local-test"
	@echo "  make diagnostics  - print cluster/socket diagnostics"

setup:
	chmod +x scripts/ci-dragonfly-setup.sh
	./scripts/ci-dragonfly-setup.sh

build:
	chmod +x scripts/minikube-dragonfly.sh
	./scripts/minikube-dragonfly.sh 1

integration-test:
	DFDAEMON_ADDR=$(DFDAEMON_ADDR) ./gradlew --no-daemon test --tests $(DRAGONFLY_TEST)

test:
	DFDAEMON_ADDR=$(DFDAEMON_ADDR) ./gradlew  test

diagnostics:
	@echo "== minikube status =="
	-@minikube status
	@echo ""
	@echo "== dragonfly namespace pods =="
	-@kubectl get pods -n $(DRAGONFLY_NS) -o wide
	@echo ""
	@echo "== dragonfly namespace events (last 40) =="
	-@kubectl get events -n $(DRAGONFLY_NS) --sort-by='.lastTimestamp' | tail -n 40 > logs.txt
	@echo ""
	@echo "== dfdaemon socket =="
	-@ls -lah $(DFDAEMON_SOCKET)
	@echo ""
	@echo "== dfdaemon logs (tail 120) =="
	-@kubectl logs -n $(DRAGONFLY_NS) -l app.kubernetes.io/name=dfdaemon --tail=120 >> logs.txt
