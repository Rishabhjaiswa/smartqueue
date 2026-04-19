import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';

import LoginPage from './pages/LoginPage';
import DoctorPanel from "./pages/DoctorPanel";
import ReceptionPanel from "./pages/ReceptionPanel";
import DisplayBoard from "./pages/DisplayBoard";
import AdminPage from "./pages/AdminPage";

export default function App() {
    return (
        <AuthProvider>
            <BrowserRouter>
                <Routes>

                    <Route path="/login" element={<LoginPage />} />

                    <Route path="/doctor" element={
                        <ProtectedRoute allowedRoles={['ROLE_DOCTOR']}>
                            <DoctorPanel />
                        </ProtectedRoute>
                    } />

                    <Route path="/reception" element={
                        <ProtectedRoute allowedRoles={['ROLE_RECEPTIONIST']}>
                            <ReceptionPanel />
                        </ProtectedRoute>
                    } />
                    <Route path="/admin" element={
                        <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                            <AdminPage />
                        </ProtectedRoute>
                    } />
                    <Route path="/board" element={<DisplayBoard />} />
                    <Route path="/display" element={<DisplayBoard />} />
                    {/* Add this to avoid blank */}
                    <Route path="/unauthorized" element={<div>Unauthorized</div>} />

                    <Route path="/" element={<Navigate to="/login" />} />

                </Routes>
            </BrowserRouter>
        </AuthProvider>
    );
}
