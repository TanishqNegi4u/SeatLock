import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import SeatMap from './SeatMap';
import * as api from '../api/seatlockApi';

jest.mock('../api/seatlockApi');

describe('SeatMap Component', () => {
  const mockSeatMap = {
    eventId: 1,
    eventName: 'SeatLock Concert 2026',
    totalSeats: 3,
    availableSeats: 1,
    lockedSeats: 1,
    bookedSeats: 1,
    sectionSeats: {
      A: [
        { id: 1, sectionName: 'A', rowNumber: 1, seatNumber: 1, status: 'AVAILABLE', lockedBy: null, label: 'A-1-1' },
        { id: 2, sectionName: 'A', rowNumber: 1, seatNumber: 2, status: 'LOCKED', lockedBy: 'other-user', label: 'A-1-2' },
        { id: 3, sectionName: 'A', rowNumber: 1, seatNumber: 3, status: 'BOOKED', lockedBy: null, label: 'A-1-3' },
      ],
    },
  };

  test('renders event name and summary seat counts', () => {
    render(
      <SeatMap
        seatMap={mockSeatMap}
        selectedSeat={null}
        onSelectSeat={jest.fn()}
        userId="user-123"
        eventId={1}
        onSeatLocked={jest.fn()}
      />
    );

    expect(screen.getByText('SeatLock Concert 2026')).toBeInTheDocument();
    expect(screen.getByText('1 available')).toBeInTheDocument();
    expect(screen.getByText('1 locked')).toBeInTheDocument();
    expect(screen.getByText('1 booked')).toBeInTheDocument();
  });

  test('renders distinct classes and states for available, locked, and booked seats', () => {
    render(
      <SeatMap
        seatMap={mockSeatMap}
        selectedSeat={null}
        onSelectSeat={jest.fn()}
        userId="user-123"
        eventId={1}
        onSeatLocked={jest.fn()}
      />
    );

    const seat1 = screen.getByTitle('A-1-1 — AVAILABLE');
    const seat2 = screen.getByTitle('A-1-2 — LOCKED');
    const seat3 = screen.getByTitle('A-1-3 — BOOKED');

    expect(seat1).toHaveClass('available');
    expect(seat1).not.toBeDisabled();

    expect(seat2).toHaveClass('locked');
    expect(seat2).toBeDisabled();

    expect(seat3).toHaveClass('booked');
    expect(seat3).toBeDisabled();
  });

  test('calls lockSeat API when available seat is clicked', async () => {
    const mockOnSelectSeat = jest.fn();
    const mockOnSeatLocked = jest.fn();
    const lockedSeatResult = { id: 1, label: 'A-1-1', status: 'LOCKED' };

    api.lockSeat.mockResolvedValueOnce(lockedSeatResult);

    render(
      <SeatMap
        seatMap={mockSeatMap}
        selectedSeat={null}
        onSelectSeat={mockOnSelectSeat}
        userId="user-123"
        eventId={1}
        onSeatLocked={mockOnSeatLocked}
      />
    );

    const seat1 = screen.getByTitle('A-1-1 — AVAILABLE');
    fireEvent.click(seat1);

    expect(api.lockSeat).toHaveBeenCalledWith(1, 1);
  });
});
