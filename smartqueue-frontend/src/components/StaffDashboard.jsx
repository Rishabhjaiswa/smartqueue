import { useState } from 'react';
import useQueue from '../hooks/useQueue';
import { callNext, completeToken, markNoShow, staffOverride } from '../services/api';

export default function StaffDashboard({ officeId = 1 }) {
    const { queueState, connected } = useQueue(officeId);
    const { currentToken, waitingCount, nextTokens } = queueState;
    const [lastCalled, setLastCalled] = useState(null);
    const [lastCalledId, setLastCalledId] = useState(null);
    const [overrideInput, setOverrideInput] = useState('');
    const [msg, setMsg] = useState('');

    const flash = (text) => {
        setMsg(text);
        setTimeout(() => setMsg(''), 3000);
    };

    const handleCallNext = async () => {
        try {
            const res = await callNext(officeId);
            setLastCalled(res.data.tokenNumber);
            setLastCalledId(res.data.id);
            flash(`Calling: ${res.data.tokenNumber}`);
        } catch { flash('Error calling next token'); }
    };

    const handleComplete = async () => {
        if (!lastCalledId) return;
        try {
            await completeToken(lastCalledId);
            flash(`Completed: ${lastCalled}`);
            setLastCalled(null);
            setLastCalledId(null);
        } catch { flash('Error completing token'); }
    };

    const handleNoShow = async () => {
        if (!lastCalledId) return;
        try {
            await markNoShow(lastCalledId, officeId);
            flash(`No-show: ${lastCalled}`);
            setLastCalled(null);
            setLastCalledId(null);
        } catch { flash('Error marking no-show'); }
    };

    const handleOverride = async () => {
        if (!overrideInput.trim()) return;
        try {
            await staffOverride(overrideInput.trim(), officeId);
            flash(`Moved ${overrideInput.trim()} to front`);
            setOverrideInput('');
        } catch { flash('Error overriding token'); }
    };

    return (
        <div style={{ padding: '24px', fontFamily: 'sans-serif', maxWidth: '500px' }}>
            <div style={{
                display: 'flex', justifyContent: 'space-between',
                alignItems: 'center', marginBottom: '20px'
            }}>
                <h2 style={{ fontSize: '18px', fontWeight: '500', margin: 0 }}>
                    Staff — Office {officeId}
                </h2>
                <div style={{
                    width: '8px', height: '8px', borderRadius: '50%',
                    background: connected ? '#1D9E75' : '#E24B4A'
                }} />
            </div>

            {msg && (
                <div style={{
                    background: '#E1F5EE', color: '#0F6E56', borderRadius: '8px',
                    padding: '10px 14px', fontSize: '13px', marginBottom: '16px'
                }}>
                    {msg}
                </div>
            )}

            <div style={{
                display: 'grid', gridTemplateColumns: '1fr 1fr',
                gap: '12px', marginBottom: '20px'
            }}>
                <div style={{
                    background: '#f5f5f5', borderRadius: '12px',
                    padding: '16px', textAlign: 'center'
                }}>
                    <div style={{ fontSize: '28px', fontWeight: '600' }}>
                        {currentToken || '—'}
                    </div>
                    <div style={{ fontSize: '12px', color: '#888', marginTop: '4px' }}>
                        Current
                    </div>
                </div>
                <div style={{
                    background: '#f5f5f5', borderRadius: '12px',
                    padding: '16px', textAlign: 'center'
                }}>
                    <div style={{ fontSize: '28px', fontWeight: '600' }}>{waitingCount}</div>
                    <div style={{ fontSize: '12px', color: '#888', marginTop: '4px' }}>
                        Waiting
                    </div>
                </div>
            </div>

            <button
                onClick={handleCallNext}
                disabled={waitingCount === 0}
                style={{
                    width: '100%', padding: '14px', borderRadius: '10px', border: 'none',
                    background: waitingCount === 0 ? '#ccc' : '#0F6E56',
                    color: '#fff', fontSize: '15px', fontWeight: '500',
                    cursor: waitingCount === 0 ? 'not-allowed' : 'pointer',
                    marginBottom: '10px'
                }}
            >
                Call Next Token
            </button>

            {lastCalled && (
                <div style={{
                    display: 'grid', gridTemplateColumns: '1fr 1fr',
                    gap: '10px', marginBottom: '20px'
                }}>
                    <button onClick={handleComplete}
                        style={{
                            padding: '12px', borderRadius: '10px', border: 'none',
                            background: '#185FA5', color: '#fff', fontSize: '14px',
                            fontWeight: '500', cursor: 'pointer'
                        }}>
                        Complete — {lastCalled}
                    </button>
                    <button onClick={handleNoShow}
                        style={{
                            padding: '12px', borderRadius: '10px', border: 'none',
                            background: '#A32D2D', color: '#fff', fontSize: '14px',
                            fontWeight: '500', cursor: 'pointer'
                        }}>
                        No-show — {lastCalled}
                    </button>
                </div>
            )}

            <div style={{ borderTop: '1px solid #eee', paddingTop: '16px', marginTop: '8px' }}>
                <div style={{ fontSize: '12px', color: '#888', marginBottom: '8px' }}>
                    OVERRIDE — move token to front
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <input
                        value={overrideInput}
                        onChange={e => setOverrideInput(e.target.value)}
                        placeholder="Token number e.g. T003"
                        style={{
                            flex: 1, padding: '10px 12px', borderRadius: '8px',
                            border: '1px solid #ddd', fontSize: '14px'
                        }}
                    />
                    <button onClick={handleOverride}
                        style={{
                            padding: '10px 16px', borderRadius: '8px', border: 'none',
                            background: '#BA7517', color: '#fff', cursor: 'pointer',
                            fontSize: '14px', fontWeight: '500'
                        }}>
                        Override
                    </button>
                </div>
            </div>

            {nextTokens?.length > 0 && (
                <div style={{ marginTop: '16px' }}>
                    <div style={{ fontSize: '12px', color: '#888', marginBottom: '8px' }}>
                        UP NEXT
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        {nextTokens.map((t, i) => (
                            <div key={i} style={{
                                padding: '6px 14px', borderRadius: '8px',
                                background: '#f0f0f0', fontSize: '14px', fontWeight: '500'
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