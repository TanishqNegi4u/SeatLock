import React, { useState, useEffect, useCallback } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useParams, useNavigate, useLocation } from 'react-router-dom';
import { useWebSocket } from './hooks/useWebSocket';
import { getSeatMap, joinQueue, getQueueStatus } from './api/seatlockApi';
import SeatMap from './components/SeatMap';
import WaitingRoom from './components/WaitingRoom';
import BookingFlow from './components/BookingFlow';
import AdminDashboard from './components/AdminDashboard';
import { ToastProvider } from './components/Toast';
import './App.css';

function MainLayout() {
  const { eventId: paramEventId } = useParams();
  const eventId = Number(paramEventId) || 1;
  const navigate = useNavigate();
  const location = useLocation();

  const isAdmin = location.pathname.endsWith('/admin');

  const [seatMap, setSeatMap] = useState(null);
  const [selectedSeat, setSelectedSeat] = useState(null);
  const [queueStatus, setQueueStatus] = useState(null);
  const [userId, setUserId] = useState(null);

  const { connected, seatUpdates, queueUpdates, auditUpdates, subscribeToQueue } = useWebSocket(eventId);

  // Load seat map on mount / event change
  const refreshSeatMap = useCallback(async () => {
    try {
      const data = await getSeatMap(eventId);
      setSeatMap(data);
    } catch (e) {
      console.error('Failed to load seat map:', e);
    }
  }, [eventId]);

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
    joinQueue(eventId)
      .then(data => {
        setQueueStatus(data);
        if (data.userId) setUserId(data.userId);
      })
      .catch(e => console.error('Failed to join queue:', e));
  }, [eventId]);

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
        const data = await getQueueStatus(eventId);
        setQueueStatus(data);
      } catch (e) { /* ignore */ }
    }, 3000);
    return () => clearInterval(interval);
  }, [eventId, queueStatus]);

  const isAdmitted = queueStatus?.status === 'ADMITTED';

  return (
    <div className="app">
      <header className="app-header">
        <h1 onClick={() => navigate(`/event/${eventId}`)} style={{ cursor: 'pointer' }}>
          🔒 SeatLock
        </h1>
        <div className="header-status">
          <span className={`ws-indicator ${connected ? 'connected' : 'disconnected'}`}>
            {connected ? '● Live' : '○ Offline'}
          </span>
        </div>
        <nav className="app-tabs" aria-label="Main Navigation">
          <button
            className={!isAdmin ? 'active' : ''}
            onClick={() => navigate(`/event/${eventId}`)}
          >
            🎫 Book Tickets
          </button>
          <button
            className={isAdmin ? 'active' : ''}
            onClick={() => navigate(`/event/${eventId}/admin`)}
          >
            📊 Admin
          </button>
        </nav>
      </header>

      <main className="app-main">
        {isAdmin ? (
          <AdminDashboard eventId={eventId} auditUpdates={auditUpdates} />
        ) : (
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
                    eventId={eventId}
                    onSeatLocked={refreshSeatMap}
                  />
                </div>
                <div className="booking-panel">
                  <BookingFlow
                    selectedSeat={selectedSeat}
                    eventId={eventId}
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
      </main>
    </div>
  );
}

function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Navigate to="/event/1" replace />} />
          <Route path="/admin" element={<Navigate to="/event/1/admin" replace />} />
          <Route path="/event/:eventId" element={<MainLayout />} />
          <Route path="/event/:eventId/admin" element={<MainLayout />} />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  );
}

export default App;
