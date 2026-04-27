import { useEffect, useState, useContext } from "react";
import {
    bookAppointment,
    checkInWalkIn,
    getActiveTokens,
    markNoShow,
    getReinstatableTokens,
    reinstateNoShow,
    reassignDoctor,
    getReceptionOverview,
    getDoctors,
    getWaitingTokens,
    downloadVisitReport
} from "../services/api";

import { connectSocket, disconnectSocket } from "../websocket/socket";
import { SERVICE_TYPE_OPTIONS, SPECIALIZATION_OPTIONS } from "../utils/medicalLabels";
import QRModal from "../components/QRModal";
import { AuthContext } from "../auth/AuthContext";

export default function ReceptionPanel() {
    const { user } = useContext(AuthContext);
    // officeId is derived from the logged-in user's profile — never typed manually
    const myOfficeId = user?.officeId ?? 1;

    const [overview, setOverview] = useState(null);
    const [doctors, setDoctors] = useState([]);
    const [loading, setLoading] = useState(true);
    const [actionLoading, setActionLoading] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");

    const [form, setForm] = useState({
        patientName: "",
        age: "",
        phone: "",
        serviceType: "",
        suggestedSpecialization: "",
        severityScore: 0
    });
    const [reportLoading, setReportLoading] = useState(false);
    const [appointmentForm, setAppointmentForm] = useState({
        patientName: "",
        age: "",
        serviceType: "",
        severityScore: 0,
        appointmentTime: ""
    });

    const [noShowTokenId, setNoShowTokenId] = useState("");
    const [reassignTokenId, setReassignTokenId] = useState("");
    const [reinstateTokenId, setReinstateTokenId] = useState("");
    const [reassignDoctorId, setReassignDoctorId] = useState("");
    const [waitingTokens, setWaitingTokens] = useState([]);
    const [activeTokens, setActiveTokens] = useState([]);
    const [reinstatableTokens, setReinstatableTokens] = useState([]);
    const [validationErrors, setValidationErrors] = useState({});
    const [lastToken, setLastToken] = useState(null);
    const [showQR, setShowQR] = useState(false);

    useEffect(() => {
        let active = true;

        const refreshOverview = async (showLoader = false) => {
            try {
                if (showLoader) {
                    setLoading(true);
                }

                const [overviewRes, doctorsRes, waitingRes, activeRes, reinstatableRes] = await Promise.all([
                    getReceptionOverview(),
                    getDoctors(),
                    getWaitingTokens(),
                    getActiveTokens(),
                    getReinstatableTokens()
                ]);

                if (active) {
                    setOverview(overviewRes.data);
                    setDoctors(doctorsRes.data);
                    setWaitingTokens(waitingRes.data || []);
                    setActiveTokens(activeRes.data || []);
                    setReinstatableTokens(reinstatableRes.data || []);
                    setError("");
                }
            } catch (err) {
                if (active) {
                    setError(
                        err?.response?.data?.detail || err?.response?.data?.message || "Unable to load reception dashboard."
                    );
                }
            } finally {
                if (active && showLoader) {
                    setLoading(false);
                }
            }
        };

        refreshOverview(true);

        connectSocket("reception", (data) => {
            if (!active) {
                return;
            }
            setOverview(data);
            setError("");
        }, myOfficeId);

        const interval = setInterval(() => {
            refreshOverview(false);
        }, 3000);

        return () => {
            active = false;
            disconnectSocket();
            clearInterval(interval);
        };
    }, [myOfficeId]);

    const refreshOverview = async () => {
        try {
            const [overviewRes, doctorsRes, waitingRes, activeRes, reinstatableRes] = await Promise.all([
                getReceptionOverview(),
                getDoctors(),
                getWaitingTokens(),
                getActiveTokens(),
                getReinstatableTokens()
            ]);
            setOverview(overviewRes.data);
            setDoctors(doctorsRes.data);
            setWaitingTokens(waitingRes.data || []);
            setActiveTokens(activeRes.data || []);
            setReinstatableTokens(reinstatableRes.data || []);
        } catch (err) {
            // Non-critical refresh failure — show inline error, do NOT propagate
            // (avoids bubbling to the 401 interceptor for non-auth errors)
            if (err?.response?.status !== 401) {
                setError(err?.response?.data?.detail || err?.response?.data?.message || "Unable to refresh dashboard.");
            }
        }
    };

    const handleCheckIn = async () => {
        const nextErrors = {};

        if (!form.patientName.trim()) {
            nextErrors.patientName = "Patient name is required.";
        }

        if (form.age !== "" && Number(form.age) <= 0) {
            nextErrors.age = "Age must be greater than 0.";
        }

        if (!form.serviceType) {
            nextErrors.serviceType = "Select a consultation type.";
        }

        if (Number(form.severityScore) < 0 || Number(form.severityScore) > 10) {
            nextErrors.severityScore = "Severity must be between 0 and 10.";
        }

        setValidationErrors(nextErrors);

        if (Object.keys(nextErrors).length > 0) {
            setError("Please correct the highlighted fields.");
            setMessage("");
            return;
        }

        try {
            setActionLoading(true);
            setError("");
            setMessage("");
            await checkInWalkIn({
                patientName: form.patientName,
                age: form.age === "" ? null : Number(form.age),
                phone: form.phone.trim() || null,
                serviceType: form.serviceType,
                suggestedSpecialization: form.suggestedSpecialization || null,
                severityScore: Number(form.severityScore) || 0,
                officeId: myOfficeId,
                idempotencyKey: crypto.randomUUID ? crypto.randomUUID() : Date.now().toString()
            }).then((res) => setLastToken(res.data));
            await refreshOverview();
            setMessage("Patient checked in.");
            setShowQR(true);
            setForm({
                patientName: "",
                age: "",
                phone: "",
                serviceType: "",
                suggestedSpecialization: "",
                severityScore: 0
            });
            setValidationErrors({});
        } catch (e) {
            setError(e?.response?.data?.detail || e?.response?.data?.message || "Unable to check in patient.");
        } finally {
            setActionLoading(false);
        }
    };

    const handleNoShow = async () => {
        try {
            setActionLoading(true);
            setError("");
            setMessage("");
            await markNoShow(Number(noShowTokenId));
            await refreshOverview();
            setMessage("Marked as no-show.");
            setNoShowTokenId("");
            setReassignDoctorId("");
        } catch (e) {
            setError(e?.response?.data?.detail || e?.response?.data?.message || "Unable to mark no-show.");
        } finally {
            setActionLoading(false);
        }
    };

    const handleAppointment = async () => {
        const nextErrors = {};

        if (!appointmentForm.patientName.trim()) {
            nextErrors.appointmentPatientName = "Patient name is required.";
        }
        if (!appointmentForm.serviceType) {
            nextErrors.appointmentServiceType = "Select a consultation type.";
        }
        if (!appointmentForm.appointmentTime) {
            nextErrors.appointmentTime = "Appointment time is required.";
        }

        setValidationErrors((current) => ({ ...current, ...nextErrors }));

        if (Object.keys(nextErrors).length > 0) {
            setError("Please correct the highlighted appointment fields.");
            setMessage("");
            return;
        }

        try {
            setActionLoading(true);
            setError("");
            setMessage("");
            const res = await bookAppointment({
                patientName: appointmentForm.patientName,
                age: appointmentForm.age === "" ? null : Number(appointmentForm.age),
                serviceType: appointmentForm.serviceType,
                severityScore: Number(appointmentForm.severityScore) || 0,
                officeId: myOfficeId,
                appointmentTime: new Date(appointmentForm.appointmentTime).toISOString()
            });
            setLastToken(res.data);
            setShowQR(true);
            await refreshOverview();
            setMessage("Appointment booked.");
            setAppointmentForm({
                patientName: "",
                age: "",
                serviceType: "",
                severityScore: 0,
                appointmentTime: ""
            });
        } catch (e) {
            setError(e?.response?.data?.detail || e?.response?.data?.message || "Unable to book appointment.");
        } finally {
            setActionLoading(false);
        }
    };

    const handleReinstate = async () => {
        try {
            setActionLoading(true);
            setError("");
            setMessage("");
            await reinstateNoShow(Number(reinstateTokenId), "Patient returned");
            await refreshOverview();
            setMessage("Reinstated.");
            setReinstateTokenId("");
            setReassignDoctorId("");
        } catch (e) {
            setError(e?.response?.data?.detail || e?.response?.data?.message || "Unable to reinstate token.");
        } finally {
            setActionLoading(false);
        }
    };

    const handleReassign = async () => {
        try {
            setActionLoading(true);
            setError("");
            setMessage("");
            await reassignDoctor(Number(reassignTokenId), Number(reassignDoctorId));
            await refreshOverview();
            setMessage("Reassigned.");
            setReassignTokenId("");
            setReassignDoctorId("");
        } catch (e) {
            setError(e?.response?.data?.detail || e?.response?.data?.message || "Unable to reassign doctor.");
        } finally {
            setActionLoading(false);
        }
    };

    const isCheckInDisabled = actionLoading
        || !form.patientName.trim()
        || !form.serviceType;

    const isNoShowDisabled = actionLoading || !noShowTokenId;
    const isReassignDisabled = actionLoading || !reassignTokenId || !reassignDoctorId;
    const isReinstateDisabled = actionLoading || !reinstateTokenId;

    return (
        <div style={page}>
            <div style={header}>
                <div>
                    <p style={eyebrow}>Front Desk</p>
                    <h1 style={title}>Reception Panel</h1>
                    <p style={subtitle}>
                        Register patients, manage token actions, and monitor live clinic flow.
                    </p>
                </div>
                <div style={statRow}>
                    <div style={statCard}>
                        <span style={statLabel}>Active doctors</span>
                        <strong style={statValue}>{loading ? "..." : (overview?.totalDoctorsActive || 0)}</strong>
                    </div>
                    <div style={statCard}>
                        <span style={statLabel}>Patients waiting</span>
                        <strong style={statValue}>{loading ? "..." : (overview?.totalPatientsWaiting || 0)}</strong>
                    </div>
                </div>
            </div>

            {error ? (
                <div style={errorBanner}>{error}</div>
            ) : null}

            {message ? (
                <div style={successBanner}>{message}</div>
            ) : null}

            {lastToken ? (
                <div style={successBanner}>
                    Token: <strong>{lastToken.tokenNumber}</strong> · Dr. {lastToken.doctorName} · Position #{lastToken.positionInQueue} · ~{lastToken.estimatedWaitMinutes} min
                    <button
                        style={{ marginLeft: 12, padding: '4px 14px', background: '#6366f1', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 700, fontSize: 13 }}
                        onClick={() => setShowQR(true)}
                    >
                        📱 Show QR
                    </button>
                    <button
                        style={{ marginLeft: 8, padding: '4px 14px', background: '#0f766e', color: '#fff', border: 'none', borderRadius: 8, cursor: reportLoading ? 'not-allowed' : 'pointer', fontWeight: 700, fontSize: 13, opacity: reportLoading ? 0.7 : 1 }}
                        disabled={reportLoading}
                        onClick={async () => {
                            setReportLoading(true);
                            try { await downloadVisitReport(lastToken.id); }
                            catch { setError("PDF generation failed. Try again."); }
                            finally { setReportLoading(false); }
                        }}
                    >
                        {reportLoading ? "Generating..." : "📄 Download Report"}
                    </button>
                </div>
            ) : null}

            {showQR && lastToken && (
                <QRModal token={lastToken} onClose={() => setShowQR(false)} />
            )}

            <div style={layout}>
                <div style={leftColumn}>
                    <div style={card}>
                        <div style={sectionHeader}>
                            <div>
                                <p style={sectionEyebrow}>Patient Intake</p>
                                <h3 style={sectionTitle}>Walk-in Check-in</h3>
                            </div>
                        </div>

                        <div style={formGrid}>
                            <div style={field}>
                                <label style={label}>Patient Name</label>
                                <input
                                    style={input(validationErrors.patientName)}
                                    placeholder="Enter patient name"
                                    value={form.patientName}
                                    onChange={e => setForm({ ...form, patientName: e.target.value })}
                                />
                                {validationErrors.patientName ? (
                                    <span style={fieldError}>{validationErrors.patientName}</span>
                                ) : null}
                            </div>

                            <div style={field}>
                                <label style={label}>Age</label>
                                <input
                                    style={input(validationErrors.age)}
                                    type="number"
                                    placeholder="Optional"
                                    value={form.age}
                                    onChange={e => setForm({ ...form, age: e.target.value })}
                                />
                                {validationErrors.age ? (
                                    <span style={fieldError}>{validationErrors.age}</span>
                                ) : null}
                            </div>

                            <div style={field}>
                                <label style={label}>Mobile Number <span style={optionalTag}>(optional — for family ID)</span></label>
                                <input
                                    style={input(false)}
                                    type="tel"
                                    placeholder="e.g. 9876543210"
                                    value={form.phone}
                                    onChange={e => setForm({ ...form, phone: e.target.value })}
                                />
                            </div>

                            <div style={field}>
                                <label style={label}>Consultation Type</label>
                                <select
                                    style={input(validationErrors.serviceType)}
                                    value={form.serviceType}
                                    onChange={e => setForm({ ...form, serviceType: e.target.value })}
                                >
                                    <option value="">Select consultation type</option>
                                    {SERVICE_TYPE_OPTIONS.map((option) => (
                                        <option key={option.value} value={option.value}>
                                            {option.label}
                                        </option>
                                    ))}
                                </select>
                                {validationErrors.serviceType ? (
                                    <span style={fieldError}>{validationErrors.serviceType}</span>
                                ) : null}
                            </div>

                            <div style={field}>
                                <label style={label}>Department (Specialization)</label>
                                <select
                                    style={input(false)}
                                    value={form.suggestedSpecialization}
                                    onChange={e => setForm({ ...form, suggestedSpecialization: e.target.value })}
                                >
                                    <option value="">Auto-assign / General</option>
                                    {SPECIALIZATION_OPTIONS.map((option) => (
                                        <option key={option.value} value={option.value}>
                                            {option.label}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div style={fieldFull}>
                                <label style={label}>Priority Score</label>
                                <div style={severityWrap}>
                                    <input
                                        style={severityInput(validationErrors.severityScore)}
                                        type="range"
                                        min="0"
                                        max="10"
                                        value={form.severityScore}
                                        onChange={e => setForm({ ...form, severityScore: e.target.value })}
                                    />
                                    <div style={severityBadge(Number(form.severityScore))}>
                                        {Number(form.severityScore) >= 8 ? "Urgent" : "Standard"} · {form.severityScore}/10
                                    </div>
                                </div>
                                {validationErrors.severityScore ? (
                                    <span style={fieldError}>{validationErrors.severityScore}</span>
                                ) : null}
                            </div>
                        </div>

                        <button style={primaryButton} onClick={handleCheckIn} disabled={isCheckInDisabled}>
                            {actionLoading ? "Saving..." : "Check In Patient"}
                        </button>
                    </div>

                    <div style={card}>
                        <div style={sectionHeader}>
                            <div>
                                <p style={sectionEyebrow}>Scheduled Visits</p>
                                <h3 style={sectionTitle}>Book Appointment</h3>
                            </div>
                        </div>

                        <div style={formGrid}>
                            <div style={field}>
                                <label style={label}>Patient Name</label>
                                <input
                                    style={input(validationErrors.appointmentPatientName)}
                                    placeholder="Enter patient name"
                                    value={appointmentForm.patientName}
                                    onChange={e => setAppointmentForm({ ...appointmentForm, patientName: e.target.value })}
                                />
                                {validationErrors.appointmentPatientName ? <span style={fieldError}>{validationErrors.appointmentPatientName}</span> : null}
                            </div>

                            <div style={field}>
                                <label style={label}>Age</label>
                                <input
                                    style={input(false)}
                                    type="number"
                                    placeholder="Optional"
                                    value={appointmentForm.age}
                                    onChange={e => setAppointmentForm({ ...appointmentForm, age: e.target.value })}
                                />
                            </div>

                            <div style={field}>
                                <label style={label}>Consultation Type</label>
                                <select
                                    style={input(validationErrors.appointmentServiceType)}
                                    value={appointmentForm.serviceType}
                                    onChange={e => setAppointmentForm({ ...appointmentForm, serviceType: e.target.value })}
                                >
                                    <option value="">Select consultation type</option>
                                    {SERVICE_TYPE_OPTIONS.map((option) => (
                                        <option key={option.value} value={option.value}>
                                            {option.label}
                                        </option>
                                    ))}
                                </select>
                                {validationErrors.appointmentServiceType ? <span style={fieldError}>{validationErrors.appointmentServiceType}</span> : null}
                            </div>

                            <div style={field}>
                                <label style={label}>Appointment Time</label>
                                <input
                                    style={input(validationErrors.appointmentTime)}
                                    type="datetime-local"
                                    value={appointmentForm.appointmentTime}
                                    onChange={e => setAppointmentForm({ ...appointmentForm, appointmentTime: e.target.value })}
                                />
                                {validationErrors.appointmentTime ? <span style={fieldError}>{validationErrors.appointmentTime}</span> : null}
                            </div>

                            <div style={field}>
                                <label style={label}>Priority Score</label>
                                <input
                                    style={input(false)}
                                    type="number"
                                    min="0"
                                    max="10"
                                    value={appointmentForm.severityScore}
                                    onChange={e => setAppointmentForm({ ...appointmentForm, severityScore: e.target.value })}
                                />
                            </div>
                        </div>

                        <button style={primaryButton} onClick={handleAppointment} disabled={actionLoading}>
                            {actionLoading ? "Saving..." : "Book Appointment"}
                        </button>
                    </div>

                    <div style={card}>
                        <div style={sectionHeader}>
                            <div>
                                <p style={sectionEyebrow}>Token Actions</p>
                                <h3 style={sectionTitle}>Update Existing Token</h3>
                            </div>
                        </div>

                        <div style={field}>
                            <label style={label}>Waiting Token</label>
                            <select
                                style={input(false)}
                                value={noShowTokenId}
                                onChange={e => setNoShowTokenId(e.target.value)}
                            >
                                <option value="">Select waiting token</option>
                                {waitingTokens.map((token) => (
                                    <option key={token.id} value={token.id}>
                                        {token.tokenNumber} · {token.patientName}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div style={buttonRow}>
                            <button style={secondaryButton} onClick={handleNoShow} disabled={isNoShowDisabled}>
                                Mark No-show
                            </button>
                        </div>

                        <div style={field}>
                            <label style={label}>Reinstatable Token</label>
                            <select
                                style={input(false)}
                                value={reinstateTokenId}
                                onChange={e => setReinstateTokenId(e.target.value)}
                            >
                                <option value="">Select no-show or expired token</option>
                                {reinstatableTokens.map((token) => (
                                    <option key={token.id} value={token.id}>
                                        {token.tokenNumber} · {token.patientName}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div style={buttonRow}>
                            <button
                                style={secondaryButton}
                                onClick={handleReinstate}
                                disabled={isReinstateDisabled}
                            >
                                Reinstate
                            </button>
                        </div>

                        <div style={field}>
                            <label style={label}>Active Token</label>
                            <select
                                style={input(false)}
                                value={reassignTokenId}
                                onChange={e => setReassignTokenId(e.target.value)}
                            >
                                <option value="">Select active token</option>
                                {activeTokens.map((token) => (
                                    <option key={token.id} value={token.id}>
                                        {token.tokenNumber} · {token.patientName} · {token.status}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div style={reassignGrid}>
                            <select
                                style={input(false)}
                                value={reassignDoctorId}
                                onChange={e => setReassignDoctorId(e.target.value)}
                            >
                                <option value="">Select doctor</option>
                                {doctors.map(d => (
                                    <option key={d.id} value={d.id}>
                                        Dr. {d.name}
                                    </option>
                                ))}
                            </select>

                            <button
                                style={primaryButton}
                                onClick={handleReassign}
                                disabled={isReassignDisabled}
                            >
                                Reassign Doctor
                            </button>
                        </div>
                    </div>
                </div>

                <div style={card}>
                    <div style={sectionHeader}>
                        <div>
                            <p style={sectionEyebrow}>Live Dashboard</p>
                            <h3 style={sectionTitle}>Clinic Overview</h3>
                        </div>
                    </div>

                    {overview?.doctors?.map(d => (
                        <div key={d.doctorId} style={doctorRow}>
                            <div>
                                <strong style={doctorName}>{d.doctorName}</strong>
                                <div style={doctorMeta}>
                                    Current token: {d.currentToken || "—"}
                                </div>
                            </div>
                            <div style={doctorStats}>
                                <span style={pill}>{d.waitingCount} waiting</span>
                            </div>
                        </div>
                    ))}

                    {!overview?.doctors?.length ? (
                        <div style={emptyState}>No active doctors available.</div>
                    ) : null}
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

const statRow = {
    display: "grid",
    gridTemplateColumns: "repeat(2, minmax(160px, 1fr))",
    gap: "12px",
    width: "100%",
    maxWidth: "380px"
};

const statCard = {
    background: "#ffffff",
    border: "1px solid #d7e6ea",
    borderRadius: "18px",
    padding: "18px 20px",
    boxShadow: "0 10px 24px rgba(22, 49, 58, 0.06)"
};

const statLabel = {
    display: "block",
    fontSize: "13px",
    color: "#557b88",
    marginBottom: "8px"
};

const statValue = {
    fontSize: "28px",
    color: "#0f4f5c"
};

const errorBanner = {
    marginBottom: "12px",
    padding: "14px 16px",
    borderRadius: "14px",
    background: "#fff1f2",
    border: "1px solid #fecdd3",
    color: "#b42318"
};

const successBanner = {
    marginBottom: "12px",
    padding: "14px 16px",
    borderRadius: "14px",
    background: "#ecfdf3",
    border: "1px solid #abefc6",
    color: "#067647"
};

const layout = {
    display: "grid",
    gridTemplateColumns: "minmax(380px, 1.15fr) minmax(320px, 0.85fr)",
    gap: "20px"
};

const leftColumn = {
    display: "flex",
    flexDirection: "column",
    gap: "20px"
};

const card = {
    background: "#ffffff",
    border: "1px solid #d7e6ea",
    padding: "24px",
    borderRadius: "24px",
    display: "flex",
    flexDirection: "column",
    gap: "16px",
    boxShadow: "0 14px 34px rgba(22, 49, 58, 0.08)"
};

const sectionHeader = {
    display: "flex",
    justifyContent: "space-between",
    gap: "12px",
    alignItems: "flex-start"
};

const sectionEyebrow = {
    margin: 0,
    fontSize: "12px",
    letterSpacing: "0.12em",
    textTransform: "uppercase",
    color: "#557b88"
};

const sectionTitle = {
    margin: "6px 0 0",
    fontSize: "24px"
};

const formGrid = {
    display: "grid",
    gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
    gap: "16px"
};

const field = {
    display: "flex",
    flexDirection: "column",
    gap: "8px"
};

const fieldFull = {
    ...field,
    gridColumn: "1 / -1"
};

const label = {
    fontSize: "14px",
    fontWeight: "700",
    color: "#2f5562"
};

const input = (hasError) => ({
    width: "100%",
    boxSizing: "border-box",
    borderRadius: "14px",
    border: hasError ? "1px solid #f04438" : "1px solid #cfe0e6",
    padding: "12px 14px",
    fontSize: "15px",
    color: "#16313a",
    background: "#fbfeff"
});

const fieldError = {
    fontSize: "13px",
    color: "#b42318"
};

const optionalTag = {
    fontSize: "12px",
    fontWeight: "400",
    color: "#88a9b4",
    marginLeft: "4px"
};

const severityWrap = {
    display: "flex",
    gap: "12px",
    alignItems: "center",
    flexWrap: "wrap"
};

const severityInput = (hasError) => ({
    flex: 1,
    accentColor: hasError ? "#f04438" : "#0f766e"
});

const severityBadge = (score) => ({
    padding: "8px 12px",
    borderRadius: "999px",
    fontSize: "13px",
    fontWeight: "700",
    color: score >= 8 ? "#b54708" : "#0f4f5c",
    background: score >= 8 ? "#fff4e5" : "#e5f2f6"
});

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

const buttonRow = {
    display: "flex",
    gap: "12px",
    flexWrap: "wrap"
};

const reassignGrid = {
    display: "grid",
    gridTemplateColumns: "1fr auto",
    gap: "12px",
    alignItems: "center"
};

const doctorRow = {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: "12px",
    padding: "16px 0",
    borderBottom: "1px solid #e5eef1"
};

const doctorName = {
    display: "block",
    fontSize: "18px",
    marginBottom: "4px"
};

const doctorMeta = {
    fontSize: "14px",
    color: "#557b88"
};

const doctorStats = {
    display: "flex",
    alignItems: "center"
};

const pill = {
    fontSize: "13px",
    fontWeight: "700",
    color: "#0f766e",
    background: "#e2f3f5",
    padding: "8px 12px",
    borderRadius: "999px"
};

const emptyState = {
    padding: "28px 20px",
    borderRadius: "18px",
    background: "#f8fbfc",
    border: "1px dashed #c8d9df",
    color: "#557b88",
    textAlign: "center"
};
