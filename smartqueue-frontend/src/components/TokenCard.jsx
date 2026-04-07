export default function TokenCard({ token }) {
    if (!token) return null;

    const { tokenNumber, serviceType, positionInQueue,
        estimatedWaitMinutes, status } = token;

    const statusColor = {
        WAITING: { bg: '#E6F1FB', text: '#185FA5' },
        CALLED: { bg: '#E1F5EE', text: '#0F6E56' },
        COMPLETED: { bg: '#EAF3DE', text: '#3B6D11' },
    }[status] || { bg: '#F1EFE8', text: '#5F5E5A' };

    return (
        <div style={{
            border: '1px solid #e0e0e0', borderRadius: '16px',
            padding: '24px', textAlign: 'center', background: '#fff',
            maxWidth: '320px', margin: '0 auto'
        }}>
            <div style={{
                fontSize: '12px', color: '#888', marginBottom: '8px',
                textTransform: 'uppercase', letterSpacing: '0.06em'
            }}>
                Your Token
            </div>
            <div style={{
                fontSize: '64px', fontWeight: '700', color: '#111',
                lineHeight: '1', marginBottom: '8px'
            }}>
                {tokenNumber}
            </div>
            <div style={{
                display: 'inline-block', padding: '4px 12px', borderRadius: '20px',
                background: statusColor.bg, color: statusColor.text,
                fontSize: '12px', fontWeight: '500', marginBottom: '16px'
            }}>
                {status}
            </div>
            <div style={{ fontSize: '13px', color: '#555', marginBottom: '4px' }}>
                {serviceType?.replace(/_/g, ' ')}
            </div>
            <div style={{
                display: 'grid', gridTemplateColumns: '1fr 1fr',
                gap: '12px', marginTop: '16px'
            }}>
                <div style={{ background: '#f5f5f5', borderRadius: '10px', padding: '12px' }}>
                    <div style={{ fontSize: '22px', fontWeight: '600', color: '#111' }}>
                        #{positionInQueue}
                    </div>
                    <div style={{ fontSize: '11px', color: '#888', marginTop: '2px' }}>
                        Position
                    </div>
                </div>
                <div style={{ background: '#f5f5f5', borderRadius: '10px', padding: '12px' }}>
                    <div style={{ fontSize: '22px', fontWeight: '600', color: '#111' }}>
                        ~{estimatedWaitMinutes}m
                    </div>
                    <div style={{ fontSize: '11px', color: '#888', marginTop: '2px' }}>
                        Est. wait
                    </div>
                </div>
            </div>
        </div>
    );
}