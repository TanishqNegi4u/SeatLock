# SeatLock Technical Interview Defense Guide 🎯

This document prepares you to defend every architectural and distributed systems decision made in SeatLock during senior backend and systems design interviews.

---

### Q1: Why did you choose a PostgreSQL-only architecture instead of Redis or Kafka?
**Defense:**
> "In high-concurrency transactional systems like ticketing, introducing Redis or Kafka creates a dual-write distributed transaction problem. If you update Redis first and the database write fails, you have phantom locks; if you update the database first and Redis fails, your cache is stale. By leveraging PostgreSQL's native primitives—`FOR UPDATE SKIP LOCKED` for concurrency, `pg_notify` for cross-pod WebSocket fan-out, and `pg_try_advisory_xact_lock` for distributed scheduling—we maintain strict ACID consistency in a single source of truth while eliminating operational complexity, dual-write failure modes, and cache invalidation races."

---

### Q2: How does `SELECT FOR UPDATE SKIP LOCKED` prevent thread starvation?
**Defense:**
> "Under conventional `SELECT FOR UPDATE`, 500 concurrent threads competing for the same row would queue up waiting for the lock. In Spring Boot, this quickly exhausts the HikariCP connection pool and Tomcat worker threads, causing cascading latency across unrelated endpoints. `SKIP LOCKED` instructs PostgreSQL to immediately bypass any row that is already locked by an active transaction. The query returns zero rows instantaneously without waiting, allowing the application to immediately return HTTP 409 Conflict with zero database wait time."

---

### Q3: What is the difference between pessimistic and optimistic locking in your system? When would you use each?
**Defense:**
> "Pessimistic locking acquires a row-level database lock at the start of the transaction, guaranteeing exclusive access. It is best suited for flash-sale scenarios where hundreds of users compete for a tiny subset of seats (e.g., front-row seats), because it halts conflicting attempts at the database level without retries. Optimistic locking uses a JPA `@Version` column and does not hold database locks while work is performed; conflicts are detected only during `UPDATE`. It achieves significantly higher throughput (~1,400 TPS vs ~650 TPS) when contention is distributed evenly across a large catalog of seats."

---

### Q4: How do you handle cross-pod WebSocket broadcasts when a user books a seat on Pod 1?
**Defense:**
> "WebSocket connections are stateful and terminated on specific pods behind the Kubernetes Service. When Pod 1 confirms a booking, it issues `SELECT pg_notify('seat_updates', payload)`. Because `pg_notify` is transactional, PostgreSQL only delivers the notification if and when the enclosing transaction commits. All backend pods maintain a dedicated, unpooled JDBC connection running `LISTEN seat_updates`. Each pod receives the event and fans it out to its locally connected STOMP clients over Spring WebSocket."

---

### Q5: Why do you need a dedicated unpooled connection for `LISTEN`? Why not use HikariCP?
**Defense:**
> "HikariCP is designed for pooled, short-lived request-response queries. If you issue a `LISTEN` on a pooled connection, HikariCP will eventually recycle, test, or evict that connection, silently terminating the event subscription. Furthermore, holding a pooled connection perpetually in a blocking `getNotifications()` loop steals a connection from the pool. Therefore, `PostgresNotificationListener` opens an independent `DriverManager.getConnection()` outside HikariCP and manages its lifecycle via Spring's `SmartLifecycle`."

---

### Q6: How do you prevent multiple pods from running the `LockReaperJob` or `WaitingRoomAdmissionJob` simultaneously?
**Defense:**
> "We use PostgreSQL transaction-level advisory locks: `SELECT pg_try_advisory_xact_lock(1001)`. The `try_` prefix makes the call non-blocking: if Pod 1 acquires it, Pods 2 and 3 get `false` immediately and exit cleanly without waiting. The `_xact_` suffix scopes the lock to the current database transaction. This means if Pod 1 crashes or is terminated by Kubernetes mid-execution, the PostgreSQL transaction aborts and automatically releases the lock immediately, with zero stale lock timeouts."

---

### Q7: What happens if a pod crashes while processing a payment mid-booking?
**Defense:**
> "Two protective layers kick in:
> 1. **PostgreSQL Rollback**: The TCP connection from the killed pod drops, causing PostgreSQL to immediately abort the in-flight uncommitted transaction, releasing any row locks held.
> 2. **Lock Reaper**: If the seat was already locked in a prior transaction and the pod dies during the client's payment phase, the seat remains in `LOCKED` status. The `LockReaperJob` periodically scans for seats where `status = 'LOCKED'` and `locked_at < (now - TTL)`. It atomically resets them to `AVAILABLE` and logs an audit record with `actor_type = 'REAPER'`."

---

### Q8: How is payment processing decoupled from the database row lock?
**Defense:**
> "Holding database locks during external third-party HTTP calls (e.g., Stripe) is an anti-pattern that leads to connection pool starvation. In SeatLock, the seat is locked in a fast, independent transaction (`tryLockSeat`), transitioning the status to `LOCKED` and persisting the owner's `userId` and `locked_at` timestamp. The payment is processed outside any database transaction lock. Once payment succeeds, a second fast transaction confirms the booking (`confirmBooking`). If payment fails, compensation logic releases the seat back to `AVAILABLE`."

---

### Q9: How do you guarantee idempotency against network retries and double clicks?
**Defense:**
> "The client generates a client-side UUID `idempotency_key` prior to dispatching the request. The very first operation in the booking flow is an `INSERT INTO booking_requests (idempotency_key, seat_id, user_id, status)`. The table enforces a hard database `UNIQUE (idempotency_key)` constraint. If a duplicate request arrives (due to retry or double-click), the `INSERT` throws a `DataIntegrityViolationException`. The catch block queries for the existing booking associated with that idempotency key and returns the identical response with `status = 'DUPLICATE'`, ensuring payment is never charged twice."

---

### Q10: How does the Waiting Room guarantee FIFO admission under high concurrency?
**Defense:**
> "When users enter the waiting room, they receive an incrementing position via `getNextPosition()` and a record is inserted with `status = 'WAITING'`. The `WaitingRoomAdmissionJob` runs periodically and executes an atomic `UPDATE ... RETURNING`:
> ```sql
> UPDATE waiting_room_entries
> SET status = 'ADMITTED', admitted_at = now()
> WHERE id IN (
>     SELECT id FROM waiting_room_entries
>     WHERE status = 'WAITING'
>     ORDER BY position
>     LIMIT :batchSize
>     FOR UPDATE SKIP LOCKED
> )
> RETURNING id, event_id, user_id, position;
> ```
> This guarantees that exact batches are admitted strictly in order of arrival, and `FOR UPDATE SKIP LOCKED` prevents duplicate admission across multiple workers."

---

### Q11: How do you prove zero overselling under load?
**Defense:**
> "We verify invariant correctness using automated SQL assertions following our k6 load tests:
> 1. `SELECT seat_id, count(*) FROM bookings WHERE status = 'CONFIRMED' GROUP BY seat_id HAVING count(*) > 1` must return **0 rows**.
> 2. `(SELECT count(*) FROM seats WHERE status = 'BOOKED')` must **exactly equal** `(SELECT count(*) FROM bookings WHERE status = 'CONFIRMED')`.
> In our k6 tests with 500 VUs fighting for 500 seats, both invariants hold with 100% mathematical precision."

---

### Q12: Why did you choose anonymous cookie session authentication instead of JWT / OAuth2?
**Defense:**
> "For a portfolio project focused on high-concurrency distributed systems and database locking correctness, JWT authentication would add token generation, refresh flows, and mock OAuth providers without adding distributed systems value. The `UserSessionFilter` assigns an anonymous UUID via an `HttpOnly` cookie on first visit, giving us consistent identity across WebSocket and REST calls while keeping the project lean, focused, and immediate to demonstrate."
