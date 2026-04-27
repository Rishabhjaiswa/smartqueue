import { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getTokenStatus } from '../services/api';

const LS_KEY = 'smartqueue_activeTokenId';

const STATUS_CONFIG = {
    WAITING:         { label: 'Waiting',         color: '#6366f1', icon: '🕐', pulse: true  },
    CALLED:          { label: 'Being Called!',    color: '#f59e0b', icon: '📢', pulse: true  },
    IN_CONSULTATION: { label: 'In Consultation',  color: '#10b981', icon: '🩺', pulse: false },
    COMPLETED:       { label: 'Completed',        color: '#64748b', icon: '✅', pulse: false },
    CANCELLED:       { label: 'Cancelled',        color: '#ef4444', icon: '❌', pulse: false },
    NO_SHOW:         { label: 'Marked No-Show',   color: '#ef4444', icon: '⚠️', pulse: false },
    EXPIRED:         { label: 'Expired',          color: '#94a3b8', icon: '⌛', pulse: false },
};

export default function PatientStatusPage() {
    const { tokenId } = useParams();
    const navigate    = useNavigate();

    const [data,    setData]    = useState(null);
    const [error,   setError]   = useState(null);
    const [loading, setLoading] = useState(true);
    const [lastUpdated, setLastUpdated] = useState(null);

    const intervalRef = useRef(null);

    // ── Persist tokenId to localStorage ────────────────────────────────────
    useEffect(() => {
        if (tokenId) {
            localStorage.setItem(LS_KEY, tokenId);
        }
    }, [tokenId]);

    // ── Poll backend every 15 s ─────────────────────────────────────────────
    const fetchStatus = useCallback(async () => {
        try {
            const res = await getTokenStatus(tokenId);
            setData(res.data);
            setError(null);
            setLastUpdated(new Date());
        } catch (err) {
            if (err.response?.status === 404) {
                setError('Token not found. Please check your link.');
            } else {
                setError('Unable to reach server. Retrying…');
            }
        } finally {
            setLoading(false);
        }
    }, [tokenId]);

    useEffect(() => {
        fetchStatus();
        intervalRef.current = setInterval(fetchStatus, 15_000);
        return () => clearInterval(intervalRef.current);
    }, [fetchStatus]);

    // ── Restore from localStorage if navigated to /status without id ────────
    useEffect(() => {
        if (!tokenId) {
            const saved = localStorage.getItem(LS_KEY);
            if (saved) navigate(`/status/${saved}`, { replace: true });
        }
    }, [tokenId, navigate]);

    const cfg    = data ? (STATUS_CONFIG[data.status] || STATUS_CONFIG.WAITING) : null;
    const isDone = data && ['COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED'].includes(data.status);

    return (
        <div style={styles.page}>
            {/* ── Header ── */}
            <div style={styles.header}>
                <div style={styles.logo}>🏥 SmartQueue</div>
                <div style={styles.subtitle}>Live Patient Tracking</div>
            </div>

            <div style={styles.card}>
                {loading && (
                    <div style={styles.center}>
                        <div style={styles.spinner} />
                        <p style={styles.loadingText}>Loading your status…</p>
                    </div>
                )}

                {!loading && error && (
                    <div style={styles.errorBox}>
                        <span style={{ fontSize: 32 }}>⚠️</span>
                        <p style={{ margin: '12px 0 0', color: '#ef4444' }}>{error}</p>
                        <button style={styles.retryBtn} onClick={fetchStatus}>Retry</button>
                    </div>
                )}

                {!loading && data && (
                    <>
                        {/* Token badge */}
                        <div style={{ ...styles.statusBadge, background: cfg.color + '22', borderColor: cfg.color }}>
                            <span style={styles.statusIcon}>{cfg.icon}</span>
                            <span style={{ ...styles.statusLabel, color: cfg.color }}>{cfg.label}</span>
                            {cfg.pulse && <span style={{ ...styles.pulseDot, background: cfg.color }} />}
                        </div>

                        {/* Token number */}
                        <div style={styles.tokenNumber}>{data.tokenNumber}</div>

                        {/* Info grid */}
                        <div style={styles.grid}>
                            {!isDone && (
                                <>
                                    <InfoTile
                                        icon="🏥" label="Doctor"
                                        value={data.doctorName || 'TBD'}
                                    />
                                    {data.roomNumber && (
                                        <InfoTile
                                            icon="🚪" label="Room"
                                            value={data.roomNumber}
                                        />
                                    )}
                                    <InfoTile
                                        icon="👥" label="Position"
                                        value={data.positionInQueue > 0 ? `#${data.positionInQueue}` : '—'}
                                    />
                                    <InfoTile
                                        icon="⏳" label="Est. Wait"
                                        value={data.estimatedWaitMinutes > 0
                                            ? `${data.estimatedWaitMinutes}–${data.estimatedWaitMinutes + 10} min`
                                            : 'Soon'}
                                    />
                                </>
                            )}
                            {data.serviceType && (
                                <InfoTile
                                    icon="🩺" label="Service"
                                    value={data.serviceType.replace(/_/g, ' ')}
                                />
                            )}
                        </div>

                        {/* Completion message */}
                        {isDone && (
                            <div style={styles.doneBox}>
                                <p>Your visit is <strong>{cfg.label.toLowerCase()}</strong>. Thank you for using SmartQueue.</p>
                                <button
                                    style={styles.clearBtn}
                                    onClick={() => {
                                        localStorage.removeItem(LS_KEY);
                                        navigate('/');
                                    }}
                                >
                                    Close
                                </button>
                            </div>
                        )}

                        {/* Last updated */}
                        {lastUpdated && (
                            <p style={styles.lastUpdated}>
                                Last updated: {lastUpdated.toLocaleTimeString()} · Auto-refreshes every 15 s
                            </p>
                        )}
                    </>
                )}
            </div>

            {/* ── Instructions ── */}
            {!loading && !error && (
                <div style={styles.tip}>
                    💡 Bookmark this page or keep it open — it automatically tracks your position.
                </div>
            )}
        </div>
    );
}

function InfoTile({ icon, label, value }) {
    return (
        <div style={styles.tile}>
            <span style={styles.tileIcon}>{icon}</span>
            <span style={styles.tileLabel}>{label}</span>
            <span style={styles.tileValue}>{value}</span>
        </div>
    );
}

/* ── Styles ─────────────────────────────────────────────────────────────── */
const styles = {
    page: {
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 60%, #0f172a 100%)',
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        justifyContent: 'flex-start', padding: '32px 16px', fontFamily: "'Inter', sans-serif",
    },
    header: {
        textAlign: 'center', marginBottom: 24,
    },
    logo: {
        fontSize: 28, fontWeight: 800, color: '#f8fafc', letterSpacing: '-0.5px',
    },
    subtitle: {
        fontSize: 13, color: '#94a3b8', marginTop: 4, letterSpacing: '0.05em', textTransform: 'uppercase',
    },
    card: {
        background: 'rgba(30,41,59,0.9)', backdropFilter: 'blur(12px)',
        border: '1px solid rgba(99,102,241,0.2)', borderRadius: 20,
        padding: '36px 28px', width: '100%', maxWidth: 460,
        boxShadow: '0 25px 60px rgba(0,0,0,0.5)',
    },
    center: {
        display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '32px 0',
    },
    spinner: {
        width: 44, height: 44, border: '4px solid rgba(99,102,241,0.2)',
        borderTop: '4px solid #6366f1', borderRadius: '50%',
        animation: 'spin 0.9s linear infinite',
    },
    loadingText: { color: '#94a3b8', marginTop: 16, fontSize: 14 },
    statusBadge: {
        display: 'flex', alignItems: 'center', gap: 10,
        border: '2px solid', borderRadius: 12, padding: '10px 16px',
        marginBottom: 20, position: 'relative',
    },
    statusIcon: { fontSize: 22 },
    statusLabel: { fontWeight: 700, fontSize: 16, letterSpacing: '-0.2px' },
    pulseDot: {
        width: 10, height: 10, borderRadius: '50%', marginLeft: 'auto',
        animation: 'pulse 1.5s ease-in-out infinite',
    },
    tokenNumber: {
        fontSize: 52, fontWeight: 900, textAlign: 'center', color: '#f8fafc',
        letterSpacing: '-2px', marginBottom: 28, lineHeight: 1,
    },
    grid: {
        display: 'grid', gridTemplateColumns: '1fr 1fr',
        gap: 12, marginBottom: 20,
    },
    tile: {
        background: 'rgba(15,23,42,0.6)', borderRadius: 12, padding: '12px 14px',
        display: 'flex', flexDirection: 'column', gap: 4,
        border: '1px solid rgba(99,102,241,0.1)',
    },
    tileIcon: { fontSize: 18 },
    tileLabel: { fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.06em' },
    tileValue: { fontSize: 15, fontWeight: 700, color: '#f1f5f9' },
    doneBox: {
        textAlign: 'center', color: '#cbd5e1', fontSize: 14,
        padding: '16px', background: 'rgba(15,23,42,0.4)', borderRadius: 12, marginTop: 8,
    },
    clearBtn: {
        marginTop: 12, padding: '8px 24px', background: '#6366f1',
        color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer',
        fontWeight: 600, fontSize: 13,
    },
    retryBtn: {
        marginTop: 12, padding: '8px 20px', background: '#6366f1',
        color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600,
    },
    errorBox: {
        textAlign: 'center', padding: '24px 0',
    },
    lastUpdated: {
        textAlign: 'center', fontSize: 11, color: '#475569', marginTop: 16,
    },
    tip: {
        marginTop: 20, maxWidth: 460, textAlign: 'center',
        fontSize: 13, color: '#64748b', lineHeight: 1.5,
    },
};

/* Inject keyframes once */
if (!document.getElementById('sq-patient-styles')) {
    const s = document.createElement('style');
    s.id = 'sq-patient-styles';
    s.textContent = `
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800;900&display=swap');
        @keyframes spin  { to { transform: rotate(360deg); } }
        @keyframes pulse { 0%,100% { opacity:1; transform:scale(1); } 50% { opacity:0.4; transform:scale(0.85); } }
    `;
    document.head.appendChild(s);
}
