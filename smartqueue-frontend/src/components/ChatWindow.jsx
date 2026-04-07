import { useState, useRef, useEffect } from 'react';
import { sendChatMessage } from '../services/api';

const SUGGESTIONS = [
  'I need Aadhaar update',
  'PAN card correction',
  'Passport renewal',
  'I am a senior citizen, need help',
  'Driving license renewal',
];

export default function ChatWindow({ officeId = 1, onTokenGenerated }) {
  const [messages, setMessages] = useState([
    {
      from: 'bot',
      text: 'Hello! I\'m the SmartQueue assistant. Tell me what service you need today, and I\'ll get you a token right away.',
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const addMsg = (from, text) =>
    setMessages(prev => [...prev, { from, text }]);

  const send = async (text) => {
    const msg = (text || input).trim();
    if (!msg || loading) return;
    setInput('');
    addMsg('user', msg);
    setLoading(true);

    try {
      const res = await sendChatMessage(msg, officeId);
      const data = res.data;

      addMsg('bot', data.botMessage);

      if (data.tokenGenerated && data.tokenData && onTokenGenerated) {
        onTokenGenerated(data.tokenData);
      }
    } catch (err) {
      console.error('Chat error:', err);
      addMsg('bot', 'Sorry, something went wrong. Please try again or approach the help desk.');
    }

    setLoading(false);
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
  };

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', height: '100%',
      fontFamily: 'sans-serif'
    }}>

      <div style={{
        flex: 1, overflowY: 'auto', padding: '16px',
        display: 'flex', flexDirection: 'column', gap: '10px'
      }}>
        {messages.map((m, i) => (
          <div key={i} style={{
            alignSelf: m.from === 'user' ? 'flex-end' : 'flex-start',
            maxWidth: '82%',
          }}>
            {m.from === 'bot' && (
              <div style={{
                fontSize: '11px', color: '#888',
                marginBottom: '3px', paddingLeft: '4px'
              }}>
                Assistant
              </div>
            )}
            <div style={{
              background: m.from === 'user' ? '#0F6E56' : '#f0f0f0',
              color: m.from === 'user' ? '#fff' : '#111',
              borderRadius: m.from === 'user'
                ? '16px 16px 4px 16px' : '16px 16px 16px 4px',
              padding: '10px 14px',
              fontSize: '14px',
              lineHeight: '1.5',
            }}>
              {m.text}
            </div>
          </div>
        ))}

        {loading && (
          <div style={{
            alignSelf: 'flex-start', display: 'flex',
            gap: '4px', padding: '10px 14px',
            background: '#f0f0f0', borderRadius: '16px 16px 16px 4px'
          }}>
            {[0, 1, 2].map(i => (
              <div key={i} style={{
                width: '6px', height: '6px', borderRadius: '50%',
                background: '#999',
                animation: `bounce 1s ${i * 0.2}s infinite`,
              }} />
            ))}
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {messages.length <= 2 && (
        <div style={{
          padding: '0 16px 8px',
          display: 'flex', gap: '6px', flexWrap: 'wrap'
        }}>
          {SUGGESTIONS.map((s, i) => (
            <button key={i} onClick={() => send(s)}
              style={{
                fontSize: '12px', padding: '5px 10px',
                borderRadius: '20px', border: '1px solid #ddd',
                background: '#fff', cursor: 'pointer', color: '#555'
              }}>
              {s}
            </button>
          ))}
        </div>
      )}

      <div style={{
        display: 'flex', gap: '8px', padding: '12px',
        borderTop: '1px solid #eee'
      }}>
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKey}
          placeholder="Describe your service need..."
          disabled={loading}
          style={{
            flex: 1, padding: '10px 14px', borderRadius: '10px',
            border: '1px solid #ddd', fontSize: '14px',
            opacity: loading ? 0.6 : 1
          }}
        />
        <button
          onClick={() => send()}
          disabled={loading || !input.trim()}
          style={{
            padding: '10px 18px', borderRadius: '10px',
            border: 'none', background: loading ? '#ccc' : '#0F6E56',
            color: '#fff', cursor: loading ? 'not-allowed' : 'pointer',
            fontSize: '14px', fontWeight: '500'
          }}>
          Send
        </button>
      </div>

      <style>{`
        @keyframes bounce {
          0%,80%,100% { transform: translateY(0); }
          40% { transform: translateY(-6px); }
        }
      `}</style>
    </div>
  );
}