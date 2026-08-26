$ErrorActionPreference = "Stop"

Write-Host "Starting booking with payment delay (10000ms)..."
$response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/seats/lock-delay" -Method POST -Body '{"seatId":1, "userId":123, "delayMs":10000}' -ContentType "application/json" -ErrorAction SilentlyContinue

Write-Host "Killing the active pod..."
$pod = kubectl get pods -l app=seatlock-backend -o jsonpath="{.items[0].metadata.name}"
kubectl delete pod $pod

Write-Host "Waiting for pod restart..."
Start-Sleep -Seconds 15
kubectl wait --for=condition=Ready pod -l app=seatlock-backend --timeout=60s

Write-Host "Waiting for Reaper Job..."
Start-Sleep -Seconds 10

Write-Host "Verifying Zero Overselling and System Healing..."
psql -U postgres -d seatlock -f verify-zero-overselling.sql
