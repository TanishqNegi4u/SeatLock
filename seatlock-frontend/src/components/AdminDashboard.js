import React, { useState, useEffect } from 'react';
import {
  LineChart, Line, AreaChart, Area, XAxis, YAxis, Tooltip,
  ResponsiveContainer, CartesianGrid, Legend
} from 'recharts';
import { getMetrics, getAuditLog, getMetricsHistory } from '../api/seatlockApi';
import './AdminDashboard.css';

function AdminDashboard({ eventId = 1, auditUpdates = [] }) {
  const [metrics, setMetrics] = useState(null);
  const [auditLogs, setAuditLogs] = useState([]);
  const [historyData, setHistoryData] = useState([]);
  const [error, setError] = useState(null);

  // Poll metrics & history every 3s
  useEffect(() => {
    let isMounted = true;

    const loadData = async () => {
      try {
        const [m, hist, logs] = await Promise.all([
          getMetrics(eventId),
          getMetricsHistory(eventId).catch(() => []),
          getAuditLog(eventId, 30).catch(() => []),
        ]);

        if (isMounted) {
          setMetrics(m);
          setAuditLogs(logs);
          setHistoryData(hist.map((pt, idx) => ({
            ...pt,
            time: new Date(pt.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
            contention: pt.lockContentionCount,
            latency: Number(pt.avgBookingLatencyMs.toFixed(1)),
          })));
          setError(null);
        }
      } catch (e) {
        if (isMounted) setError('Failed to load metrics');
      }
    };

    loadData();
    const interval = setInterval(loadData, 3000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [eventId]);

  // Merge live incoming WebSocket audit updates into the feed
  useEffect(() => {
    if (auditUpdates && auditUpdates.length > 0) {
      setAuditLogs(prev => {
        const existingIds = new Set(prev.map(l => l.id));
        const newLogs = auditUpdates.filter(u => !existingIds.has(u.id));
        return [...newLogs, ...prev].slice(0, 50);
      });
    }
  }, [auditUpdates]);

  if (error && !metrics) return <div className="admin-error">{error}</div>;
  if (!metrics) return <div className="admin-loading">Loading telemetry dashboard...</div>;

  return (
    <div className="admin-dashboard-container">
      <div className="admin-header">
        <div>
          <h2>📊 Distributed Engine Telemetry</h2>
          <p className="subtitle">
            PostgreSQL Concurrency, Resilience & Multi-Replica Audit Stream
          </p>
        </div>
        <div className="pod-pill">
          <span className="pod-indicator"></span>
          Pod: <code>{metrics.podHostname || 'local-replica'}</code>
        </div>
      </div>

      {/* ── Stat Cards Grid ────────────────────────────────────────── */}
      <div className="stat-cards-grid">
        <div className="stat-card green-card">
          <div className="stat-icon">🟢</div>
          <div className="stat-body">
            <div className="stat-num">{metrics.availableSeats}</div>
            <div className="stat-title">Available Seats</div>
          </div>
        </div>

        <div className="stat-card yellow-card">
          <div className="stat-icon">🟡</div>
          <div className="stat-body">
            <div className="stat-num">{metrics.lockedSeats}</div>
            <div className="stat-title">Active Locks</div>
          </div>
        </div>

        <div className="stat-card red-card">
          <div className="stat-icon">🔴</div>
          <div className="stat-body">
            <div className="stat-num">{metrics.bookedSeats}</div>
            <div className="stat-title">Booked Seats</div>
          </div>
        </div>

        <div className="stat-card blue-card">
          <div className="stat-icon">✅</div>
          <div className="stat-body">
            <div className="stat-num">{metrics.totalBookings}</div>
            <div className="stat-title">Total Bookings</div>
          </div>
        </div>

        <div className="stat-card purple-card">
          <div className="stat-icon">⏳</div>
          <div className="stat-body">
            <div className="stat-num">{metrics.waitingInQueue}</div>
            <div className="stat-title">Queue Waiting</div>
          </div>
        </div>

        <div className="stat-card orange-card">
          <div className="stat-icon">⚡</div>
          <div className="stat-body">
            <div className="stat-num">{metrics.lockContentionCount}</div>
            <div className="stat-title">Lock Contention</div>
          </div>
        </div>

        <div className="stat-card cyan-card">
          <div className="stat-icon">⏱️</div>
          <div className="stat-body">
            <div className="stat-num">{metrics.avgBookingLatencyMs.toFixed(1)}<span className="stat-unit">ms</span></div>
            <div className="stat-title">Avg Latency</div>
          </div>
        </div>

        <div className="stat-card slate-card">
          <div className="stat-icon">🚪</div>
          <div className="stat-body">
            <div className="stat-num">{metrics.admittedFromQueue}</div>
            <div className="stat-title">Admitted Users</div>
          </div>
        </div>
      </div>

      {/* ── Real-time Charts ───────────────────────────────────────── */}
      <div className="charts-grid">
        <div className="chart-panel">
          <div className="chart-title">⚡ Lock Contention & Booking Latency Trend</div>
          <div className="chart-wrapper">
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={historyData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="time" stroke="#64748b" tick={{ fontSize: 11 }} />
                <YAxis yAxisId="left" stroke="#f97316" tick={{ fontSize: 11 }} />
                <YAxis yAxisId="right" orientation="right" stroke="#38bdf8" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', borderColor: '#475569', borderRadius: '8px', color: '#f8fafc' }}
                />
                <Legend />
                <Line yAxisId="left" type="monotone" dataKey="contention" name="Contention Events" stroke="#f97316" strokeWidth={2} dot={false} />
                <Line yAxisId="right" type="monotone" dataKey="latency" name="Latency (ms)" stroke="#38bdf8" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="chart-panel">
          <div className="chart-title">🪑 Seat Inventory Distribution Trend</div>
          <div className="chart-wrapper">
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={historyData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="time" stroke="#64748b" tick={{ fontSize: 11 }} />
                <YAxis stroke="#94a3b8" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', borderColor: '#475569', borderRadius: '8px', color: '#f8fafc' }}
                />
                <Legend />
                <Area type="monotone" dataKey="availableSeats" name="Available" stackId="1" stroke="#22c55e" fill="#22c55e" fillOpacity={0.4} />
                <Area type="monotone" dataKey="lockedSeats" name="Locked" stackId="1" stroke="#eab308" fill="#eab308" fillOpacity={0.4} />
                <Area type="monotone" dataKey="bookedSeats" name="Booked" stackId="1" stroke="#ef4444" fill="#ef4444" fillOpacity={0.4} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* ── Live Streaming Audit Log Panel ─────────────────────────── */}
      <div className="audit-log-panel">
        <div className="audit-header">
          <div className="audit-title-wrapper">
            <h3>📜 Live Audit & Replication Stream</h3>
            <span className="live-badge">LIVE WEBSOCKET</span>
          </div>
          <div className="audit-note">All state mutations broadcast via Postgres LISTEN/NOTIFY</div>
        </div>

        <div className="audit-table-wrapper">
          <table className="audit-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Seat ID</th>
                <th>Transition</th>
                <th>Actor</th>
                <th>Pod Replica</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              {auditLogs.length === 0 ? (
                <tr>
                  <td colSpan="6" className="empty-audit">No audit events captured yet</td>
                </tr>
              ) : (
                auditLogs.map((log, idx) => (
                  <tr key={log.id || `log-${idx}`}>
                    <td className="time-cell">
                      {new Date(log.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                    </td>
                    <td>
                      <code>Seat #{log.seatId}</code>
                    </td>
                    <td>
                      <span className="status-transition">
                        <span className={`status-tag status-${(log.fromStatus || 'INIT').toLowerCase()}`}>
                          {log.fromStatus || 'INIT'}
                        </span>
                        <span className="arrow">→</span>
                        <span className={`status-tag status-${(log.toStatus || 'UNKNOWN').toLowerCase()}`}>
                          {log.toStatus}
                        </span>
                      </span>
                    </td>
                    <td>
                      <span className={`actor-badge actor-${(log.actorType || 'SYSTEM').toLowerCase()}`}>
                        {log.actorType}
                      </span>
                    </td>
                    <td>
                      <code className="pod-code">{log.podHostname || 'local-pod'}</code>
                    </td>
                    <td className="reason-cell">{log.reason || '-'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default AdminDashboard;
