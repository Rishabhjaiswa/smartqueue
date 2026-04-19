import { useEffect, useState } from "react";
import { getDisplayBoard } from "../services/api";
import { connectSocket, disconnectSocket } from "../websocket/socket";

export default function DisplayBoard() {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;

        const fetchData = async (showLoader = false) => {
            try {
                if (showLoader) {
                    setLoading(true);
                }

                const res = await getDisplayBoard();
                if (active) {
                    setData(res.data);
                    setError("");
                }
            } catch (err) {
                if (active) {
                    setError(
                        err?.response?.data?.message || "Unable to load display board."
                    );
                }
            } finally {
                if (active && showLoader) {
                    setLoading(false);
                }
            }
        };

        fetchData(true);
        connectSocket("reception", (message) => {
            if (!active) {
                return;
            }
            setData(message);
            setError("");
        });
        const interval = setInterval(() => {
            fetchData(false);
        }, 3000);

        return () => {
            active = false;
            disconnectSocket();
            clearInterval(interval);
        };
    }, []);

    return (
        <div style={page}>
            <style>
                {`
                    @keyframes smartqueuePulse {
                        0% { transform: scale(1); box-shadow: 0 0 0 rgba(20, 184, 166, 0.35); }
                        50% { transform: scale(1.02); box-shadow: 0 0 28px rgba(20, 184, 166, 0.45); }
                        100% { transform: scale(1); box-shadow: 0 0 0 rgba(20, 184, 166, 0.35); }
                    }
                `}
            </style>
            <div style={header}>
                <div>
                    <p style={eyebrow}>Clinic Queue Display</p>
                    <h1 style={title}>Now Serving</h1>
                </div>
                <div style={headerStats}>
                    <div style={headerBadge}>
                        Doctors: {data?.doctors?.length || 0}
                    </div>
                </div>
            </div>

            {error ? (
                <p style={{ textAlign: "center", color: "#ffb4ab", marginBottom: "20px", fontSize: "22px" }}>
                    {error}
                </p>
            ) : null}

            {loading && !data ? (
                <p style={{ textAlign: "center", fontSize: "24px" }}>Loading display board...</p>
            ) : null}

            {!loading && (!data?.doctors || data.doctors.length === 0) ? (
                <div style={emptyState}>No data available</div>
            ) : null}

            <div style={grid}>
                {data?.doctors?.map(doc => (
                    <div key={doc.doctorId} style={doctorCard(doc.active)}>
                        <div style={doctorHeader}>
                            <h2 style={doctorName}>{doc.doctorName}</h2>
                            <span style={waitBadge}>{doc.waitingCount} waiting</span>
                        </div>

                        <div style={tokenCard(doc.active)}>
                            {doc.currentToken || "—"}
                        </div>

                        <p style={tokenLabel}>Current token</p>

                        <div style={upNextSection}>
                            <div style={upNextLabel}>Up next</div>
                            <div style={nextTokenRow}>
                                {(doc.nextTokens || []).length > 0 ? (
                                    doc.nextTokens.map((token) => (
                                        <span key={token} style={nextTokenChip}>{token}</span>
                                    ))
                                ) : (
                                    <span style={nextTokenEmpty}>No waiting tokens</span>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

const page = {
    minHeight: "100vh",
    background: "linear-gradient(180deg, #0d1f2a 0%, #102b37 100%)",
    color: "#ffffff",
    padding: "40px",
    fontFamily: "Arial"
};

const header = {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "flex-end",
    gap: "20px",
    marginBottom: "32px",
    flexWrap: "wrap"
};

const eyebrow = {
    margin: 0,
    fontSize: "16px",
    letterSpacing: "0.16em",
    textTransform: "uppercase",
    color: "#8bcad3"
};

const title = {
    margin: "8px 0 0",
    fontSize: "56px",
    lineHeight: 1
};

const headerStats = {
    display: "flex",
    gap: "12px"
};

const headerBadge = {
    padding: "12px 18px",
    borderRadius: "999px",
    background: "rgba(139, 202, 211, 0.14)",
    border: "1px solid rgba(139, 202, 211, 0.24)",
    fontSize: "20px",
    fontWeight: "700",
    color: "#d5f6fb"
};

const grid = {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(360px, 1fr))",
    gap: "24px"
};

const doctorCard = (active) => ({
    background: active ? "rgba(20, 184, 166, 0.12)" : "rgba(255,255,255,0.06)",
    border: active ? "1px solid rgba(139, 202, 211, 0.36)" : "1px solid rgba(255,255,255,0.08)",
    borderRadius: "28px",
    padding: "28px",
    boxShadow: active ? "0 20px 46px rgba(14, 165, 233, 0.18)" : "0 18px 40px rgba(0, 0, 0, 0.24)"
});

const doctorHeader = {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: "12px",
    marginBottom: "22px"
};

const doctorName = {
    margin: 0,
    fontSize: "32px"
};

const waitBadge = {
    fontSize: "18px",
    fontWeight: "700",
    background: "rgba(139, 202, 211, 0.14)",
    color: "#d5f6fb",
    padding: "10px 14px",
    borderRadius: "999px"
};

const tokenCard = (active) => ({
    minHeight: "220px",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: "24px",
    background: "linear-gradient(135deg, #14b8a6 0%, #0ea5e9 100%)",
    color: "#ffffff",
    fontSize: "82px",
    fontWeight: "700",
    letterSpacing: "0.08em",
    marginBottom: "18px",
    animation: active ? "smartqueuePulse 2.2s ease-in-out infinite" : "none"
});

const tokenLabel = {
    margin: 0,
    fontSize: "22px",
    color: "#d1e9ee",
    textAlign: "center"
};

const upNextSection = {
    marginTop: "18px"
};

const upNextLabel = {
    fontSize: "16px",
    fontWeight: "700",
    color: "#d1e9ee",
    marginBottom: "12px"
};

const nextTokenRow = {
    display: "flex",
    gap: "10px",
    flexWrap: "wrap"
};

const nextTokenChip = {
    padding: "10px 14px",
    borderRadius: "999px",
    background: "rgba(255,255,255,0.09)",
    border: "1px solid rgba(255,255,255,0.12)",
    fontSize: "18px",
    fontWeight: "700",
    color: "#ffffff"
};

const nextTokenEmpty = {
    fontSize: "16px",
    color: "#b9d6db"
};

const emptyState = {
    minHeight: "240px",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: "28px",
    border: "1px dashed rgba(213, 246, 251, 0.35)",
    color: "#d5f6fb",
    fontSize: "28px",
    background: "rgba(255,255,255,0.04)",
    marginBottom: "24px"
};
