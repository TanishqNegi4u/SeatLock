$ErrorActionPreference = "Stop"

Write-Host "Running k6 Concurrency Test..."
k6 run .\concurrency-test.js

Write-Host "Running k6 Burst Traffic Test..."
k6 run .\burst-traffic.js

Write-Host "Running k6 Reaper Stress Test..."
k6 run .\reaper-stress.js

Write-Host "Checking for zero overselling in Database..."
$sqlQuery = "SELECT seat_id, count(*) FROM bookings GROUP BY seat_id HAVING count(*) > 1;"
$output = psql -U postgres -d seatlock -c $sqlQuery -t

if ([string]::IsNullOrWhiteSpace($output)) {
    Write-Host "SUCCESS: Zero overselling verified. No duplicate bookings found." -ForegroundColor Green
} else {
    Write-Host "FAILURE: Overselling detected! Duplicate bookings exist:`n$output" -ForegroundColor Red
}
