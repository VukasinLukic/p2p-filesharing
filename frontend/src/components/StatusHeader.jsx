import { useState } from 'react'
import { api } from '../api'
import { usePeerApiBase } from '../hooks/usePeerApiBase'
import { usePolling } from '../hooks/usePolling'
import { hostLabel } from '../settings'
import NetworkSettingsModal from './NetworkSettingsModal'

export default function StatusHeader() {
  const apiBase = usePeerApiBase()
  const { data: status, error } = usePolling(api.status, 1000, [apiBase])
  const [settingsOpen, setSettingsOpen] = useState(false)
  const online = Boolean(status?.connectedToTracker)
  const waitingForPeer = Boolean(error) && !status
  const statusLabel = waitingForPeer
    ? 'Pokretanje lokalnog peer-a…'
    : online
      ? 'Povezan sa trackerom'
      : status
        ? 'Peer radi — tracker nije dostupan'
        : 'Nije povezan'

  return (
    <>
      <header className="glass-panel status-header">
        <div>
          <h1 className="brand-title">P2P Share</h1>
          <p className="brand-sub">Hybrid Peer-to-Peer · Tracker Discovery · RMT Projekat</p>
        </div>
        <div className="status-dot-group">
          <span className={`status-dot ${online ? 'online' : waitingForPeer ? 'connecting' : ''}`} />
          <div>
            <div className="status-text">{statusLabel}</div>
            <div className="status-meta">
              peer <code>{status?.peerId ? status.peerId.slice(0, 8) : '—'}</code> · tcp{' '}
              <code>{status?.tcpPort ?? '—'}</code> · api <code>{hostLabel(apiBase)}</code>
            </div>
          </div>
          <button
            type="button"
            className="icon-btn"
            onClick={() => setSettingsOpen(true)}
            title="Podešavanja mreže"
            aria-label="Podešavanja mreže"
          >
            <GearIcon />
          </button>
        </div>
      </header>

      {settingsOpen && <NetworkSettingsModal onClose={() => setSettingsOpen(false)} />}
    </>
  )
}

function GearIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0 .33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  )
}
