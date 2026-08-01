import { api, PEER_HTTP_PORT } from '../api'
import { usePolling } from '../hooks/usePolling'

export default function StatusHeader() {
  const { data: status } = usePolling(api.status, 1000, [])
  const online = Boolean(status?.connectedToTracker)

  return (
    <header className="glass-panel status-header">
      <div>
        <h1 className="brand-title">
          <span className="sparkle">✦</span> P2P Share <span className="sparkle">✦</span>
        </h1>
        <p className="brand-sub">Hybrid Peer-to-Peer / Tracker Discovery / RMT Projekat</p>
      </div>
      <div className="status-dot-group">
        <span className={`status-dot ${online ? 'online' : ''}`} />
        <div>
          <div>{online ? 'Connected to tracker' : 'Disconnected'}</div>
          <div className="status-meta">
            peer <code>{status?.peerId ? status.peerId.slice(0, 8) : '—'}</code> · tcp{' '}
            <code>{status?.tcpPort ?? '—'}</code> · api <code>{PEER_HTTP_PORT}</code>
          </div>
        </div>
      </div>
    </header>
  )
}
