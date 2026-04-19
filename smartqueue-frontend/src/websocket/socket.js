import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

let stompClient = null;
const SOCKET_BASE = (process.env.REACT_APP_API_URL || "http://localhost:8080").replace(/\/$/, "");

export const connectSocket = (doctorId, onMessage) => {
    if (stompClient) {
        stompClient.deactivate();
    }

    const socket = new SockJS(`${SOCKET_BASE}/ws`);
    const topic = doctorId === "reception"
        ? "/topic/reception/overview"
        : `/topic/doctor/${doctorId}`;

    stompClient = new Client({
        webSocketFactory: () => socket,

        reconnectDelay: 5000,

        onConnect: () => {
            console.log("✅ WebSocket Connected");

            stompClient.subscribe(topic, (message) => {
                try {
                    const data = JSON.parse(message.body);
                    onMessage(data);
                } catch (error) {
                    console.error("❌ WebSocket payload error:", error);
                }
            });
        },

        onStompError: (frame) => {
            console.error("❌ STOMP error:", frame);
        },
    });

    stompClient.activate();
};

export const disconnectSocket = () => {
    if (stompClient) {
        stompClient.deactivate();
        stompClient = null;
    }
};
