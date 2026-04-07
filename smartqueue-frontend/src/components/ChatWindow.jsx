import { useState } from 'react';
import { generateToken } from '../services/api';

const SERVICE_KEYWORDS = {
  aadhaar: 'AADHAAR_UPDATE',
  pan: 'PAN_CARD',
  passport: 'PASSPORT',
  driving: 'DRIVING_LICENSE',
  income: 'INCOME_CERTIFICATE',
};

const PRIORITY_KEYWORDS = {
  senior: 'SENIOR',
  elderly: 'SENIOR',
  emergency: 'EMERGENCY',
  urgent: 'EMERGENCY',
};

function detectIntent(text) {
  const lower = text.toLowerCase();
  let serviceType = 'OTHER';
  let priorityFlag = 'NORMAL';

  for (const [k, v] of Object.entries(SERVICE_KEYWORDS)) {
    if (lower.includes(k)) { serviceType = v; break; }
  }
  for (const [k, v] of Object.entries(PRIORITY_KEYWORDS)) {
    if (lower.includes(k)) { priorityFlag = v; break; }
  }
  return { serviceType, priorityFlag };
}

export default function ChatWindow({ officeId = 1, onTokenGenerated }) {
  const [messages, setMessages] = useState([
    { from: 'bot', text: 'Hello! Tell me what service you need today.' }
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);

  const addMsg = (from, text) =>
    setMessages(prev => [...prev, { from, text }]);

  const send = async () => {
    const text = input.trim();
    if (!text) return;
    setInput('');
    addMsg('user', text);
    setLoading(true);

    const { serviceType, priorityFlag } = detectIntent(text);

    if (serviceType === 'OTHER') {
      addMsg('bot',
        'I didn\'t catch the service type. Try: "I need Aadhaar update", ' +
        '"PAN card", "passport", "driving license", or "income certificate".'
      );
      setLoading(false);
      return;
    }

    try {
      const res = await generateToken(serviceType, priorityFlag, officeId);
      const { tokenNumber, positionInQueue, estimatedWaitMinutes } = res.data;

      addMsg('bot',
        `Your token is ${tokenNumber}. You are #${positionInQueue} in queue. ` +
        `Estimated wait: ~${estimatedWaitMinutes} minutes.`
      );

      if (onTokenGenerated) onTokenGenerated(res.data);
    } catch {
      addMsg('bot', 'Something went wrong. Please try again.');
    }
    setLoading(false);
  };

  const handleKey = (e) => { if (e.key === 'Enter') send(); };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{
        flex: 1, overflowY: 'auto', padding: '12px',
        display: 'flex', flexDirection: 'column', gap: '8px'
      }}>
        {messages.map((m, i) => (
          <div key={i} style={{
            alignSelf: m.from === 'user' ? 'flex-end' : 'flex-start',
            background: m.from === 'user' ? '#1D9E75' : '#f0f0f0',
            color: m.from === 'user' ? '#fff' : '#222',
            borderRadius: '12px',
            padding: '8px 14px',
            maxWidth: '80%',
            fontSize: '14px',
            lineHeight: '1.5',
          }}>
            {m.text}
          </div>
        ))}
        {loading && (
          <div style={{ alignSelf: 'flex-start', color: '#888', fontSize: '13px' }}>
            Typing...
          </div>
        )}
      </div>
      <div style={{ display: 'flex', gap: '8px', padding: '12px', borderTop: '1px solid #eee' }}>
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKey}
          placeholder="Type your service request..."
          style={{
            flex: 1, padding: '10px 14px', borderRadius: '8px',
            border: '1px solid #ddd', fontSize: '14px'
          }}
        />
        <button
          onClick={send}
          style={{
            padding: '10px 20px', borderRadius: '8px', border: 'none',
            background: '#1D9E75', color: '#fff', cursor: 'pointer',
            fontSize: '14px', fontWeight: '500'
          }}
        >
          Send
        </button>
      </div>
    </div>
  );
}