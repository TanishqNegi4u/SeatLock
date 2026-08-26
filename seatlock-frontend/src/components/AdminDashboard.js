import React, { useState, useEffect } from 'react';
import { getMetrics } from '../api/seatlockApi';
import './AdminDashboard.css';

function AdminDashboard({ eventId }) {
  const [metrics, setMetrics] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const data = await getMetrics(eventId);
        setMetrics(data);
        setError(null);
      } catch (e) {
        setError('Failed to load metrics');
      }
    };

    fetchMetrics();
    const interval = setInterval(fetchMetrics, 3000);
    return () => clearInterval(interval);
  }, [eventId]);

  if (error) return <div className="admin-error">{error}</div>;
  if (!metrics) return <div className="admin-loading">Loading metrics...</div>;

  const rows = [
    ['Total Seats', metrics.totalSeats, '🪑'],
    ['Available', metrics.availableSeats, '🟢'],
    ['Locked', metrics.lockedSeats, '🟡'],
    ['Booked', metrics.bookedSeats, '🔴'],
    ['Total Bookings', metrics.totalBookings, '✅'],
    ['Waiting in Queue', metrics.waitingInQueue, '⏳'],
    ['Admitted from Queue', metrics.admittedFromQueue, '🚪'],
    ['Lock Contention Count', metrics.lockContentionCount, '⚡'],
    ['Avg Booking Latency', `${metrics.avgBookingLatencyMs.toFixed(1)} ms`, '⏱️'],
  ];

  return (
    <div className="admin-dashboard">
      <h2>📊 Admin Dashboard</h2>
      <p className="last-updated">
        Last updated: {new Date(metrics.timestamp).toLocaleTimeString()}
      </p>
      <table className="metrics-table">
        <thead>
          <tr>
            <th></th>
            <th>Metric</th>
            <th>Value</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([label, value, icon]) => (
            <tr key={label}>
              <td className="icon-col">{icon}</td>
              <td>{label}</td>
              <td className="value-col">{value}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="dashboard-note">
        Auto-refreshes every 3 seconds. Contention and latency counters reset on pod restart.
      </div>
    </div>
  );
}

export default AdminDashboard;
