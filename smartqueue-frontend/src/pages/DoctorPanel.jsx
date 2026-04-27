import { useEffect, useState } from "react";
import {
    callNext,
    completeConsultation,
    extendConsultation,
    setDoctorAvailability,
    downloadVisitReport
} from "../services/api";
import { connectSocket, disconnectSocket } from "../websocket/socket";
import { getDoctorQueue } from "../services/api";
import { getVisitTypeLabel } from "../utils/medicalLabels";
import useAuth from "../hooks/useAuth";

export default function DoctorPanel() {
    const { user } = useAuth();
    const doctorId = user?.doctorId;

    const [queue, setQueue] = useState(null);
    const [loading, setLoading] = useState(true);
    const [actionLoading, setActionLoading] = useState(false);
    const [reportLoading, setReportLoading] = useState(false);
    const [error, setError] = useState("");
    const [available, setAvailable] = useState(Boolean(user?.available));
    const [remainingSeconds, setRemainingSeconds] = useState(null);

    useEffect(() => {
        setAvailable(Boolean(user?.available));
    }, [user?.available]);

    useEffect(() => {
        setRemainingSeconds(queue?.remainingConsultationSeconds ?? null);
    }, [queue?.remainingConsultationSeconds]);

    useEffect(() => {
        if (remainingSeconds == null || remainingSeconds <= 0) {
            return undefined;
        }

        const timer = setInterval(() => {
            setRemainingSeconds((current) => {
                if (current == null || current <= 1) {
                    clearInterval(timer);
                    return 0;
                }
                return current - 1;
            });
        }, 1000);

        return () => clearInterval(timer);
    }, [remainingSeconds]);

    useEffect(() => {
        if (!doctorId) {
            setLoading(false);
            return undefined;
        }

        let active = true;

        const refreshQueue = async (showLoader = false) => {
            try {
                if (showLoader) {
                    setLoading(true);
                }

                const res = await getDoctorQueue();
                if (active) {
                    setQueue(res.data);
                    setError("");
                }
            } catch (err) {
                if (active) {
                    setError(
                        err?.response?.data?.detail || err?.response?.data?.message || "Unable to load doctor queue."
                    );
                }
            } finally {
                if (active && showLoader) {
                    setLoading(false);
                }
            }
        };

        refreshQueue(true);

        connectSocket(doctorId, (data) => {
            if (!active) {
                return;
            }

            if (data?.doctorId || data?.currentTokenId !== undefined) {
                setQueue(data);
                setError("");
            } else if (data?.payload) {
                setQueue(data.payload);
                setError("");
            }
        });

        const interval = setInterval(async () => {
            await refreshQueue(false);
        }, 3000);

        return () => {
            active = false;
            disconnectSocket();
            clearInterval(interval);
        };
    }, [doctorId]);

    const handleCallNext = async () => {
        setActionLoading(true);
        setError("");
        try {
            await callNext();
            const res = await getDoctorQueue();
            setQueue(res.data);
        } catch (err) {
            setError(
                err?.response?.data?.detail || err?.response?.data?.message || "Unable to call next patient."
            );
        } finally {
            setActionLoading(false);
        }
    };

    const handleComplete = async () => {
        if (!queue?.currentTokenId) return;

        setActionLoading(true);
        setError("");
        try {
            await completeConsultation(queue.currentTokenId);
            const res = await getDoctorQueue();
            setQueue(res.data);
        } catch (err) {
            setError(
                err?.response?.data?.detail || err?.response?.data?.message || "Unable to complete consultation."
            );
        } finally {
            setActionLoading(false);
        }
    };

    const handleExtend = async () => {
        if (!queue?.currentTokenId) return;

        try {
            setActionLoading(true);
            setError("");
            await extendConsultation(queue.currentTokenId);
            const res = await getDoctorQueue();
            setQueue(res.data);
        } catch (err) {
            setError(
                err?.response?.data?.detail || err?.response?.data?.message || "Unable to extend consultation."
            );
        } finally {
            setActionLoading(false);
        }
    };

    const handleAvailabilityToggle = async () => {
        try {
            setActionLoading(true);
            setError("");
            const nextValue = !available;
            await setDoctorAvailability(nextValue);
            setAvailable(nextValue);
        } catch (err) {
            setError(
                err?.response?.data?.detail || err?.response?.data?.message || "Unable to update availability."
            );
        } finally {
            setActionLoading(false);
        }
    };

    const hasCurrentToken = Boolean(queue?.currentTokenId);
    const hasWaitingPatients = (queue?.nextTokens?.length || 0) > 0;
    const queueCount = queue?.nextTokens?.length || 0;
    const doctorName = queue?.doctorName || user?.doctorName || user?.name || "Doctor";
    const roomNumber = queue?.roomNumber || "Room";
    const loadStatus = queueCount > 10 ? "High Load" : "Normal Load";
    const unavailable = queue?.doctorAvailable === false || !available;
    const currentHighPriority = (queue?.currentSeverityScore || 0) > 7;

    if (!doctorId) {
        return (
            <div style={page}>
                <div style={errorBanner}>Doctor mapping is not configured for this user.</div>
            </div>
        );
    }

    return (
        <div style={page}>
            <div style={header}>
                <div>
                    <p style={eyebrow}>Consultation Room</p>
                    <h1 style={title}>{doctorName}</h1>
                    <p style={subtitle}>
                        {roomNumber} · {available ? "Available for consultation" : "Marked unavailable"}
                    </p>
                </div>

                <div style={summaryGrid}>
                    <div style={summaryCard}>
                        <span style={summaryLabel}>Waiting patients</span>
                        <strong style={summaryValue}>{loading ? "..." : queueCount}</strong>
                    </div>
                    <div style={summaryCard}>
                        <span style={summaryLabel}>Estimated wait</span>
                        <strong style={summaryValue}>
                            {loading ? "..." : `${queue?.estimatedWaitMinutes || 0} min`}
                        </strong>
                    </div>
                    <div style={summaryCard}>
                        <span style={summaryLabel}>Queue Load</span>
                        <strong style={summaryValue}>{loading ? "..." : loadStatus}</strong>
                    </div>
                    <div style={summaryCard}>
                        <span style={summaryLabel}>Availability</span>
                        <button
                            style={availabilityButton(available)}
                            disabled={actionLoading || loading}
                            onClick={handleAvailabilityToggle}
                        >
                            {available ? "Set Unavailable" : "Set Available"}
                        </button>
                    </div>
                </div>
            </div>

            {error ? (
                <div style={errorBanner}>{error}</div>
            ) : null}

            <div style={contentGrid}>
                <div style={currentCard}>
                    <div style={cardHeader}>
                        <div>
                            <p style={cardEyebrow}>Current Token</p>
                            <h2 style={cardTitle}>Now Calling</h2>
                        </div>
                        <div style={statusBadge(hasCurrentToken)}>
                            {hasCurrentToken ? "Active" : "Idle"}
                        </div>
                    </div>

                    <div style={tokenHero}>
                        {loading ? "Loading..." : (queue?.currentToken || "—")}
                    </div>

                    <p style={patientName}>
                        {queue?.currentPatientName || "No patient assigned"}
                    </p>

                    {queue?.currentVisitType ? (
                        <div style={metaRow}>
                            <span style={typeBadge}>{getVisitTypeLabel(queue.currentVisitType)}</span>
                            {currentHighPriority ? <span style={priorityPill}>High Priority</span> : null}
                        </div>
                    ) : null}

                    {remainingSeconds != null && hasCurrentToken ? (
                        <div style={timerCard}>
                            <div style={timerLabel}>Auto-completing based on avg time</div>
                            <div style={timerValue}>{formatDuration(remainingSeconds)}</div>
                        </div>
                    ) : null}

                    <div style={actionRow}>
                        <button
                            style={primaryButton}
                            onClick={handleCallNext}
                            disabled={unavailable || hasCurrentToken || !hasWaitingPatients || actionLoading || loading}
                        >
                            {actionLoading ? "Updating..." : "Call Next"}
                        </button>
                        <button
                            style={secondaryButton}
                            onClick={handleComplete}
                            disabled={!hasCurrentToken || actionLoading || loading}
                        >
                            Complete Now
                        </button>
                        <button
                            style={secondaryButton}
                            onClick={handleExtend}
                            disabled={!hasCurrentToken || actionLoading || loading}
                        >
                            Extend Consultation (+5 min)
                        </button>
                        {hasCurrentToken && (
                            <button
                                style={reportButton}
                                disabled={reportLoading}
                                onClick={async () => {
                                    setReportLoading(true);
                                    try { await downloadVisitReport(queue.currentTokenId); }
                                    catch { setError("PDF generation failed."); }
                                    finally { setReportLoading(false); }
                                }}
                            >
                                {reportLoading ? "Generating..." : "📄 Report"}
                            </button>
                        )}
                    </div>
                </div>

                <div style={queueCard}>
                    <div style={cardHeader}>
                        <div>
                            <p style={cardEyebrow}>Waiting Queue</p>
                            <h2 style={cardTitle}>Next Patients</h2>
                        </div>
                        <div style={queueCounter}>
                            {queueCount} waiting
                        </div>
                    </div>

                    {queue?.nextTokens?.length > 0 ? (
                        queue.nextTokens.map((t, i) => (
                            <div key={i} style={queueItem(i === 0)}>
                                <div style={queueItemLeft}>
                                    <div style={queuePriority(i === 0)}>
                                        {i === 0 ? "Highest Priority" : `Queue #${i + 1}`}
                                    </div>
                                    <strong style={queueToken}>{t.tokenNumber}</strong>
                                    <span style={queuePatient}>{t.patientName}</span>
                                </div>
                                <div style={queueItemRight}>
                                    <span style={typeBadge}>{getVisitTypeLabel(t.visitType)}</span>
                                    {(t.severityScore || 0) > 7 ? <span style={priorityPill}>High Priority</span> : null}
                                    <span style={waitLabel}>{t.estimatedWaitMinutes} min</span>
                                </div>
                            </div>
                        ))
                    ) : (
                        <div style={emptyState}>No patients waiting.</div>
                    )}
                </div>
            </div>
        </div>
    );
}

const page = {
    minHeight: "100vh",
    background: "linear-gradient(180deg, #f5fbfc 0%, #eef4f7 100%)",
    padding: "32px",
    fontFamily: "Arial",
    color: "#16313a"
};

const header = {
    display: "flex",
    justifyContent: "space-between",
    gap: "20px",
    alignItems: "flex-start",
    marginBottom: "24px",
    flexWrap: "wrap"
};

const eyebrow = {
    margin: 0,
    fontSize: "12px",
    letterSpacing: "0.12em",
    textTransform: "uppercase",
    color: "#557b88"
};

const title = {
    margin: "6px 0 8px",
    fontSize: "34px"
};

const subtitle = {
    margin: 0,
    fontSize: "16px",
    color: "#557b88"
};

const summaryGrid = {
    display: "grid",
    gridTemplateColumns: "repeat(2, minmax(160px, 1fr))",
    gap: "12px",
    width: "100%",
    maxWidth: "420px"
};

const summaryCard = {
    background: "#ffffff",
    border: "1px solid #d7e6ea",
    borderRadius: "18px",
    padding: "18px 20px",
    boxShadow: "0 10px 24px rgba(22, 49, 58, 0.06)"
};

const summaryLabel = {
    display: "block",
    fontSize: "13px",
    color: "#557b88",
    marginBottom: "8px"
};

const summaryValue = {
    fontSize: "28px",
    color: "#0f4f5c"
};

const errorBanner = {
    marginBottom: "20px",
    padding: "14px 16px",
    borderRadius: "14px",
    background: "#fff1f2",
    border: "1px solid #fecdd3",
    color: "#b42318"
};

const contentGrid = {
    display: "grid",
    gridTemplateColumns: "minmax(320px, 1.1fr) minmax(340px, 1fr)",
    gap: "20px"
};

const sharedCard = {
    background: "#ffffff",
    border: "1px solid #d7e6ea",
    borderRadius: "24px",
    padding: "24px",
    boxShadow: "0 14px 34px rgba(22, 49, 58, 0.08)"
};

const currentCard = {
    ...sharedCard
};

const queueCard = {
    ...sharedCard
};

const cardHeader = {
    display: "flex",
    justifyContent: "space-between",
    gap: "12px",
    alignItems: "flex-start",
    marginBottom: "20px"
};

const cardEyebrow = {
    margin: 0,
    fontSize: "12px",
    letterSpacing: "0.12em",
    textTransform: "uppercase",
    color: "#557b88"
};

const cardTitle = {
    margin: "6px 0 0",
    fontSize: "24px"
};

const statusBadge = (active) => ({
    padding: "8px 12px",
    borderRadius: "999px",
    fontSize: "13px",
    fontWeight: "700",
    background: active ? "#dff7f2" : "#edf2f4",
    color: active ? "#0f766e" : "#52606d"
});

const tokenHero = {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    minHeight: "170px",
    marginBottom: "16px",
    borderRadius: "22px",
    background: "linear-gradient(135deg, #0f766e 0%, #1d9bb2 100%)",
    color: "#ffffff",
    fontSize: "72px",
    fontWeight: "700",
    letterSpacing: "0.04em",
    boxShadow: "inset 0 1px 0 rgba(255,255,255,0.18)"
};

const patientName = {
    margin: 0,
    fontSize: "20px",
    fontWeight: "600",
    color: "#16313a"
};

const metaRow = {
    display: "flex",
    gap: "10px",
    marginTop: "14px",
    flexWrap: "wrap"
};

const timerCard = {
    marginTop: "18px",
    padding: "16px 18px",
    borderRadius: "18px",
    background: "#f4fbfd",
    border: "1px solid #d4eaef"
};

const timerLabel = {
    fontSize: "13px",
    color: "#557b88",
    marginBottom: "6px"
};

const timerValue = {
    fontSize: "28px",
    fontWeight: "700",
    color: "#0f4f5c"
};

const actionRow = {
    display: "flex",
    gap: "12px",
    marginTop: "24px",
    flexWrap: "wrap"
};

const buttonBase = {
    border: "none",
    borderRadius: "14px",
    padding: "12px 18px",
    fontSize: "15px",
    fontWeight: "700",
    cursor: "pointer"
};

const primaryButton = {
    ...buttonBase,
    background: "#0f766e",
    color: "#ffffff"
};

const secondaryButton = {
    ...buttonBase,
    background: "#e8f3f6",
    color: "#174654"
};

const reportButton = {
    ...buttonBase,
    background: "#f0faf8",
    color: "#0f766e",
    border: "1.5px solid #0f766e"
};

const availabilityButton = (available) => ({
    border: "none",
    borderRadius: "12px",
    padding: "10px 12px",
    fontSize: "14px",
    fontWeight: "700",
    cursor: "pointer",
    background: available ? "#fff1f2" : "#e8f7f4",
    color: available ? "#b42318" : "#0f766e"
});

const queueCounter = {
    fontSize: "14px",
    fontWeight: "700",
    color: "#0f766e",
    background: "#e2f3f5",
    padding: "8px 12px",
    borderRadius: "999px"
};

const queueItem = (highlighted) => ({
    display: "flex",
    justifyContent: "space-between",
    gap: "14px",
    alignItems: "center",
    padding: "16px 18px",
    borderRadius: "18px",
    border: highlighted ? "1px solid #81d4cf" : "1px solid #e3edf0",
    background: highlighted ? "#f0fbfa" : "#f8fbfc",
    marginBottom: "12px"
});

const queueItemLeft = {
    display: "flex",
    flexDirection: "column",
    gap: "4px"
};

const queueItemRight = {
    display: "flex",
    flexDirection: "column",
    alignItems: "flex-end",
    gap: "8px"
};

const queuePriority = (highlighted) => ({
    fontSize: "12px",
    fontWeight: "700",
    color: highlighted ? "#0f766e" : "#557b88",
    letterSpacing: "0.08em",
    textTransform: "uppercase"
});

const queueToken = {
    fontSize: "22px",
    color: "#16313a"
};

const queuePatient = {
    fontSize: "15px",
    color: "#557b88"
};

const typeBadge = {
    fontSize: "13px",
    fontWeight: "700",
    color: "#0f4f5c",
    background: "#e5f2f6",
    padding: "7px 10px",
    borderRadius: "999px"
};

const priorityPill = {
    fontSize: "13px",
    fontWeight: "700",
    color: "#b54708",
    background: "#fff4e5",
    padding: "7px 10px",
    borderRadius: "999px"
};

const waitLabel = {
    fontSize: "14px",
    color: "#557b88"
};

const emptyState = {
    padding: "28px 20px",
    borderRadius: "18px",
    background: "#f8fbfc",
    border: "1px dashed #c8d9df",
    color: "#557b88",
    textAlign: "center"
};

function formatDuration(totalSeconds) {
    const safe = Math.max(0, totalSeconds || 0);
    const minutes = Math.floor(safe / 60);
    const seconds = safe % 60;
    return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}
