import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import useAuth from "../hooks/useAuth";

const ROLE_REDIRECT = {
  ROLE_DOCTOR: "/doctor",
  ROLE_RECEPTIONIST: "/reception",
  ROLE_ADMIN: "/admin",
};

export default function LoginPage() {

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const storedMessage = sessionStorage.getItem("authMessage");
    if (storedMessage) {
      setError(storedMessage);
      sessionStorage.removeItem("authMessage");
    }
  }, []);

  const handleLogin = async () => {
    try {
      setError("");
      const user = await login(username, password);
      navigate(ROLE_REDIRECT[user.role] || "/", { replace: true });
    } catch (err) {
      setError(err?.response?.data?.detail || err?.response?.data?.message || "Invalid username or password");
    }
  };

  return (
    <div style={page}>
      <div style={card}>
        <p style={eyebrow}>Clinic Queue Platform</p>
        <h1 style={title}>SmartQueue</h1>
        <p style={subtitle}>Clinic Queue Management</p>

        {error && <div style={errorBanner}>{error}</div>}

        <div style={field}>
          <label style={label}>Username</label>
          <input
            style={input}
            placeholder="Enter username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </div>

        <div style={field}>
          <label style={label}>Password</label>
          <input
            style={input}
            type={showPassword ? "text" : "password"}
            placeholder="Enter password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <button
            type="button"
            style={toggleButton}
            onClick={() => setShowPassword((current) => !current)}
          >
            {showPassword ? "Hide Password" : "Show Password"}
          </button>
        </div>

        <button style={button} onClick={handleLogin}>
          Login
        </button>
      </div>
    </div>
  );
}

const page = {
  minHeight: "100vh",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  padding: "24px",
  background: "linear-gradient(180deg, #e9f7fa 0%, #f5fbfc 50%, #eef4f7 100%)",
  fontFamily: "Arial"
};

const card = {
  width: "100%",
  maxWidth: "420px",
  background: "#ffffff",
  borderRadius: "28px",
  padding: "32px",
  border: "1px solid #d7e6ea",
  boxShadow: "0 18px 40px rgba(22, 49, 58, 0.10)"
};

const eyebrow = {
  margin: 0,
  fontSize: "12px",
  letterSpacing: "0.14em",
  textTransform: "uppercase",
  color: "#557b88"
};

const title = {
  margin: "8px 0 6px",
  fontSize: "36px",
  color: "#16313a"
};

const subtitle = {
  margin: "0 0 24px",
  color: "#557b88",
  fontSize: "16px"
};

const errorBanner = {
  marginBottom: "16px",
  padding: "12px 14px",
  borderRadius: "14px",
  background: "#fff1f2",
  border: "1px solid #fecdd3",
  color: "#b42318"
};

const field = {
  display: "flex",
  flexDirection: "column",
  gap: "8px",
  marginBottom: "16px"
};

const label = {
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

const button = {
  width: "100%",
  border: "none",
  borderRadius: "14px",
  padding: "13px 18px",
  fontSize: "15px",
  fontWeight: "700",
  cursor: "pointer",
  background: "#0f766e",
  color: "#ffffff",
  marginTop: "8px"
};

const toggleButton = {
  border: "none",
  background: "transparent",
  color: "#0f766e",
  fontSize: "13px",
  fontWeight: "700",
  padding: 0,
  textAlign: "left",
  cursor: "pointer"
};
