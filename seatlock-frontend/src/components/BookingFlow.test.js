import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import BookingFlow from './BookingFlow';
import * as api from '../api/seatlockApi';

jest.mock('../api/seatlockApi');

describe('BookingFlow Component', () => {
  const mockSelectedSeat = {
    id: 10,
    label: 'A-2-5',
    sectionName: 'A',
    rowNumber: 2,
    seatNumber: 5,
  };

  test('renders prompt to select seat when no seat is selected', () => {
    render(
      <BookingFlow
        selectedSeat={null}
        eventId={1}
        onBookingComplete={jest.fn()}
      />
    );

    expect(screen.getByText(/Select a seat from the map to begin booking/i)).toBeInTheDocument();
  });

  test('renders selected seat details and payment button when seat is selected', () => {
    render(
      <BookingFlow
        selectedSeat={mockSelectedSeat}
        eventId={1}
        onBookingComplete={jest.fn()}
      />
    );

    expect(screen.getByText('A-2-5')).toBeInTheDocument();
    expect(screen.getByText(/Section A · Row 2 · Seat 5/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Confirm & Pay/i })).toBeInTheDocument();
  });

  test('transitions to confirmed success state upon successful booking API response', async () => {
    const mockOnBookingComplete = jest.fn();
    api.bookSeat.mockResolvedValueOnce({
      status: 'CONFIRMED',
      bookingId: 42,
      seatLabel: 'A-2-5',
    });

    render(
      <BookingFlow
        selectedSeat={mockSelectedSeat}
        eventId={1}
        onBookingComplete={mockOnBookingComplete}
      />
    );

    const payButton = screen.getByRole('button', { name: /Confirm & Pay/i });
    fireEvent.click(payButton);

    await waitFor(() => {
      expect(screen.getByText('Booking Confirmed!')).toBeInTheDocument();
      expect(screen.getByText('Booking #42')).toBeInTheDocument();
    });
  });

  test('transitions to failed state upon payment failure or conflict', async () => {
    api.bookSeat.mockResolvedValueOnce({
      status: 'FAILED',
      message: 'Payment rejected by bank',
    });

    render(
      <BookingFlow
        selectedSeat={mockSelectedSeat}
        eventId={1}
        onBookingComplete={jest.fn()}
      />
    );

    const payButton = screen.getByRole('button', { name: /Confirm & Pay/i });
    fireEvent.click(payButton);

    await waitFor(() => {
      expect(screen.getByText('Booking Failed')).toBeInTheDocument();
      expect(screen.getByText('Payment rejected by bank')).toBeInTheDocument();
    });
  });
});
