import React from 'react';
import './WaitingRoom.css';

function WaitingRoom({ queueStatus }) {
  if (!queueStatus) {
    return (
      <div className="waiting-room">
        <div className="queue-card">
          <div className="spinner"></div>
          <p>Joining the queue...</p>
        </div>
      </div>
    );
  }

  const { status, position, estimatedWaitSeconds } = queueStatus;

  if (status === 'ADMITTED') {
    return (
      <div className="waiting-room admitted">
        <div className="queue-card">
          <div className="admitted-icon">🎉</div>
          <h2>You're In!</h2>
          <p>Select your seat from the map.</p>
        </div>
      </div>
    );
  }

  const minutes = Math.floor(estimatedWaitSeconds / 60);
  const seconds = estimatedWaitSeconds % 60;

  return (
    <div className="waiting-room">
      <div className="queue-card">
        <div className="queue-icon">⏳</div>
        <h2>Virtual Waiting Room</h2>
        <div className="position-display">
          <div className="position-number">{position}</div>
          <div className="position-label">Your position</div>
        </div>
        <div className="eta">
          Estimated wait: {minutes > 0 ? `${minutes}m ` : ''}{seconds}s
        </div>
        <div className="queue-bar">
          <div className="queue-bar-fill" style={{ width: `${Math.max(5, 100 - position * 2)}%` }}></div>
        </div>
        <p className="queue-note">Stay on this page. You'll be admitted automatically.</p>
      </div>
    </div>
  );
}

export default WaitingRoom;
