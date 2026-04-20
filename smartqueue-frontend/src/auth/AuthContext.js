import { createContext, useState, useEffect, useCallback } from 'react';
import { getMe, login as apiLogin, logout as apiLogout, resetLoginRedirect } from '../services/api';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {

    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (window.location.pathname === '/login') {
            setLoading(false);
            return;
        }

        const token = localStorage.getItem("token");
        if (!token) {
            setLoading(false);
            return;
        }

        getMe()
            .then(res => setUser(res.data))
            .catch(() => {
                localStorage.removeItem("token");
                localStorage.removeItem("role");
                setUser(null);
            })
            .finally(() => setLoading(false));
    }, []);

    const login = useCallback(async (username, password) => {
        resetLoginRedirect();
        const loginRes = await apiLogin(username, password);
        const { token, role } = loginRes.data;
        localStorage.setItem("token", token);
        localStorage.setItem("role", role);

        const res = await getMe();
        setUser(res.data);
        return res.data;
    }, []);

    const logout = useCallback(async () => {
        await apiLogout();
        resetLoginRedirect();
        localStorage.removeItem("token");
        localStorage.removeItem("role");
        setUser(null);
        window.location.assign("/login");
    }, []);

    return (
        <AuthContext.Provider value={{ user, loading, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}
