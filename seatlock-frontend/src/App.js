import React, { useState, useEffect, useCallback } from 'react';
import { useWebSocket } from './hooks/useWebSocket';
import { getSeatMap, joinQueue, getQueueStatus } from './api/seatlockApi';
import SeatMap from './components/SeatMap';
import WaitingRoom from './components/WaitingRoom';
import BookingFlow from './components/BookingFlow';
import AdminDashboard from './components/AdminDashboard';
import './App.css';

const EVENT_ID = 1;

function App() {
  const [tab, setTab] = useState('book');
  const [seatMap, setSeatMap] = useState(null);
  const [selectedSeat, setSelectedSeat] = useState(null);
  const [queueStatus, setQueueStatus] = useState(null);
  const [userId, setUserId] = useState(null);
  const { connected, seatUpdates, queueUpdates, subscribeToQueue } = useWebSocket(EVENT_ID);

  // Load seat map on mount
  const refreshSeatMap = useCallback(async () => {
    try {
      const data = await getSeatMap(EVENT_ID);
      setSeatMap(data);
    } catch (e) {
      console.error('Failed to load seat map:', e);
    }
  }, []);

  useEffect(() => { refreshSeatMap(); }, [refreshSeatMap]);

  // Apply live seat updates to local state
  useEffect(() => {
    if (seatUpdates.length === 0 || !seatMap) return;
    const latest = seatUpdates[seatUpdates.length - 1];
    setSeatMap(prev => {
      if (!prev) return prev;
      const updated = { ...prev };
      const section = updated.sectionSeats[latest.sectionName];
      if (section) {
        const idx = section.findIndex(s => s.id === latest.seatId);
        if (idx >= 0) {
          section[idx] = {
            ...section[idx],
            status: latest.status,
            lockedBy: latest.lockedBy,
          };
        }
      }
      // Recalc counts
      let available = 0, locked = 0, booked = 0;
      Object.values(updated.sectionSeats).forEach(seats => {
        seats.forEach(s => {
          if (s.status === 'AVAILABLE') available++;
          else if (s.status === 'LOCKED') locked++;
          else if (s.status === 'BOOKED') booked++;
        });
      });
      return { ...updated, availableSeats: available, lockedSeats: locked, bookedSeats: booked };
    });
  }, [seatUpdates, seatMap]);

  // Extract userId from cookie
  useEffect(() => {
    const match = document.cookie.match(/seatlock_user_id=([^;]+)/);
    if (match) setUserId(match[1]);
  }, [queueStatus]);

  // Join queue on mount
  useEffect(() => {
    joinQueue(EVENT_ID)
      .then(data => {
        setQueueStatus(data);
        // Extract userId from response
        if (data.userId) setUserId(data.userId);
      })
      .catch(e => console.error('Failed to join queue:', e));
  }, []);

  // Subscribe to queue updates once userId is known
  useEffect(() => {
    if (userId && connected) {
      subscribeToQueue(userId);
    }
  }, [userId, connected, subscribeToQueue]);

  // Apply queue updates
  useEffect(() => {
    if (queueUpdates.length === 0) return;
    const latest = queueUpdates[queueUpdates.length - 1];
    setQueueStatus(prev => ({
      ...prev,
      status: latest.status,
      position: latest.position,
      estimatedWaitSeconds: latest.estimatedWaitSeconds,
    }));
  }, [queueUpdates]);

  // Poll queue status as fallback
  useEffect(() => {
    if (!queueStatus || queueStatus.status === 'ADMITTED') return;
    const interval = setInterval(async () => {
      try {
        const data = await getQueueStatus(EVENT_ID);
        setQueueStatus(data);
      } catch (e) { /* ignore */ }
    }, 3000);
    return () => clearInterval(interval);
  }, [queueStatus]);

  const isAdmitted = queueStatus?.status === 'ADMITTED';

  return (
    <div className="app">
      <header className="app-header">
        <h1>🔒 SeatLock</h1>
        <div className="header-status">
          <span className={`ws-indicator ${connected ? 'connected' : 'disconnected'}`}>
            {connected ? '● Live' : '○ Offline'}
          </span>
        </div>
        <nav className="app-tabs">
          <button className={tab === 'book' ? 'active' : ''} onClick={() => setTab('book')}>
            🎫 Book Tickets
          </button>
          <button className={tab === 'admin' ? 'active' : ''} onClick={() => setTab('admin')}>
            📊 Admin
          </button>
        </nav>
      </header>

      <main className="app-main">
        {tab === 'book' && (
          <div className="booking-layout">
            {!isAdmitted ? (
              <WaitingRoom queueStatus={queueStatus} />
            ) : (
              <>
                <div className="seat-map-panel">
                  <SeatMap
                    seatMap={seatMap}
                    selectedSeat={selectedSeat}
                    onSelectSeat={setSelectedSeat}
                    userId={userId}
                    eventId={EVENT_ID}
                    onSeatLocked={refreshSeatMap}
                  />
                </div>
                <div className="booking-panel">
                  <BookingFlow
                    selectedSeat={selectedSeat}
                    eventId={EVENT_ID}
                    onBookingComplete={() => {
                      setSelectedSeat(null);
                      refreshSeatMap();
                    }}
                  />
                </div>
              </>
            )}
          </div>
        )}
        {tab === 'admin' && <AdminDashboard eventId={EVENT_ID} />}
      </main>
    </div>
  );
}

export default App;
