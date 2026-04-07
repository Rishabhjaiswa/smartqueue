import StaffDashboard from '../components/StaffDashboard';

export default function StaffPage() {
    return (
        <div style={{
            minHeight: '100vh', background: '#fafafa',
            padding: '32px 16px'
        }}>
            <StaffDashboard officeId={1} />
        </div>
    );
}