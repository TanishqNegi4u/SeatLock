import React, { useState, useCallback } from 'react';
import { bookSeat, releaseSeat } from '../api/seatlockApi';
import './BookingFlow.css';

function BookingFlow({ selectedSeat, eventId, onBookingComplete }) {
  const [phase, setPhase] = useState('idle'); // idle | confirming | processing | success | failed
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleConfirm = useCallback(async () => {
    if (!selectedSeat) return;
    setPhase('processing');
    setError(null);

    const idempotencyKey = crypto.randomUUID();

    try {
      const res = await bookSeat(eventId, selectedSeat.id, idempotencyKey);
      if (res.status === 'CONFIRMED' || res.status === 'DUPLICATE') {
        setPhase('success');
        setResult(res);
      } else {
        setPhase('failed');
        setError(res.message || 'Booking failed');
      }
    } catch (e) {
      setPhase('failed');
      setError(e.message || 'Booking failed');
    }
  }, [selectedSeat, eventId]);

  const handleCancel = useCallback(async () => {
    if (!selectedSeat) return;
    try {
      await releaseSeat(eventId, selectedSeat.id);
    } catch (e) { /* ignore */ }
    onBookingComplete();
    setPhase('idle');
    setResult(null);
    setError(null);
  }, [selectedSeat, eventId, onBookingComplete]);

  const handleDone = useCallback(() => {
    onBookingComplete();
    setPhase('idle');
    setResult(null);
    setError(null);
  }, [onBookingComplete]);

  if (!selectedSeat && phase === 'idle') {
    return (
      <div className="booking-flow">
        <div className="booking-card empty">
          <div className="empty-icon">👆</div>
          <p>Select a seat from the map to begin booking.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="booking-flow">
      <div className="booking-card">
        {phase === 'success' ? (
          <div className="booking-success">
            <div className="success-icon">✅</div>
            <h3>Booking Confirmed!</h3>
            <p className="booking-detail">Seat: <strong>{result?.seatLabel}</strong></p>
            <p className="booking-detail">Booking #{result?.bookingId}</p>
            <button className="btn btn-primary" onClick={handleDone}>Done</button>
          </div>
        ) : phase === 'failed' ? (
          <div className="booking-failed">
            <div className="fail-icon">❌</div>
            <h3>Booking Failed</h3>
            <p className="error-msg">{error}</p>
            <button className="btn btn-secondary" onClick={handleDone}>Try Again</button>
          </div>
        ) : (
          <>
            <h3>Selected Seat</h3>
            <div className="seat-detail">
              <div className="seat-label-big">{selectedSeat?.label}</div>
              <div className="seat-meta">
                Section {selectedSeat?.sectionName} · Row {selectedSeat?.rowNumber} · Seat {selectedSeat?.seatNumber}
              </div>
            </div>

            {phase === 'processing' ? (
              <div className="processing">
                <div className="spinner small"></div>
                <p>Processing payment...</p>
              </div>
            ) : (
              <div className="booking-actions">
                <button className="btn btn-primary" onClick={handleConfirm}>
                  💳 Confirm & Pay
                </button>
                <button className="btn btn-secondary" onClick={handleCancel}>
                  Cancel
                </button>
              </div>
            )}

            <p className="lock-timer">⏱ Lock expires in 5 minutes</p>
          </>
        )}
      </div>
    </div>
  );
}

export default BookingFlow;
