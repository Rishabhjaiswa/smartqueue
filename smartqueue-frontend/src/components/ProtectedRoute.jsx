import { Navigate } from 'react-router-dom';
import useAuth from '../hooks/useAuth';

export default function ProtectedRoute({ allowedRoles, children }) {

    const { user, loading } = useAuth();

    if (loading) return <div style={{ padding: '2rem' }}>Loading...</div>;

    if (!user) return <Navigate to="/login" replace />;

    const hasRole = allowedRoles.includes(user.role);

    if (!hasRole) return <Navigate to="/unauthorized" replace />;

    return children;
}