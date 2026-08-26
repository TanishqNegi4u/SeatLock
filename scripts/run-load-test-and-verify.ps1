# ==============================================================================
# Design Rationale (Option B):
# k6 executes in an isolated Go-based JS runtime without native shell-out
# or direct SQL driver support. Running load generation followed by a strict
# database-level invariant verification script in the pipeline guarantees that
# zero-overselling is proven at the database engine level (single source of truth),
# failing the entire pipeline with a non-zero exit code if any invariant is violated.
# ==============================================================================
$ErrorActionPreference = "Stop"

$baseUrl = if ($env:BASE_URL) { $env:BASE_URL } else { "http://localhost:8080" }
$dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$dbPort = if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }
$dbUser = if ($env:DB_USER) { $env:DB_USER } else { "seatlock" }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { "seatlock" }
$env:PGPASSWORD = if ($env:PGPASSWORD) { $env:PGPASSWORD } else { "seatlock" }

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "🚀 Step 1: Running k6 500-VU Concurrency Load Test" -ForegroundColor Green
Write-Host "Target: $baseUrl" -ForegroundColor White
Write-Host "========================================================" -ForegroundColor Cyan

k6 run --env BASE_URL="$baseUrl" k6/concurrency-test.js

Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "🔍 Step 2: Database State & Invariant Verification" -ForegroundColor Green
Write-Host "Database: postgresql://${dbUser}@${dbHost}:${dbPort}/${dbName}" -ForegroundColor White
Write-Host "========================================================" -ForegroundColor Cyan

# Run verification queries
psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -f scripts/verify-zero-overselling.sql

# Invariant 1: Check for duplicate/oversold seats (MUST be 0)
$oversellQuery = "SELECT count(*) FROM (SELECT seat_id FROM bookings WHERE status = 'CONFIRMED' GROUP BY seat_id HAVING count(*) > 1) sub;"
$oversellCount = (psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -t -A -c $oversellQuery).Trim()

# Invariant 2: Total booked seats count (MUST equal total inventory 500)
$totalSeatsQuery = "SELECT count(*) FROM seats WHERE status = 'BOOKED';"
$bookedCount = (psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -t -A -c $totalSeatsQuery).Trim()

# Invariant 3: Total confirmed bookings count
$confirmedQuery = "SELECT count(*) FROM bookings WHERE status = 'CONFIRMED';"
$confirmedCount = (psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -t -A -c $confirmedQuery).Trim()

Write-Host ""
Write-Host "--------------------------------------------------------"
Write-Host "📊 Verification Invariants Summary:" -ForegroundColor White
Write-Host "  - Total Booked Seats:       $bookedCount / 500"
Write-Host "  - Total Confirmed Bookings: $confirmedCount / 500"
Write-Host "  - Duplicate / Oversold:     $oversellCount"
Write-Host "--------------------------------------------------------"

# Strict assertions
if ($oversellCount -ne "0") {
    Write-Host "❌ FATAL: Overselling detected! Duplicate bookings count: $oversellCount" -ForegroundColor Red
    exit 1
}

if ($bookedCount -ne "500") {
    Write-Host "❌ FATAL: Inventory mismatch! Expected 500 booked seats, got $bookedCount" -ForegroundColor Red
    exit 1
}

if ($bookedCount -ne $confirmedCount) {
    Write-Host "❌ FATAL: Reconciliation mismatch! Booked seats ($bookedCount) != Confirmed bookings ($confirmedCount)" -ForegroundColor Red
    exit 1
}

Write-Host "✅ SUCCESS: All database invariants verified with zero overselling (100% mathematical match)." -ForegroundColor Green
exit 0
