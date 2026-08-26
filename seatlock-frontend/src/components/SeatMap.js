import React, { useCallback, useRef } from 'react';
import { lockSeat } from '../api/seatlockApi';
import { useToast } from './Toast';
import './SeatMap.css';

function SeatMap({ seatMap, selectedSeat, onSelectSeat, userId, eventId, onSeatLocked }) {
  const toast = useToast();
  const seatGridRef = useRef(null);

  const handleSeatClick = useCallback(async (seat) => {
    if (seat.status !== 'AVAILABLE') return;
    try {
      const locked = await lockSeat(eventId, seat.id);
      onSelectSeat(locked);
      toast.success(`Seat ${seat.label} locked! You have 5 minutes to complete payment.`);
      if (onSeatLocked) onSeatLocked();
    } catch (e) {
      if (e.status === 429) {
        toast.warning('Rate limit exceeded. Please wait 10 seconds before locking another seat.');
      } else {
        toast.error(e.message || 'Seat is no longer available. Please select another seat.');
      }
    }
  }, [eventId, onSelectSeat, onSeatLocked, toast]);

  const handleKeyDown = (e, seat) => {
    const currentBtn = e.target;
    let targetBtn = null;

    if (e.key === 'ArrowRight') {
      targetBtn = currentBtn.nextElementSibling;
    } else if (e.key === 'ArrowLeft') {
      targetBtn = currentBtn.previousElementSibling;
    } else if (e.key === 'ArrowDown') {
      // 10 seats per row in grid
      let next = currentBtn;
      for (let i = 0; i < 10 && next; i++) {
        next = next.nextElementSibling;
      }
      targetBtn = next;
    } else if (e.key === 'ArrowUp') {
      let prev = currentBtn;
      for (let i = 0; i < 10 && prev; i++) {
        prev = prev.previousElementSibling;
      }
      targetBtn = prev;
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleSeatClick(seat);
      return;
    }

    if (targetBtn && targetBtn.classList.contains('seat')) {
      e.preventDefault();
      targetBtn.focus();
    }
  };

  if (!seatMap) return <div className="seat-map-loading">Loading seat map...</div>;

  return (
    <div className="seat-map" ref={seatGridRef}>
      <div className="seat-map-header">
        <h2>{seatMap.eventName}</h2>
        <div className="seat-counts">
          <span className="count available">{seatMap.availableSeats} available</span>
          <span className="count locked">{seatMap.lockedSeats} locked</span>
          <span className="count booked">{seatMap.bookedSeats} booked</span>
        </div>
      </div>

      <div className="stage" aria-hidden="true">STAGE</div>

      <div className="sections" role="region" aria-label="Seat Selection Sections">
        {Object.entries(seatMap.sectionSeats).map(([sectionName, seats]) => (
          <div key={sectionName} className="section">
            <div className="section-label" aria-hidden="true">Section {sectionName}</div>
            <div className="seat-grid" role="group" aria-label={`Section ${sectionName} seats`}>
              {seats.map(seat => {
                const isSelected = selectedSeat?.id === seat.id;
                const isMine = seat.lockedBy === userId;
                const accessibleLabel = `Seat ${seat.label}, status ${seat.status.toLowerCase()}${isMine ? ', locked by you' : ''}`;

                return (
                  <button
                    key={seat.id}
                    className={`seat ${seat.status.toLowerCase()} ${isSelected ? 'selected' : ''} ${isMine ? 'mine' : ''}`}
                    onClick={() => handleSeatClick(seat)}
                    onKeyDown={(e) => handleKeyDown(e, seat)}
                    disabled={seat.status !== 'AVAILABLE'}
                    title={`${seat.label} — ${seat.status}${isMine ? ' (yours)' : ''}`}
                    aria-label={accessibleLabel}
                    tabIndex={seat.status === 'AVAILABLE' ? 0 : -1}
                  >
                    {seat.seatNumber}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </div>

      <div className="legend" aria-label="Seat Status Legend">
        <span className="legend-item"><span className="dot available" aria-hidden="true"></span> Available</span>
        <span className="legend-item"><span className="dot locked" aria-hidden="true"></span> Locked</span>
        <span className="legend-item"><span className="dot booked" aria-hidden="true"></span> Booked</span>
        <span className="legend-item"><span className="dot selected" aria-hidden="true"></span> Selected</span>
      </div>
    </div>
  );
}

export default SeatMap;
