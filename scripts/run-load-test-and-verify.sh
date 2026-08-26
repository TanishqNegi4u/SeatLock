#!/usr/bin/env bash
set -e

# ==============================================================================
# Design Rationale (Option B):
# k6 executes in an isolated Go-based JS runtime without native shell-out
# or direct SQL driver support. Running load generation followed by a strict
# database-level invariant verification script in the pipeline guarantees that
# zero-overselling is proven at the database engine level (single source of truth),
# failing the entire pipeline if any invariant is violated.
# ==============================================================================

BASE_URL="${BASE_URL:-http://localhost:8080}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-seatlock}"
DB_NAME="${DB_NAME:-seatlock}"
export PGPASSWORD="${PGPASSWORD:-seatlock}"

echo "========================================================"
echo "🚀 Step 1: Running k6 500-VU Concurrency Load Test"
echo "Target: ${BASE_URL}"
echo "========================================================"

k6 run --env BASE_URL="${BASE_URL}" k6/concurrency-test.js

echo ""
echo "========================================================"
echo "🔍 Step 2: Database State & Invariant Verification"
echo "Database: postgresql://${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "========================================================"

# Run verification queries
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -f scripts/verify-zero-overselling.sql

# Invariant 1: Check for duplicate/oversold seats (MUST be 0)
OVERSELL_COUNT=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -A -c \
  "SELECT count(*) FROM (SELECT seat_id FROM bookings WHERE status = 'CONFIRMED' GROUP BY seat_id HAVING count(*) > 1) sub;")

# Invariant 2: Total booked seats count (MUST equal total inventory 500)
BOOKED_COUNT=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -A -c \
  "SELECT count(*) FROM seats WHERE status = 'BOOKED';")

# Invariant 3: Total confirmed bookings count
CONFIRMED_COUNT=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -A -c \
  "SELECT count(*) FROM bookings WHERE status = 'CONFIRMED';")

echo ""
echo "--------------------------------------------------------"
echo "📊 Verification Invariants Summary:"
echo "  - Total Booked Seats:       ${BOOKED_COUNT} / 500"
echo "  - Total Confirmed Bookings: ${CONFIRMED_COUNT} / 500"
echo "  - Duplicate / Oversold:     ${OVERSELL_COUNT}"
echo "--------------------------------------------------------"

# Strict assertions
if [ "${OVERSELL_COUNT}" -ne 0 ]; then
    echo "❌ FATAL: Overselling detected! Duplicate bookings count: ${OVERSELL_COUNT}"
    exit 1
fi

if [ "${BOOKED_COUNT}" -ne 500 ]; then
    echo "❌ FATAL: Inventory mismatch! Expected 500 booked seats, got ${BOOKED_COUNT}"
    exit 1
fi

if [ "${BOOKED_COUNT}" -ne "${CONFIRMED_COUNT}" ]; then
    echo "❌ FATAL: Reconciliation mismatch! Booked seats (${BOOKED_COUNT}) != Confirmed bookings (${CONFIRMED_COUNT})"
    exit 1
fi

echo "✅ SUCCESS: All database invariants verified with zero overselling (100% mathematical match)."
exit 0
