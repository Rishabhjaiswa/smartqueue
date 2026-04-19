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

        getMe()
            .then(res => setUser(res.data))
            .catch(() => setUser(null))
            .finally(() => setLoading(false));
    }, []);

    const login = useCallback(async (username, password) => {
        resetLoginRedirect();
        await apiLogin(username, password);
        const res = await getMe();
        setUser(res.data);
        return res.data;
    }, []);

    const logout = useCallback(async () => {
        await apiLogout();
        resetLoginRedirect();
        setUser(null);
    }, []);

    return (
        <AuthContext.Provider value={{ user, loading, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}
