.PHONY: help setup build test diagnostics investigate-container investigate-worker
# Write log/diagnostics in log.txt
# or manually set (make diagnostics OUT=diag.txt)
DFDAEMON_ADDR ?= unix:///var/run/dragonfly/dfdaemon.sock
DRAGONFLY_TEST ?= ru.hse.dragonfly.puller.BlobPullerLocalIntegrationTest
DRAGONFLY_NS ?= dragonfly-system
DFDAEMON_SOCKET ?= /var/run/dragonfly/dfdaemon.sock
TARGET_CONTAINER_POD ?= dragonfly-seed-client-2
TARGET_WORKER_POD ?= dragonfly-redis-replicas-0
OUT ?= logs.txt

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
	@echo "== minikube status ==" > $(OUT)
	-@minikube status >> $(OUT) 2>&1
	@echo "" >> $(OUT)
	@echo "== dragonfly namespace pods ==" >> $(OUT)
	-@kubectl get pods -n $(DRAGONFLY_NS) -o wide >> $(OUT) 2>&1
	@echo "" >> $(OUT)
	@echo "== dragonfly namespace events (last 40) ==" >> $(OUT)
	-@kubectl get events -n $(DRAGONFLY_NS) --sort-by='.lastTimestamp' | tail -n 40 >> $(OUT) 2>&1
	@echo "" >> $(OUT)
	@echo "== dfdaemon socket ==" >> $(OUT)
	-@ls -lah $(DFDAEMON_SOCKET) >> $(OUT) 2>&1
	@echo "" >> $(OUT)
	@echo "== dfdaemon logs (tail 120) ==" >> $(OUT)
	-@kubectl logs -n $(DRAGONFLY_NS) -l app.kubernetes.io/name=dfdaemon --tail=120 >> $(OUT) 2>&1

investigate-container:
	@echo "== describe $(TARGET_CONTAINER_POD) ==" > $(OUT)
	-@kubectl describe pod -n $(DRAGONFLY_NS) $(TARGET_CONTAINER_POD) >> $(OUT) 2>&1
	@echo "" >> $(OUT)
	@echo "== logs $(TARGET_CONTAINER_POD) ==" >> $(OUT)
	-@kubectl logs -n $(DRAGONFLY_NS) $(TARGET_CONTAINER_POD) >> $(OUT) 2>&1
	@echo "" >> $(OUT)
	@echo "== previous logs $(TARGET_CONTAINER_POD) ==" >> $(OUT)
	-@kubectl logs -n $(DRAGONFLY_NS) $(TARGET_CONTAINER_POD) --previous >> $(OUT) 2>&1

investigate-worker:
	@echo "== describe $(TARGET_WORKER_POD) ==" > $(OUT)
	-@kubectl describe pod -n $(DRAGONFLY_NS) $(TARGET_WORKER_POD) >> $(OUT) 2>&1
	@echo "" >> $(OUT)
	@echo "== logs $(TARGET_WORKER_POD) ==" >> $(OUT)
	-@kubectl logs -n $(DRAGONFLY_NS) $(TARGET_WORKER_POD) >> $(OUT) 2>&1
	@echo "" >> $(OUT)
	@echo "== previous logs $(TARGET_WORKER_POD) ==" >> $(OUT)
	-@kubectl logs -n $(DRAGONFLY_NS) $(TARGET_WORKER_POD) --previous >> $(OUT) 2>&1
