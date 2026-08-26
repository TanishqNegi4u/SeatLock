import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import AdminDashboard from './AdminDashboard';
import * as api from '../api/seatlockApi';

jest.mock('../api/seatlockApi');

// Mock ResponsiveContainer for recharts in JSDOM
jest.mock('recharts', () => {
  const OriginalModule = jest.requireActual('recharts');
  return {
    ...OriginalModule,
    ResponsiveContainer: ({ children }) => (
      <div className="recharts-responsive-container" style={{ width: 500, height: 200 }}>
        {children}
      </div>
    ),
  };
});

describe('AdminDashboard Component', () => {
  const mockMetrics = {
    eventId: 1,
    totalSeats: 500,
    availableSeats: 480,
    lockedSeats: 10,
    bookedSeats: 10,
    totalBookings: 10,
    waitingInQueue: 5,
    admittedFromQueue: 15,
    lockContentionCount: 3,
    avgBookingLatencyMs: 42.5,
    podHostname: 'seatlock-backend-pod-1',
    timestamp: new Date().toISOString(),
  };

  const mockHistory = [
    {
      timestamp: new Date().toISOString(),
      lockContentionCount: 3,
      avgBookingLatencyMs: 42.5,
      availableSeats: 480,
      lockedSeats: 10,
      bookedSeats: 10,
      podHostname: 'seatlock-backend-pod-1',
    },
  ];

  const mockAuditLogs = [
    {
      id: 101,
      seatId: 1,
      eventId: 1,
      fromStatus: 'LOCKED',
      toStatus: 'BOOKED',
      actorUserId: '123e4567-e89b-12d3-a456-426614174000',
      actorType: 'USER',
      reason: 'Payment successful',
      podHostname: 'seatlock-backend-pod-1',
      createdAt: new Date().toISOString(),
    },
  ];

  beforeEach(() => {
    api.getMetrics.mockResolvedValue(mockMetrics);
    api.getMetricsHistory.mockResolvedValue(mockHistory);
    api.getAuditLog.mockResolvedValue(mockAuditLogs);
  });

  test('renders stat cards with real-time metrics', async () => {
    render(<AdminDashboard eventId={1} auditUpdates={[]} />);

    await waitFor(() => {
      expect(screen.getByText(/Distributed Engine Telemetry/i)).toBeInTheDocument();
      expect(screen.getByText('480')).toBeInTheDocument();
      expect(screen.getByText('Available Seats')).toBeInTheDocument();
      expect(screen.getByText('Active Locks')).toBeInTheDocument();
      expect(screen.getByText('Lock Contention')).toBeInTheDocument();
    });
  });

  test('renders live audit stream entries and pod hostname tags', async () => {
    render(<AdminDashboard eventId={1} auditUpdates={[]} />);

    await waitFor(() => {
      expect(screen.getByText(/Live Audit & Replication Stream/i)).toBeInTheDocument();
      expect(screen.getByText('Seat #1')).toBeInTheDocument();
      expect(screen.getByText('Payment successful')).toBeInTheDocument();
    });
  });
});
