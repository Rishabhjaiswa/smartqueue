import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

let stompClient = null;
const rawBase = process.env.REACT_APP_API_BASE_URL;
if (!rawBase) {
    document.body.innerHTML = "<h1 style='color:red; text-align:center; margin-top:20%; font-family:sans-serif;'>Configuration Error: REACT_APP_API_BASE_URL is missing</h1>";
    throw new Error("REACT_APP_API_BASE_URL environment variable is missing.");
}
const SOCKET_BASE = rawBase.replace(/\/$/, "");

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
