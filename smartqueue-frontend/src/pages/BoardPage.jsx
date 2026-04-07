import QueueBoard from '../components/QueueBoard';

export default function BoardPage() {
    return (
        <div style={{
            minHeight: '100vh', background: '#fff',
            display: 'flex', alignItems: 'center',
            justifyContent: 'center'
        }}>
            <div style={{ width: '100%', maxWidth: '480px' }}>
                <QueueBoard officeId={1} />
            </div>
        </div>
    );
}