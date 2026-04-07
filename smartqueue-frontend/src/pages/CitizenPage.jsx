import { useState } from 'react';
import ChatWindow from '../components/ChatWindow';
import TokenCard from '../components/TokenCard';
import QueueBoard from '../components/QueueBoard';

export default function CitizenPage() {
    const [myToken, setMyToken] = useState(null);

    return (
        <div style={{
            maxWidth: '900px', margin: '0 auto', padding: '24px',
            fontFamily: 'sans-serif'
        }}>
            <h1 style={{ fontSize: '22px', fontWeight: '500', marginBottom: '24px' }}>
                SmartQueue — Citizen Portal
            </h1>
            <div style={{
                display: 'grid',
                gridTemplateColumns: myToken ? '1fr 1fr' : '1fr',
                gap: '20px', marginBottom: '24px'
            }}>
                <div style={{
                    border: '1px solid #eee', borderRadius: '16px',
                    height: '420px', overflow: 'hidden'
                }}>
                    <ChatWindow officeId={1} onTokenGenerated={setMyToken} />
                </div>
                {myToken && <TokenCard token={myToken} />}
            </div>
            <div style={{ border: '1px solid #eee', borderRadius: '16px', overflow: 'hidden' }}>
                <QueueBoard officeId={1} />
            </div>
        </div>
    );
}