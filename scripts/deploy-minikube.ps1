$ErrorActionPreference = "Stop"

Write-Host "Starting Minikube (2 CPUs, 4GB RAM)..."
minikube start --cpus=2 --memory=4g --driver=docker

Write-Host "Configuring Docker environment..."
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

Write-Host "Building Docker images..."
docker build -t seatlock-backend:latest ./seatlock-backend
docker build -t seatlock-frontend:latest ./seatlock-frontend

Write-Host "Applying Kubernetes manifests..."
kubectl apply -k ./k8s

Write-Host "Waiting for deployments to be ready..."
kubectl wait --for=condition=ready pod -l app=seatlock-db --timeout=120s
kubectl wait --for=condition=ready pod -l app=seatlock-backend --timeout=180s

Write-Host "NodePort URLs:"
minikube service seatlock-backend --url
