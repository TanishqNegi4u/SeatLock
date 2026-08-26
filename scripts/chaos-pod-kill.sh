#!/bin/bash
set -e

echo "Starting booking with payment delay (10000ms)..."
curl -X POST http://localhost:8080/api/v1/seats/lock-delay -H "Content-Type: application/json" -d '{"seatId":1, "userId":123, "delayMs":10000}' &
sleep 2

echo "Killing the active pod..."
POD=$(kubectl get pods -l app=seatlock-backend -o jsonpath="{.items[0].metadata.name}")
kubectl delete pod $POD

echo "Waiting for pod restart..."
sleep 15
kubectl wait --for=condition=Ready pod -l app=seatlock-backend --timeout=60s

echo "Waiting for Reaper Job..."
sleep 10

echo "Verifying Zero Overselling and System Healing..."
psql -U postgres -h localhost -d seatlock -f verify-zero-overselling.sql
