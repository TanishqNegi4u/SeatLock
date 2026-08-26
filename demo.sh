#!/usr/bin/env bash
set -e

# ==============================================================================
# SeatLock — 1-Command Live Interview Demo Script
# Starts Minikube (2 CPUs, 3GB RAM) -> Deploys Postgres & 3 Replicas -> Opens UI
# ==============================================================================

echo "========================================================"
echo "🚀 SeatLock — 1-Command Live Interview Demonstration"
echo "========================================================"

# Step 1: Check and Start Minikube with resource constraints
if ! minikube status >/dev/null 2>&1; then
    echo "⚙️ Starting Minikube (--cpus=2 --memory=3072m)..."
    minikube start --cpus=2 --memory=3072m --driver=docker
else
    echo "✅ Minikube is already running."
fi

# Step 2: Configure Docker environment to use Minikube's Docker daemon
echo "🐳 Setting up Minikube Docker environment..."
eval $(minikube docker-env)

# Step 3: Build local Docker images
echo "📦 Building Backend & Frontend Docker images..."
docker build -t seatlock-backend:latest ./seatlock-backend
docker build -t seatlock-frontend:latest ./seatlock-frontend

# Step 4: Apply Kubernetes Manifests
echo "☸️ Applying Kubernetes Manifests (Postgres, 3-Replica Backend, NodePort)..."
if [ ! -f "./k8s/secret.yaml" ]; then
    cp ./k8s/secret.yaml.example ./k8s/secret.yaml
fi
kubectl apply -k ./k8s

# Step 5: Wait for Postgres & Backend Pods to reach Ready status
echo "⏳ Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app=seatlock-db --timeout=120s

echo "⏳ Waiting for 3 SeatLock Backend Pods to be ready..."
kubectl wait --for=condition=ready pod -l app=seatlock-backend --timeout=180s

# Step 6: Retrieve Service URL
MINIKUBE_IP=$(minikube ip)
BACKEND_URL="http://${MINIKUBE_IP}:30080"
echo "========================================================"
echo "🎉 SeatLock Live Cluster is READY!"
echo "Backend API & Actuator: ${BACKEND_URL}"
echo "========================================================"

# Step 7: Open 2 browser tabs for real-time WebSocket demo
echo "🌐 Opening 2 Browser Tabs to demonstrate live cross-pod WebSocket sync..."
if command -v xdg-open > /dev/null; then
    xdg-open "${BACKEND_URL}/api/events/1/seats" &
    xdg-open "http://localhost:3000" &
elif command -v open > /dev/null; then
    open "http://localhost:3000" &
    open "http://localhost:3000" &
fi

echo "👉 Run the Automated Concurrency Load Test & DB Invariant Verification:"
echo "   ./scripts/run-load-test-and-verify.sh"
echo "========================================================"
