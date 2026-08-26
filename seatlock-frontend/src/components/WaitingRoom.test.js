import React from 'react';
import { render, screen } from '@testing-library/react';
import WaitingRoom from './WaitingRoom';

describe('WaitingRoom Component', () => {
  test('renders spinner and joining prompt when queue status is not yet loaded', () => {
    render(<WaitingRoom queueStatus={null} />);
    expect(screen.getByText('Joining the queue...')).toBeInTheDocument();
  });

  test('renders position number and estimated wait time when in WAITING status', () => {
    const queueStatus = {
      status: 'WAITING',
      position: 15,
      estimatedWaitSeconds: 45,
    };

    render(<WaitingRoom queueStatus={queueStatus} />);
    expect(screen.getByText('Virtual Waiting Room')).toBeInTheDocument();
    expect(screen.getByText('15')).toBeInTheDocument();
    expect(screen.getByText(/Estimated wait: 45s/i)).toBeInTheDocument();
  });

  test('renders admission success message when in ADMITTED status', () => {
    const queueStatus = {
      status: 'ADMITTED',
      position: 1,
      estimatedWaitSeconds: 0,
    };

    render(<WaitingRoom queueStatus={queueStatus} />);
    expect(screen.getByText("You're In!")).toBeInTheDocument();
    expect(screen.getByText('Select your seat from the map.')).toBeInTheDocument();
  });
});
