import { useEffect, useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';

/**
 * QRModal — shows the Magic Link QR code for a generated token.
 * Uses window.location.origin so the URL works on any IP/hostname,
 * including local Wi-Fi (no hardcoded localhost).
 */
export default function QRModal({ token, onClose }) {
    const overlayRef = useRef(null);

    // Close on Escape key
    useEffect(() => {
        const handler = (e) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', handler);
        return () => window.removeEventListener('keydown', handler);
    }, [onClose]);

    if (!token) return null;

    const magicLink = `${window.location.origin}/status/${token.id}`;

    const handlePrint = () => {
        const w = window.open('', '_blank');
        w.document.write(`
            <html><head><title>SmartQueue Token</title>
            <style>
                body { font-family: sans-serif; text-align:center; padding:40px; }
                h2 { font-size:28px; margin-bottom:4px; }
                p  { color:#555; font-size:16px; }
                .token { font-size:48px; font-weight:900; margin:20px 0; }
                .url   { font-size:13px; color:#6366f1; word-break:break-all; margin-top:16px; }
            </style></head><body>
            <h2>🏥 SmartQueue</h2>
            <p>Scan to track your queue position</p>
            <div class="token">${token.tokenNumber}</div>
            <img src="https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(magicLink)}" />
            <p class="url">${magicLink}</p>
            <p style="margin-top:24px;font-size:14px;">Doctor: <strong>${token.doctorName || 'TBD'}</strong> · Room: <strong>${token.roomNumber || 'TBD'}</strong></p>
            </body></html>
        `);
        w.document.close();
        w.focus();
        w.print();
    };

    return (
        <div
            ref={overlayRef}
            style={styles.overlay}
            onClick={(e) => { if (e.target === overlayRef.current) onClose(); }}
        >
            <div style={styles.modal}>
                {/* Header */}
                <div style={styles.header}>
                    <div>
                        <p style={styles.eyebrow}>Magic Link</p>
                        <h2 style={styles.title}>Patient QR Code</h2>
                    </div>
                    <button style={styles.closeBtn} onClick={onClose} aria-label="Close">✕</button>
                </div>

                {/* Token badge */}
                <div style={styles.tokenBadge}>{token.tokenNumber}</div>

                {/* QR code */}
                <div style={styles.qrWrap}>
                    <QRCodeSVG
                        value={magicLink}
                        size={200}
                        bgColor="#ffffff"
                        fgColor="#1e293b"
                        level="H"
                        includeMargin={true}
                    />
                </div>

                {/* URL */}
                <p style={styles.urlText}>{magicLink}</p>

                {/* Doctor info */}
                <div style={styles.infoRow}>
                    <span style={styles.infoChip}>🏥 {token.doctorName || 'TBD'}</span>
                    {token.roomNumber && <span style={styles.infoChip}>🚪 {token.roomNumber}</span>}
                    <span style={styles.infoChip}>👥 #{token.positionInQueue}</span>
                </div>

                {/* Actions */}
                <div style={styles.actions}>
                    <button style={styles.printBtn} onClick={handlePrint}>🖨️ Print / Reprint</button>
                    <button style={styles.copyBtn} onClick={() => navigator.clipboard?.writeText(magicLink)}>
                        📋 Copy Link
                    </button>
                </div>
            </div>
        </div>
    );
}

const styles = {
    overlay: {
        position: 'fixed', inset: 0,
        background: 'rgba(0,0,0,0.65)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 9999,
    },
    modal: {
        background: '#ffffff', borderRadius: 24, padding: '32px 28px',
        width: '100%', maxWidth: 420,
        boxShadow: '0 32px 64px rgba(0,0,0,0.3)',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16,
    },
    header: {
        width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
    },
    eyebrow: {
        margin: 0, fontSize: 11, letterSpacing: '0.1em', textTransform: 'uppercase', color: '#6366f1',
    },
    title: { margin: '4px 0 0', fontSize: 22, color: '#0f172a' },
    closeBtn: {
        background: '#f1f5f9', border: 'none', borderRadius: 8,
        width: 32, height: 32, cursor: 'pointer', fontSize: 14, color: '#475569',
    },
    tokenBadge: {
        fontSize: 40, fontWeight: 900, color: '#0f172a', letterSpacing: '-2px',
    },
    qrWrap: {
        padding: 12, background: '#f8fafc', borderRadius: 16,
        border: '2px solid #e2e8f0',
        boxShadow: '0 4px 16px rgba(99,102,241,0.08)',
    },
    urlText: {
        fontSize: 11, color: '#6366f1', wordBreak: 'break-all',
        textAlign: 'center', margin: 0, padding: '0 8px',
    },
    infoRow: {
        display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'center',
    },
    infoChip: {
        fontSize: 13, fontWeight: 600, color: '#334155',
        background: '#f1f5f9', padding: '6px 12px', borderRadius: 999,
    },
    actions: {
        display: 'flex', gap: 10, width: '100%',
    },
    printBtn: {
        flex: 1, padding: '12px 0', background: '#0f766e', color: '#fff',
        border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 14, cursor: 'pointer',
    },
    copyBtn: {
        flex: 1, padding: '12px 0', background: '#f1f5f9', color: '#334155',
        border: 'none', borderRadius: 12, fontWeight: 700, fontSize: 14, cursor: 'pointer',
    },
};
