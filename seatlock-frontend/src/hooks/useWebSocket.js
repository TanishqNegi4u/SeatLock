import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = process.env.REACT_APP_WS_URL || 'http://localhost:8080/ws';

export function useWebSocket(eventId) {
  const clientRef = useRef(null);
  const [connected, setConnected] = useState(false);
  const [seatUpdates, setSeatUpdates] = useState([]);
  const [queueUpdates, setQueueUpdates] = useState([]);

  useEffect(() => {
    if (!eventId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      debug: () => {}, // silence debug logs
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/event/${eventId}/seats`, (msg) => {
          const update = JSON.parse(msg.body);
          setSeatUpdates(prev => [...prev.slice(-99), update]);
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => console.error('STOMP error:', frame),
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [eventId]);

  const subscribeToQueue = useCallback((userId) => {
    if (!clientRef.current || !clientRef.current.connected) return;
    clientRef.current.subscribe(
      `/topic/event/${eventId}/queue/${userId}`,
      (msg) => setQueueUpdates(prev => [...prev.slice(-19), JSON.parse(msg.body)])
    );
  }, [eventId]);

  const consumeLastSeatUpdate = useCallback(() => {
    const last = seatUpdates[seatUpdates.length - 1];
    return last;
  }, [seatUpdates]);

  return { connected, seatUpdates, queueUpdates, subscribeToQueue, consumeLastSeatUpdate };
}
