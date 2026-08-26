$ErrorActionPreference = "Stop"

Write-Host "Starting Minikube..."
minikube start --cpus=4 --memory=6g

Write-Host "Configuring Docker environment..."
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

Write-Host "Building Docker images..."
docker build -t seatlock-backend:latest ../backend
docker build -t seatlock-frontend:latest ../frontend

Write-Host "Applying Kubernetes manifests..."
kubectl apply -f ../k8s/

Write-Host "Waiting for deployments to be ready..."
kubectl wait --for=condition=available deployment/seatlock-backend --timeout=120s
kubectl wait --for=condition=available deployment/seatlock-frontend --timeout=120s

Write-Host "NodePort URLs:"
minikube service seatlock-frontend --url
minikube service seatlock-backend --url
