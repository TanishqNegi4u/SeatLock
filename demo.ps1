# ==============================================================================
# SeatLock — 1-Command Live Interview Demo Script (PowerShell for Windows)
# Starts Minikube -> Deploys Postgres & 3 Replicas -> Opens UI
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "SeatLock - 1-Command Live Interview Demonstration" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan

# Step 0: Ensure Docker Desktop is running
Write-Host "Checking Docker Desktop status..." -ForegroundColor Yellow
$null = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Docker Desktop is not running on your machine." -ForegroundColor Red
    Write-Host "Please start the Docker Desktop application from Windows Start Menu, wait until it starts, and re-run .\demo.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "Docker Desktop is running." -ForegroundColor Green

# Step 1: Check and Start Minikube (tuned for Docker Desktop: 2 CPUs, 3GB RAM)
$status = minikube status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Starting Minikube (2 CPUs, 3GB RAM)..." -ForegroundColor Yellow
    minikube start --cpus=2 --memory=3072m --driver=docker
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Minikube failed to start." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Minikube is already running." -ForegroundColor Green
}

# Step 2: Configure Docker environment to use Minikube Docker daemon
Write-Host "Setting up Minikube Docker environment..." -ForegroundColor Yellow
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

# Step 3: Build local Docker images
Write-Host "Building Backend and Frontend Docker images..." -ForegroundColor Yellow
docker build -t seatlock-backend:latest ./seatlock-backend
docker build -t seatlock-frontend:latest ./seatlock-frontend

# Step 4: Apply Kubernetes Manifests
Write-Host "Applying Kubernetes Manifests (Postgres, 3-Replica Backend, NodePort)..." -ForegroundColor Yellow
if (-not (Test-Path "./k8s/secret.yaml")) {
    Copy-Item "./k8s/secret.yaml.example" "./k8s/secret.yaml"
}
kubectl apply -k ./k8s

# Step 5: Wait for Postgres & Backend Pods to reach Ready status
Write-Host "Waiting for PostgreSQL to be ready..." -ForegroundColor Yellow
kubectl wait --for=condition=ready pod -l app=postgres --timeout=120s

Write-Host "Waiting for 3 SeatLock Backend Pods to be ready..." -ForegroundColor Yellow
kubectl wait --for=condition=ready pod -l app=backend --timeout=180s

# Step 6: Start Port Forwarding in background (maps Minikube backend to localhost:8080)
Write-Host "Forwarding backend service to localhost:8080..." -ForegroundColor Yellow
$portForwardJob = Start-Job -ScriptBlock { kubectl port-forward svc/backend 8080:8080 }
Start-Sleep -Seconds 2

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "SeatLock Live Cluster is READY & CONNECTED!" -ForegroundColor Green
Write-Host "Backend API & Actuator: http://localhost:8080" -ForegroundColor White
Write-Host "Live Seat Map:          http://localhost:8080/api/events/1/seats" -ForegroundColor White
Write-Host "Admin Metrics:          http://localhost:8080/api/admin/metrics?eventId=1" -ForegroundColor White
Write-Host "========================================================" -ForegroundColor Cyan

# Step 7: Open 2 browser tabs for real-time WebSocket demo
Write-Host "Opening 2 Browser Tabs to demonstrate live cross-pod WebSocket sync..." -ForegroundColor Green
Start-Process "http://localhost:3000"
Start-Process "http://localhost:3000"

Write-Host "Run the Automated Concurrency Load Test & DB Invariant Verification:" -ForegroundColor Yellow
Write-Host "   .\scripts\run-load-test-and-verify.ps1" -ForegroundColor White
Write-Host "========================================================" -ForegroundColor Cyan
