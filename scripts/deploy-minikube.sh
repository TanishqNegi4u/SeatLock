#!/bin/bash
set -e

echo "Starting Minikube..."
minikube start --cpus=4 --memory=6g

echo "Configuring Docker environment..."
eval $(minikube docker-env)

echo "Building Docker images..."
docker build -t seatlock-backend:latest ../backend
docker build -t seatlock-frontend:latest ../frontend

echo "Applying Kubernetes manifests..."
kubectl apply -f ../k8s/

echo "Waiting for deployments to be ready..."
kubectl wait --for=condition=available deployment/seatlock-backend --timeout=120s
kubectl wait --for=condition=available deployment/seatlock-frontend --timeout=120s

echo "NodePort URLs:"
minikube service seatlock-frontend --url
minikube service seatlock-backend --url
