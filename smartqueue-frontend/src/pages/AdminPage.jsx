import { useEffect, useState } from "react";
import { createStaff, getAdminAnalytics, getAdminHistory, getAuditLogs, getDoctors, getStaffList, resetStaffPassword } from "../services/api";

export default function AdminPage() {
    const [staff, setStaff] = useState([]);
    const [doctors, setDoctors] = useState([]);
    const [analytics, setAnalytics] = useState(null);
    const [history, setHistory] = useState([]);
    const [auditLogs, setAuditLogs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [showCreatePassword, setShowCreatePassword] = useState(false);
    const [showResetPassword, setShowResetPassword] = useState(false);
    const [resetPasswordForm, setResetPasswordForm] = useState({
        staffUserId: "",
        password: ""
    });
    const [form, setForm] = useState({
        username: "",
        password: "",
        role: "DOCTOR",
        doctorId: "",
        createNewDoctor: false,
        doctorName: "",
        specialization: "",
        roomNumber: "",
        avgConsultMins: "",
        officeId: "1"
    });

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            setLoading(true);
            const [staffRes, doctorsRes, analyticsRes, historyRes, auditRes] = await Promise.all([
                getStaffList(),
                getDoctors(),
                getAdminAnalytics(),
                getAdminHistory(),
                getAuditLogs()
            ]);
            setStaff(staffRes.data);
            setDoctors(doctorsRes.data);
            setAnalytics(analyticsRes.data || null);
            setHistory(historyRes.data || []);
            setAuditLogs(auditRes.data || []);
            setError("");
        } catch (err) {
            setError(err?.response?.data?.detail || err?.response?.data?.message || "Unable to load admin data.");
        } finally {
            setLoading(false);
        }
    };

    const handleCreateStaff = async () => {
        if (!form.username.trim() || !form.password.trim()) {
            setError("Username and password are required.");
            setMessage("");
            return;
        }

        if (form.role === "DOCTOR" && !form.doctorId && !form.createNewDoctor) {
            setError("Doctor mapping is required for doctor role.");
            setMessage("");
            return;
        }

        if (form.role === "DOCTOR" && form.createNewDoctor && !form.doctorName.trim()) {
            setError("Doctor name is required when creating a new doctor.");
            setMessage("");
            return;
        }

        try {
            setSubmitting(true);
            setError("");
            setMessage("");

            await createStaff({
                username: form.username.trim(),
                password: form.password,
                role: form.role,
                officeId: Number(form.officeId) || 1,
                doctorId: form.role === "DOCTOR" && !form.createNewDoctor ? Number(form.doctorId) : null,
                doctorName: form.role === "DOCTOR" && form.createNewDoctor ? form.doctorName.trim() : null,
                specialization: form.role === "DOCTOR" && form.createNewDoctor ? form.specialization.trim() : null,
                roomNumber: form.role === "DOCTOR" && form.createNewDoctor ? form.roomNumber.trim() : null,
                avgConsultMins: form.role === "DOCTOR" && form.createNewDoctor && form.avgConsultMins ? Number(form.avgConsultMins) : null
            });

            await loadData();
            setForm({
                username: "",
                password: "",
                role: "DOCTOR",
                doctorId: "",
                createNewDoctor: false,
                doctorName: "",
                specialization: "",
                roomNumber: "",
                avgConsultMins: "",
                officeId: "1"
            });
            setMessage("Staff user created.");
        } catch (err) {
            setError(err?.response?.data?.detail || err?.response?.data?.message || "Unable to create staff user.");
        } finally {
            setSubmitting(false);
        }
    };

    const handleResetPassword = async () => {
        if (!resetPasswordForm.staffUserId || !resetPasswordForm.password.trim()) {
            setError("Select a staff user and enter a new password.");
            setMessage("");
            return;
        }

        try {
            setSubmitting(true);
            setError("");
            setMessage("");
            await resetStaffPassword(Number(resetPasswordForm.staffUserId), resetPasswordForm.password);
            setResetPasswordForm({ staffUserId: "", password: "" });
            const selectedUser = staff.find((item) => String(item.id) === String(resetPasswordForm.staffUserId));
            setMessage(`Password reset successfully${selectedUser ? ` for ${selectedUser.username}` : ""}.`);
        } catch (err) {
            setError(err?.response?.data?.detail || err?.response?.data?.message || "Unable to reset password.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div style={page}>
            <div style={header}>
                <div>
                    <p style={eyebrow}>Administration</p>
                    <h1 style={title}>Staff Management</h1>
                    <p style={subtitle}>
                        Create clinic staff accounts and map doctor logins to existing doctor records.
                    </p>
                </div>
            </div>

            {error ? <div style={errorBanner}>{error}</div> : null}
            {message ? <div style={successBanner}>{message}</div> : null}

            <div style={layout}>
                <div style={card}>
                    <p style={sectionEyebrow}>Create Staff</p>
                    <h2 style={sectionTitle}>New Staff User</h2>

                    <div style={formGrid}>
                        <div style={field}>
                            <label style={label}>Username</label>
                            <input
                                style={input}
                                value={form.username}
                                onChange={(e) => setForm({ ...form, username: e.target.value })}
                                placeholder="Enter username"
                            />
                        </div>

                        <div style={field}>
                            <label style={label}>Password</label>
                            <div style={inputRow}>
                                <input
                                    style={inputFlex}
                                    type={showCreatePassword ? "text" : "password"}
                                    value={form.password}
                                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                                    placeholder="Enter password"
                                />
                                <button
                                    type="button"
                                    style={toggleButton}
                                    onClick={() => setShowCreatePassword((current) => !current)}
                                >
                                    {showCreatePassword ? "Hide" : "Show"}
                                </button>
                            </div>
                        </div>

                        <div style={field}>
                            <label style={label}>Office ID</label>
                            <input
                                style={input}
                                type="number"
                                min="1"
                                value={form.officeId}
                                onChange={(e) => setForm({ ...form, officeId: e.target.value })}
                                placeholder="1"
                            />
                        </div>

                        <div style={field}>
                            <label style={label}>Role</label>
                            <select
                                style={input}
                                value={form.role}
                                onChange={(e) => setForm({
                                    ...form,
                                    role: e.target.value,
                                    doctorId: e.target.value === "DOCTOR" ? form.doctorId : ""
                                })}
                            >
                                <option value="DOCTOR">DOCTOR</option>
                                <option value="RECEPTION">RECEPTION</option>
                            </select>
                        </div>

                        <div style={field}>
                            <label style={label}>Doctor Mapping</label>
                            <select
                                style={input}
                                value={form.doctorId}
                                disabled={form.role !== "DOCTOR" || form.createNewDoctor}
                                onChange={(e) => setForm({ ...form, doctorId: e.target.value })}
                            >
                                <option value="">Select doctor</option>
                                {doctors.map((doctor) => (
                                    <option key={doctor.id} value={doctor.id}>
                                        {doctor.name}
                                    </option>
                                ))}
                            </select>
                        </div>

                        {form.role === "DOCTOR" ? (
                            <>
                                <div style={fieldFull}>
                                    <label style={checkboxRow}>
                                        <input
                                            type="checkbox"
                                            checked={form.createNewDoctor}
                                            onChange={(e) => setForm({
                                                ...form,
                                                createNewDoctor: e.target.checked,
                                                doctorId: e.target.checked ? "" : form.doctorId
                                            })}
                                        />
                                        Create new doctor inline
                                    </label>
                                </div>

                                {form.createNewDoctor ? (
                                    <>
                                        <div style={field}>
                                            <label style={label}>Doctor Name</label>
                                            <input
                                                style={input}
                                                value={form.doctorName}
                                                onChange={(e) => setForm({ ...form, doctorName: e.target.value })}
                                                placeholder="Enter doctor name"
                                            />
                                        </div>
                                        <div style={field}>
                                            <label style={label}>Specialization</label>
                                            <input
                                                style={input}
                                                value={form.specialization}
                                                onChange={(e) => setForm({ ...form, specialization: e.target.value })}
                                                placeholder="General Medicine"
                                            />
                                        </div>
                                        <div style={field}>
                                            <label style={label}>Room Number</label>
                                            <input
                                                style={input}
                                                value={form.roomNumber}
                                                onChange={(e) => setForm({ ...form, roomNumber: e.target.value })}
                                                placeholder="101"
                                            />
                                        </div>
                                        <div style={field}>
                                            <label style={label}>Avg Consult Minutes</label>
                                            <input
                                                style={input}
                                                type="number"
                                                min="1"
                                                value={form.avgConsultMins}
                                                onChange={(e) => setForm({ ...form, avgConsultMins: e.target.value })}
                                                placeholder="10"
                                            />
                                        </div>
                                    </>
                                ) : null}
                            </>
                        ) : null}
                    </div>

                    <button style={primaryButton} disabled={submitting} onClick={handleCreateStaff}>
                        {submitting ? "Processing..." : "Create Staff"}
                    </button>
                </div>

                <div style={card}>
                    <p style={sectionEyebrow}>Staff Directory</p>
                    <h2 style={sectionTitle}>Current Staff</h2>

                    {loading ? (
                        <div style={emptyState}>Loading staff list...</div>
                    ) : staff.length === 0 ? (
                        <div style={emptyState}>No staff users available.</div>
                    ) : (
                        <div style={tableWrap}>
                            <table style={table}>
                                <thead>
                                <tr>
                                    <th style={th}>Username</th>
                                    <th style={th}>Role</th>
                                    <th style={th}>Office</th>
                                    <th style={th}>Doctor</th>
                                </tr>
                                </thead>
                                <tbody>
                                {staff.map((item) => (
                                    <tr key={item.id}>
                                        <td style={td}>{item.username}</td>
                                        <td style={td}>{item.role}</td>
                                        <td style={td}>{item.officeId ?? 1}</td>
                                        <td style={td}>{item.doctorName || "—"}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </div>

            <div style={auditLayout}>
                <div style={card}>
                    <p style={sectionEyebrow}>Password Reset</p>
                    <h2 style={sectionTitle}>Reset Staff Password</h2>

                    <div style={formGrid}>
                        <div style={field}>
                            <label style={label}>Staff User</label>
                            <select
                                style={input}
                                value={resetPasswordForm.staffUserId}
                                onChange={(e) => setResetPasswordForm({ ...resetPasswordForm, staffUserId: e.target.value })}
                            >
                                <option value="">Select staff user</option>
                                {staff.map((item) => (
                                    <option key={item.id} value={item.id}>
                                        {item.username} · {item.role}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div style={field}>
                            <label style={label}>New Password</label>
                            <div style={inputRow}>
                                <input
                                    style={inputFlex}
                                    type={showResetPassword ? "text" : "password"}
                                    value={resetPasswordForm.password}
                                    onChange={(e) => setResetPasswordForm({ ...resetPasswordForm, password: e.target.value })}
                                    placeholder="Enter new password"
                                />
                                <button
                                    type="button"
                                    style={toggleButton}
                                    onClick={() => setShowResetPassword((current) => !current)}
                                >
                                    {showResetPassword ? "Hide" : "Show"}
                                </button>
                            </div>
                        </div>
                    </div>

                    <button style={primaryButton} disabled={submitting} onClick={handleResetPassword}>
                        {submitting ? "Processing..." : "Reset Password"}
                    </button>
                </div>
            </div>

            <div style={secondaryLayout}>
                <div style={card}>
                    <p style={sectionEyebrow}>Daily Summary</p>
                    <h2 style={sectionTitle}>Clinic Analytics</h2>

                    <div style={analyticsGrid}>
                        <div style={metricCard}>
                            <span style={metricLabel}>Patients today</span>
                            <strong style={metricValue}>{loading ? "..." : (analytics?.totalPatientsToday || 0)}</strong>
                        </div>
                        <div style={metricCard}>
                            <span style={metricLabel}>Avg consult time</span>
                            <strong style={metricValue}>{loading ? "..." : `${Math.round(analytics?.averageConsultMinutes || 0)} min`}</strong>
                        </div>
                        <div style={metricCard}>
                            <span style={metricLabel}>Avg wait time</span>
                            <strong style={metricValue}>{loading ? "..." : `${Math.round(analytics?.averageWaitMinutes || 0)} min`}</strong>
                        </div>
                    </div>

                    <div style={performanceList}>
                        {(analytics?.doctorPerformance || []).map((item) => (
                            <div key={item.doctorId} style={performanceRow}>
                                <div>
                                    <strong style={performanceName}>{item.doctorName}</strong>
                                    <div style={performanceMeta}>
                                        Avg consult: {item.averageConsultMinutes} min
                                    </div>
                                </div>
                                <div style={performanceBadges}>
                                    <span style={pill}>{item.waitingCount} waiting</span>
                                    <span style={statusPill(item.available)}>
                                        {item.available ? "Available" : "Unavailable"}
                                    </span>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                <div style={card}>
                    <p style={sectionEyebrow}>History</p>
                    <h2 style={sectionTitle}>Recent Completed Tokens</h2>

                    {loading ? (
                        <div style={emptyState}>Loading history...</div>
                    ) : history.length === 0 ? (
                        <div style={emptyState}>No completed consultations yet.</div>
                    ) : (
                        <div style={scrollList}>
                            {history.map((item, index) => (
                                <div key={`${item.tokenNumber}-${index}`} style={historyRow}>
                                    <div>
                                        <strong style={performanceName}>{item.tokenNumber}</strong>
                                        <div style={performanceMeta}>
                                            {item.patientName} · {item.doctorName}
                                        </div>
                                    </div>
                                    <div style={historyMeta}>
                                        <span style={pill}>{item.status}</span>
                                        <span style={historyTime}>
                                            {item.consultationEnd ? new Date(item.consultationEnd).toLocaleString() : "Completed"}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            <div style={auditLayout}>
                <div style={card}>
                    <p style={sectionEyebrow}>Audit Trail</p>
                    <h2 style={sectionTitle}>Recent System Actions</h2>

                    {loading ? (
                        <div style={emptyState}>Loading audit logs...</div>
                    ) : auditLogs.length === 0 ? (
                        <div style={emptyState}>No audit activity recorded yet.</div>
                    ) : (
                        <div style={scrollList}>
                            {auditLogs.map((item, index) => (
                                <div key={`${item.action}-${index}`} style={historyRow}>
                                    <div>
                                        <strong style={performanceName}>{item.action}</strong>
                                        <div style={performanceMeta}>
                                            {item.actorUsername} · {item.details}
                                        </div>
                                    </div>
                                    <div style={historyMeta}>
                                        <span style={historyTime}>
                                            {item.createdAt ? new Date(item.createdAt).toLocaleString() : ""}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
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
    marginBottom: "24px"
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
    gridTemplateColumns: "minmax(360px, 420px) 1fr",
    gap: "20px"
};

const secondaryLayout = {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
    gap: "20px",
    marginTop: "20px"
};

const auditLayout = {
    marginTop: "20px"
};

const card = {
    background: "#ffffff",
    border: "1px solid #d7e6ea",
    borderRadius: "24px",
    padding: "24px",
    boxShadow: "0 14px 34px rgba(22, 49, 58, 0.08)"
};

const sectionEyebrow = {
    margin: 0,
    fontSize: "12px",
    letterSpacing: "0.12em",
    textTransform: "uppercase",
    color: "#557b88"
};

const sectionTitle = {
    margin: "6px 0 20px",
    fontSize: "24px"
};

const formGrid = {
    display: "grid",
    gap: "16px",
    marginBottom: "20px"
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

const checkboxRow = {
    display: "flex",
    alignItems: "center",
    gap: "10px",
    fontSize: "14px",
    fontWeight: "700",
    color: "#2f5562"
};

const input = {
    width: "100%",
    boxSizing: "border-box",
    borderRadius: "14px",
    border: "1px solid #cfe0e6",
    padding: "12px 14px",
    fontSize: "15px",
    color: "#16313a",
    background: "#fbfeff"
};

const inputRow = {
    display: "flex",
    alignItems: "center",
    gap: "10px"
};

const inputFlex = {
    ...input,
    flex: 1
};

const toggleButton = {
    border: "1px solid #cfe0e6",
    borderRadius: "12px",
    padding: "11px 14px",
    background: "#ffffff",
    color: "#0f4f5c",
    fontSize: "14px",
    fontWeight: "700",
    cursor: "pointer"
};

const primaryButton = {
    border: "none",
    borderRadius: "14px",
    padding: "12px 18px",
    fontSize: "15px",
    fontWeight: "700",
    cursor: "pointer",
    background: "#0f766e",
    color: "#ffffff"
};

const tableWrap = {
    overflowX: "auto"
};

const table = {
    width: "100%",
    borderCollapse: "collapse"
};

const th = {
    textAlign: "left",
    padding: "12px 10px",
    borderBottom: "1px solid #d7e6ea",
    color: "#557b88",
    fontSize: "13px",
    letterSpacing: "0.04em"
};

const td = {
    padding: "14px 10px",
    borderBottom: "1px solid #edf3f5",
    fontSize: "15px"
};

const emptyState = {
    padding: "28px 20px",
    borderRadius: "18px",
    background: "#f8fbfc",
    border: "1px dashed #c8d9df",
    color: "#557b88",
    textAlign: "center"
};

const analyticsGrid = {
    display: "grid",
    gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
    gap: "12px",
    marginBottom: "18px"
};

const metricCard = {
    background: "#f7fbfc",
    border: "1px solid #dcebef",
    borderRadius: "18px",
    padding: "16px"
};

const metricLabel = {
    display: "block",
    fontSize: "13px",
    color: "#557b88",
    marginBottom: "8px"
};

const metricValue = {
    fontSize: "26px",
    color: "#0f4f5c"
};

const performanceList = {
    display: "flex",
    flexDirection: "column",
    gap: "12px"
};

const performanceRow = {
    display: "flex",
    justifyContent: "space-between",
    gap: "12px",
    alignItems: "center",
    padding: "14px 0",
    borderBottom: "1px solid #e6eff2"
};

const performanceName = {
    fontSize: "16px",
    color: "#16313a"
};

const performanceMeta = {
    marginTop: "4px",
    fontSize: "13px",
    color: "#557b88"
};

const performanceBadges = {
    display: "flex",
    gap: "8px",
    alignItems: "center",
    flexWrap: "wrap"
};

const pill = {
    padding: "7px 10px",
    borderRadius: "999px",
    background: "#e6f4f7",
    color: "#0f4f5c",
    fontSize: "12px",
    fontWeight: "700"
};

const statusPill = (available) => ({
    ...pill,
    background: available ? "#dff7f2" : "#fff1f2",
    color: available ? "#0f766e" : "#b42318"
});

const historyList = {
    display: "flex",
    flexDirection: "column",
    gap: "12px"
};

const scrollList = {
    ...historyList,
    maxHeight: "360px",
    overflowY: "auto",
    paddingRight: "6px"
};

const historyRow = {
    display: "flex",
    justifyContent: "space-between",
    gap: "12px",
    alignItems: "center",
    padding: "14px 0",
    borderBottom: "1px solid #e6eff2"
};

const historyMeta = {
    display: "flex",
    flexDirection: "column",
    gap: "8px",
    alignItems: "flex-end"
};

const historyTime = {
    fontSize: "12px",
    color: "#557b88"
};
