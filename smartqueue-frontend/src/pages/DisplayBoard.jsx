import { useEffect, useState } from "react";
import { getAllOfficesDisplay } from "../services/api";

export default function DisplayBoard() {
    const [offices, setOffices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [lastUpdated, setLastUpdated] = useState(null);

    const fetchAll = async (showLoader = false) => {
        try {
            if (showLoader) setLoading(true);
            const res = await getAllOfficesDisplay();
            const data = res.data || [];
            // Filter out offices with no doctors
            setOffices(data.filter(o => o.doctors && o.doctors.length > 0));
            setError("");
            setLastUpdated(new Date());
        } catch (err) {
            setError("Unable to load display board.");
        } finally {
            if (showLoader) setLoading(false);
        }
    };

    useEffect(() => {
        fetchAll(true);
        const interval = setInterval(() => fetchAll(false), 5000);
        return () => clearInterval(interval);
    }, []);

    return (
        <div style={page}>
            <style>{`
                @keyframes pulse {
                    0% { transform: scale(1); box-shadow: 0 0 0 rgba(20,184,166,0.35); }
                    50% { transform: scale(1.015); box-shadow: 0 0 32px rgba(20,184,166,0.5); }
                    100% { transform: scale(1); box-shadow: 0 0 0 rgba(20,184,166,0.35); }
                }
                @keyframes fadeIn {
                    from { opacity: 0; transform: translateY(8px); }
                    to { opacity: 1; transform: translateY(0); }
                }
            `}</style>

            <div style={header}>
                <div>
                    <p style={eyebrow}>SmartQueue · Live Display</p>
                    <h1 style={title}>Now Serving</h1>
                </div>
                <div style={headerRight}>
                    <div style={officeCountBadge}>
                        {offices.length} Office{offices.length !== 1 ? "s" : ""} Active
                    </div>
                    {lastUpdated && (
                        <div style={updatedBadge}>
                            Updated {lastUpdated.toLocaleTimeString()}
                        </div>
                    )}
                </div>
            </div>

            {error ? (
                <p style={errorText}>{error}</p>
            ) : null}

            {loading && offices.length === 0 ? (
                <p style={loadingText}>Loading display board...</p>
            ) : null}

            {!loading && offices.length === 0 && !error ? (
                <div style={emptyState}>No clinic offices are currently active.</div>
            ) : null}

            {offices.map((office, idx) => (
                <div key={office.officeId ?? idx} style={officeSection}>
                    <div style={officeSectionHeader}>
                        <span style={officeLabel}>
                            {office.officeName || `Office ${office.officeId ?? idx + 1}`}
                        </span>
                        <span style={officeDoctorCount}>
                            {office.doctors?.length || 0} doctor{(office.doctors?.length || 0) !== 1 ? "s" : ""}
                        </span>
                    </div>

                    {(!office.doctors || office.doctors.length === 0) ? (
                        <div style={officeEmpty}>No active doctors in this office.</div>
                    ) : (
                        <div style={grid}>
                            {office.doctors.map(doc => (
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
                                            {(doc.nextTokens || []).length > 0
                                                ? doc.nextTokens.map(t => (
                                                    <span key={t} style={nextTokenChip}>{t}</span>
                                                ))
                                                : <span style={nextTokenEmpty}>No waiting tokens</span>
                                            }
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
}

// ─── Styles ──────────────────────────────────────────────────────────
const page = {
    minHeight: "100vh",
    background: "linear-gradient(180deg, #0d1f2a 0%, #0a1820 100%)",
    color: "#ffffff",
    padding: "36px 40px",
    fontFamily: "Arial",
};

const header = {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "flex-end",
    gap: "20px",
    marginBottom: "36px",
    flexWrap: "wrap",
};

const eyebrow = {
    margin: 0,
    fontSize: "14px",
    letterSpacing: "0.18em",
    textTransform: "uppercase",
    color: "#8bcad3",
};

const title = {
    margin: "8px 0 0",
    fontSize: "52px",
    lineHeight: 1,
};

const headerRight = {
    display: "flex",
    gap: "10px",
    alignItems: "center",
    flexWrap: "wrap",
};

const officeCountBadge = {
    padding: "10px 16px",
    borderRadius: "999px",
    background: "rgba(139,202,211,0.14)",
    border: "1px solid rgba(139,202,211,0.24)",
    fontSize: "18px",
    fontWeight: "700",
    color: "#d5f6fb",
};

const updatedBadge = {
    padding: "8px 14px",
    borderRadius: "999px",
    background: "rgba(255,255,255,0.05)",
    border: "1px solid rgba(255,255,255,0.1)",
    fontSize: "14px",
    color: "#8bcad3",
};

const errorText = {
    textAlign: "center",
    color: "#ffb4ab",
    marginBottom: "20px",
    fontSize: "20px",
};

const loadingText = {
    textAlign: "center",
    fontSize: "22px",
    color: "#8bcad3",
};

const emptyState = {
    minHeight: "200px",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: "28px",
    border: "1px dashed rgba(213,246,251,0.3)",
    color: "#d5f6fb",
    fontSize: "24px",
    background: "rgba(255,255,255,0.04)",
};

const officeSection = {
    marginBottom: "48px",
    animation: "fadeIn 0.4s ease-out both",
};

const officeSectionHeader = {
    display: "flex",
    alignItems: "center",
    gap: "14px",
    marginBottom: "20px",
    paddingBottom: "14px",
    borderBottom: "1px solid rgba(139,202,211,0.2)",
};

const officeLabel = {
    fontSize: "28px",
    fontWeight: "700",
    color: "#d5f6fb",
    letterSpacing: "0.04em",
};

const officeDoctorCount = {
    fontSize: "15px",
    color: "#8bcad3",
    padding: "6px 12px",
    borderRadius: "999px",
    background: "rgba(139,202,211,0.1)",
    border: "1px solid rgba(139,202,211,0.18)",
};

const officeEmpty = {
    padding: "28px",
    borderRadius: "20px",
    border: "1px dashed rgba(213,246,251,0.2)",
    color: "#8bcad3",
    fontSize: "18px",
    textAlign: "center",
};

const grid = {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
    gap: "22px",
};

const doctorCard = (active) => ({
    background: active ? "rgba(20,184,166,0.12)" : "rgba(255,255,255,0.06)",
    border: active ? "1px solid rgba(139,202,211,0.36)" : "1px solid rgba(255,255,255,0.08)",
    borderRadius: "28px",
    padding: "26px",
    boxShadow: active ? "0 20px 46px rgba(14,165,233,0.18)" : "0 18px 40px rgba(0,0,0,0.24)",
    transition: "all 0.3s ease",
});

const doctorHeader = {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: "12px",
    marginBottom: "20px",
};

const doctorName = {
    margin: 0,
    fontSize: "28px",
};

const waitBadge = {
    fontSize: "16px",
    fontWeight: "700",
    background: "rgba(139,202,211,0.14)",
    color: "#d5f6fb",
    padding: "8px 12px",
    borderRadius: "999px",
    whiteSpace: "nowrap",
};

const tokenCard = (active) => ({
    minHeight: "200px",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: "22px",
    background: "linear-gradient(135deg, #14b8a6 0%, #0ea5e9 100%)",
    color: "#ffffff",
    fontSize: "78px",
    fontWeight: "700",
    letterSpacing: "0.08em",
    marginBottom: "16px",
    animation: active ? "pulse 2.2s ease-in-out infinite" : "none",
});

const tokenLabel = {
    margin: 0,
    fontSize: "20px",
    color: "#d1e9ee",
    textAlign: "center",
};

const upNextSection = { marginTop: "18px" };

const upNextLabel = {
    fontSize: "15px",
    fontWeight: "700",
    color: "#d1e9ee",
    marginBottom: "10px",
};

const nextTokenRow = {
    display: "flex",
    gap: "8px",
    flexWrap: "wrap",
};

const nextTokenChip = {
    padding: "8px 12px",
    borderRadius: "999px",
    background: "rgba(255,255,255,0.09)",
    border: "1px solid rgba(255,255,255,0.12)",
    fontSize: "16px",
    fontWeight: "700",
    color: "#ffffff",
};

const nextTokenEmpty = {
    fontSize: "15px",
    color: "#b9d6db",
};
