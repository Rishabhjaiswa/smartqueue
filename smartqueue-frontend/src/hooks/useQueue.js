import { useEffect, useState, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export default function useQueue(officeId) {
    const [queueState, setQueueState] = useState({
        currentToken: '',
        waitingCount: 0,
        avgWaitMinutes: 0,
        nextTokens: [],
    });
    const [connected, setConnected] = useState(false);
    const clientRef = useRef(null);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
            reconnectDelay: 3000,

            onConnect: () => {
                setConnected(true);

                client.subscribe(`/topic/queue/${officeId}`, (message) => {
                    const data = JSON.parse(message.body);
                    setQueueState(data);
                });

                client.publish({
                    destination: `/app/queue.subscribe/${officeId}`,
                    body: '',
                });
            },

            onDisconnect: () => setConnected(false),
            onStompError: (frame) => console.error('STOMP error', frame),
        });

        client.activate();
        clientRef.current = client;

        return () => client.deactivate();
    }, [officeId]);

    return { queueState, connected };
}