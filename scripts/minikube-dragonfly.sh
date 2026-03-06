#!/usr/bin/env bash
set -euo pipefail

# Local setup for Dragonfly single-node cluster used by local integration tests.
# Usage: ./scripts/minikube-dragonfly.sh [1]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALUES_FILE="${SCRIPT_DIR}/values.yaml"

if [[ ! -f "$VALUES_FILE" ]]; then
  echo "Missing Helm values file: $VALUES_FILE"
  exit 1
fi

NODES="${1:-1}"
if [[ "$NODES" != "1" ]]; then
  echo "Only single-node mode is supported in this repository."
  exit 1
fi

for cmd in minikube kubectl helm; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd"
    exit 1
  fi
done

echo ">>> Recreating minikube (driver=none, single node)..."
minikube stop 2>/dev/null || true
minikube delete 2>/dev/null || true

sudo mkdir -p /etc/cni/net.d
sudo env HOME="$HOME" CHANGE_MINIKUBE_NONE_USER=true minikube start --driver=none

if [[ -f /root/.kube/config && "$(id -u)" != "0" ]]; then
  mkdir -p "$HOME/.kube"
  sudo cp /root/.kube/config "$HOME/.kube/config"
  sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
  if [[ -d /root/.minikube ]]; then
    sudo cp -r /root/.minikube "$HOME/.minikube"
    sudo chown -R "$(id -u):$(id -g)" "$HOME/.minikube"
  fi
fi

export KUBECONFIG="${HOME}/.kube/config"
kubectl cluster-info >/dev/null

echo ">>> Installing Dragonfly via Helm..."
helm repo add dragonfly https://dragonflyoss.github.io/helm-charts/ 2>/dev/null || true
helm repo update
helm uninstall dragonfly -n dragonfly-system 2>/dev/null || true
helm install --wait --timeout 15m --create-namespace --namespace dragonfly-system \
  dragonfly dragonfly/dragonfly -f "$VALUES_FILE"

sudo mkdir -p /var/run/dragonfly/output
sudo chmod 777 /var/run/dragonfly/output

echo ">>> Waiting for dfdaemon socket..."
for i in $(seq 1 30); do
  if [[ -S /var/run/dragonfly/dfdaemon.sock ]]; then
    sudo chmod 666 /var/run/dragonfly/dfdaemon.sock 2>/dev/null || true
    echo "dfdaemon socket ready"
    break
  fi
  echo "Waiting for dfdaemon socket... ($i/30)"
  sleep 2
done

test -S /var/run/dragonfly/dfdaemon.sock || {
  echo "dfdaemon socket not found"
  kubectl get pods -n dragonfly-system -o wide || true
  exit 1
}

echo ">>> Ready. Run integration test with:"
echo "DFDAEMON_ADDR=unix:///var/run/dragonfly/dfdaemon.sock ./gradlew --no-daemon test --tests ru.hse.dragonfly.puller.BlobPullerLocalIntegrationTest"
