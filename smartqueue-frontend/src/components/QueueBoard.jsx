import useQueue from '../hooks/useQueue';

export default function QueueBoard({ officeId = 1 }) {
    const { queueState, connected } = useQueue(officeId);
    const { currentToken, waitingCount, avgWaitMinutes, nextTokens } = queueState;

    return (
        <div style={{ padding: '24px', fontFamily: 'sans-serif' }}>
            <div style={{
                display: 'flex', justifyContent: 'space-between',
                alignItems: 'center', marginBottom: '24px'
            }}>
                <h2 style={{ fontSize: '18px', fontWeight: '500', margin: 0 }}>
                    Queue — Office {officeId}
                </h2>
                <div style={{
                    width: '8px', height: '8px', borderRadius: '50%',
                    background: connected ? '#1D9E75' : '#E24B4A'
                }} title={connected ? 'Live' : 'Disconnected'} />
            </div>

            <div style={{
                background: '#0F6E56', color: '#fff', borderRadius: '16px',
                padding: '32px', textAlign: 'center', marginBottom: '16px'
            }}>
                <div style={{ fontSize: '13px', opacity: 0.7, marginBottom: '8px' }}>
                    NOW SERVING
                </div>
                <div style={{ fontSize: '72px', fontWeight: '700', lineHeight: '1' }}>
                    {currentToken || '—'}
                </div>
            </div>

            <div style={{
                display: 'grid', gridTemplateColumns: '1fr 1fr',
                gap: '12px', marginBottom: '20px'
            }}>
                <div style={{ background: '#f5f5f5', borderRadius: '12px', padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '28px', fontWeight: '600' }}>{waitingCount}</div>
                    <div style={{ fontSize: '12px', color: '#888', marginTop: '4px' }}>Waiting</div>
                </div>
                <div style={{ background: '#f5f5f5', borderRadius: '12px', padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '28px', fontWeight: '600' }}>~{avgWaitMinutes}m</div>
                    <div style={{ fontSize: '12px', color: '#888', marginTop: '4px' }}>Avg wait</div>
                </div>
            </div>

            {nextTokens?.length > 0 && (
                <div>
                    <div style={{
                        fontSize: '12px', color: '#888', marginBottom: '8px',
                        textTransform: 'uppercase', letterSpacing: '0.05em'
                    }}>
                        Up next
                    </div>
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                        {nextTokens.map((t, i) => (
                            <div key={i} style={{
                                padding: '8px 16px', borderRadius: '8px',
                                background: '#E1F5EE', color: '#0F6E56',
                                fontSize: '16px', fontWeight: '500'
                            }}>
                                {t}
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}