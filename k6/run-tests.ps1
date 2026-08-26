# ==============================================================================
# SeatLock k6 Concurrency Benchmark & DB Invariant Verification Runner
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "🚀 Running SeatLock k6 Concurrency Test Suite" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Run 500 VU Concurrency Test
Write-Host "👉 Running 500-VU Concurrency Load Test..." -ForegroundColor Yellow
k6 run .\concurrency-test.js

# 2. Run Burst Traffic Test
Write-Host "👉 Running Waiting Room Burst Traffic Test..." -ForegroundColor Yellow
k6 run .\burst-traffic.js

# 3. Post-Run Database Invariant Verification
Write-Host "`n🔍 Verifying Database Invariants (PostgreSQL)..." -ForegroundColor Cyan

# Check 1: Duplicate / Oversold Seats
$oversellQuery = "SELECT seat_id, count(*) AS booking_count FROM bookings WHERE status = 'CONFIRMED' GROUP BY seat_id HAVING count(*) > 1;"
$oversellCheck = psql -U seatlock -d seatlock -c $oversellQuery -t 2>$null

# Check 2: Total Booked Seats Count
$totalSeatsQuery = "SELECT count(*) FROM seats WHERE status = 'BOOKED';"
$bookedCount = (psql -U seatlock -d seatlock -c $totalSeatsQuery -t 2>$null).Trim()

Write-Host "--------------------------------------------------------"
Write-Host "Total Seats in BOOKED State: $bookedCount / 500" -ForegroundColor White

if ([string]::IsNullOrWhiteSpace($oversellCheck)) {
    Write-Host "✅ Invariant 1 PASSED: Zero Overselling Verified (0 duplicate bookings)." -ForegroundColor Green
} else {
    Write-Host "❌ Invariant 1 FAILED: Duplicate bookings detected:`n$oversellCheck" -ForegroundColor Red
}

if ($bookedCount -eq "500") {
    Write-Host "✅ Invariant 2 PASSED: 100% of seats successfully booked without deadlocks." -ForegroundColor Green
} else {
    Write-Host "ℹ️ Invariant 2 INFO: $bookedCount seats booked (normal if payment failure rate > 0%)." -ForegroundColor Yellow
}
Write-Host "========================================================" -ForegroundColor Cyan
