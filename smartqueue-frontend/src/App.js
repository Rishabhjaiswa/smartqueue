import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import CitizenPage from './pages/CitizenPage';
import BoardPage from './pages/BoardPage';
import StaffPage from './pages/StaffPage';

function NavBar() {
  return (
    <nav style={{
      background: '#fff', borderBottom: '1px solid #eee',
      padding: '12px 24px', display: 'flex', gap: '24px',
      alignItems: 'center'
    }}>
      <span style={{ fontWeight: '500', fontSize: '15px', color: '#111' }}>
        SmartQueue
      </span>
      <Link to="/" style={{ color: '#555', textDecoration: 'none', fontSize: '14px' }}>Citizen</Link>
      <Link to="/board" style={{ color: '#555', textDecoration: 'none', fontSize: '14px' }}>Display Board</Link>
      <Link to="/staff" style={{ color: '#555', textDecoration: 'none', fontSize: '14px' }}>Staff</Link>
    </nav>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <NavBar />
      <Routes>
        <Route path="/" element={<CitizenPage />} />
        <Route path="/board" element={<BoardPage />} />
        <Route path="/staff" element={<StaffPage />} />
      </Routes>
    </BrowserRouter>
  );
}