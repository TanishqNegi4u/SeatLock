import React, { useCallback } from 'react';
import { lockSeat } from '../api/seatlockApi';
import './SeatMap.css';

function SeatMap({ seatMap, selectedSeat, onSelectSeat, userId, eventId, onSeatLocked }) {
  const handleSeatClick = useCallback(async (seat) => {
    if (seat.status !== 'AVAILABLE') return;
    try {
      const locked = await lockSeat(eventId, seat.id);
      onSelectSeat(locked);
      onSeatLocked();
    } catch (e) {
      alert(e.message || 'Seat is no longer available');
    }
  }, [eventId, onSelectSeat, onSeatLocked]);

  if (!seatMap) return <div className="seat-map-loading">Loading seat map...</div>;

  return (
    <div className="seat-map">
      <div className="seat-map-header">
        <h2>{seatMap.eventName}</h2>
        <div className="seat-counts">
          <span className="count available">{seatMap.availableSeats} available</span>
          <span className="count locked">{seatMap.lockedSeats} locked</span>
          <span className="count booked">{seatMap.bookedSeats} booked</span>
        </div>
      </div>

      <div className="stage">STAGE</div>

      <div className="sections">
        {Object.entries(seatMap.sectionSeats).map(([sectionName, seats]) => (
          <div key={sectionName} className="section">
            <div className="section-label">Section {sectionName}</div>
            <div className="seat-grid">
              {seats.map(seat => {
                const isSelected = selectedSeat?.id === seat.id;
                const isMine = seat.lockedBy === userId;
                return (
                  <button
                    key={seat.id}
                    className={`seat ${seat.status.toLowerCase()} ${isSelected ? 'selected' : ''} ${isMine ? 'mine' : ''}`}
                    onClick={() => handleSeatClick(seat)}
                    disabled={seat.status !== 'AVAILABLE'}
                    title={`${seat.label} — ${seat.status}${isMine ? ' (yours)' : ''}`}
                  >
                    {seat.seatNumber}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </div>

      <div className="legend">
        <span className="legend-item"><span className="dot available"></span> Available</span>
        <span className="legend-item"><span className="dot locked"></span> Locked</span>
        <span className="legend-item"><span className="dot booked"></span> Booked</span>
        <span className="legend-item"><span className="dot selected"></span> Selected</span>
      </div>
    </div>
  );
}

export default SeatMap;
