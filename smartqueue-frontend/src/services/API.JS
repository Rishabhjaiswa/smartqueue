import axios from "axios";

const BASE = process.env.REACT_APP_API_URL || "http://localhost:8080";

const api = axios.create({
    baseURL: BASE,
    withCredentials: true,
});

let redirectedToLogin = false;

// 🔥 Handle 401 globally
api.interceptors.response.use(
    res => res,
    err => {
        if (err.response?.status === 401) {
            const isLoginPage = window.location.pathname === "/login";
            if (!isLoginPage && !redirectedToLogin) {
                redirectedToLogin = true;
                sessionStorage.setItem("authMessage", "Session expired. Please login again");
                window.location.assign("/login");
            }
        }
        console.error("API ERROR:", err.response || err);
        return Promise.reject(err);
    }
);

// ======================
// 🔐 AUTH
// ======================
export const login = (username, password) =>
    api.post(
        "/api/auth/login",
        new URLSearchParams({ username, password }),
        {
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
        }
    );

export const getDoctorQueue = () =>
    api.get("/api/doctor/queue");

export const reassignDoctor = (tokenId, doctorId) =>
    api.put(`/api/reception/token/${tokenId}/doctor/${doctorId}`);

export const logout = () => api.post("/api/auth/logout");

export const getMe = () => api.get("/api/auth/me");
export const createStaff = (data) => api.post("/api/admin/create-staff", data);
export const resetStaffPassword = (staffUserId, password) =>
    api.post(`/api/admin/staff/${staffUserId}/reset-password`, { password });
export const getStaffList = () => api.get("/api/admin/staff");
export const getAdminAnalytics = () => api.get("/api/admin/analytics");
export const getAdminHistory = () => api.get("/api/admin/history");
export const getAuditLogs = () => api.get("/api/admin/audit-logs");


// ======================
// 📊 RECEPTION
// ======================
export const checkInWalkIn = (data) =>
    api.post("/api/reception/checkin", data);

export const bookAppointment = (data) =>
    api.post("/api/reception/appointment", data);

export const markNoShow = (tokenId) =>
    api.post(`/api/reception/token/${tokenId}/noshow`);

export const reinstateNoShow = (tokenId, reason) =>
    api.post(`/api/reception/token/${tokenId}/reinstate`, null, {
        params: { reason },
    });

export const getReceptionOverview = () =>
    api.get("/api/reception/overview");
export const getWaitingTokens = () => api.get("/api/reception/tokens/waiting");
export const getActiveTokens = () => api.get("/api/reception/tokens/active");
export const getReinstatableTokens = () => api.get("/api/reception/tokens/reinstatable");

export const referPatient = (tokenId, toDoctorId) =>
    api.post(`/api/doctor/token/${tokenId}/refer/${toDoctorId}`);
// ======================
// 👨‍⚕️ DOCTOR
// ======================
export const callNext = () =>
    api.post("/api/doctor/call-next");

export const startConsultation = (tokenId) =>
    api.post(`/api/doctor/token/${tokenId}/in-consultation`);

export const completeConsultation = (tokenId) =>
    api.post(`/api/doctor/token/${tokenId}/complete`);

export const extendConsultation = (tokenId) =>
    api.post(`/api/doctor/token/${tokenId}/extend`);

export const setDoctorAvailability = (available) =>
    api.put("/api/doctor/availability", null, {
        params: { available },
    });

// ======================
// 🏥 ADMIN / DATA
// ======================
export const getDoctors = () =>
    api.get("/api/reception/doctors");

export const getDisplayBoard = () =>
    api.get("/api/reception/display");

// ======================
// 🤖 AI Chat
// ======================
export const sendChatMessage = (message, officeId) =>
    api.post("/api/chat", { message, officeId });

export default api;

export const resetLoginRedirect = () => {
    redirectedToLogin = false;
};
