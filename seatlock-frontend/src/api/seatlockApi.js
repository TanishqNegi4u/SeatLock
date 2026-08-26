const API_BASE = process.env.REACT_APP_API_URL || 'http://localhost:8080';

async function request(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
  const data = await res.json();
  if (!res.ok) throw { status: res.status, ...data };
  return data;
}

export const getEvents = () => request('/api/events');

export const getSeatMap = (eventId) => request(`/api/events/${eventId}/seats`);

export const lockSeat = (eventId, seatId) =>
  request(`/api/events/${eventId}/seats/${seatId}/lock`, { method: 'POST' });

export const releaseSeat = (eventId, seatId) =>
  request(`/api/events/${eventId}/seats/${seatId}/lock`, { method: 'DELETE' });

export const bookSeat = (eventId, seatId, idempotencyKey) =>
  request(`/api/events/${eventId}/book`, {
    method: 'POST',
    body: JSON.stringify({ seatId, idempotencyKey }),
  });

export const joinQueue = (eventId) =>
  request(`/api/events/${eventId}/queue`, { method: 'POST' });

export const getQueueStatus = (eventId) =>
  request(`/api/events/${eventId}/queue/status`);

export const getMetrics = (eventId = 1) =>
  request(`/api/admin/metrics?eventId=${eventId}`);
