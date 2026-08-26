#!/usr/bin/env bash
set -e

echo "Starting Minikube (4 CPUs, 6GB RAM)..."
minikube start --cpus=4 --memory=6g --driver=docker

echo "Configuring Docker environment..."
eval $(minikube docker-env)

echo "Building Docker images..."
docker build -t seatlock-backend:latest ./seatlock-backend
docker build -t seatlock-frontend:latest ./seatlock-frontend

echo "Applying Kubernetes manifests..."
kubectl apply -k ./k8s

echo "Waiting for deployments to be ready..."
kubectl wait --for=condition=ready pod -l app=seatlock-db --timeout=120s
kubectl wait --for=condition=ready pod -l app=seatlock-backend --timeout=180s

echo "NodePort URLs:"
minikube service seatlock-backend --url
