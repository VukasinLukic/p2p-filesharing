import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import { usePeerApiBase } from '../hooks/usePeerApiBase'
import { DEFAULT_API_BASE, resetPeerApiBase, setPeerApiBase } from '../settings'

const PRESETS = [
  { label: 'Peer A · 7001', value: '7001' },
  { label: 'Peer B · 7002', value: '7002' },
]

export default function NetworkSettingsModal({ onClose }) {
  const apiBase = usePeerApiBase()
  const [draft, setDraft] = useState(apiBase)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [status, setStatus] = useState(null)
  const [statusError, setStatusError] = useState(null)
  const [checking, setChecking] = useState(false)

  const checkConnection = useCallback(async ({ reconnect = false } = {}) => {
    setChecking(true)
    setStatusError(null)
    try {
      // /tracker/reconnect returns the same payload as /status, after forcing a re-register.
      const data = reconnect ? await api.reconnectTracker() : await api.status()
      setStatus(data)
      setNotice(
        data.connectedToTracker
          ? 'Peer je povezan sa trackerom.'
          : 'Peer radi, ali tracker nije dostupan — proveri da li je tracker pokrenut.',
      )
    } catch (err) {
      setStatus(null)
      setStatusError(err.message)
      setNotice(null)
    } finally {
      setChecking(false)
    }
  }, [])

  // Re-check whenever the target peer changes (mount included), so the panel never shows
  // status belonging to the previously selected peer.
  useEffect(() => {
    setDraft(apiBase)
    checkConnection()
  }, [apiBase, checkConnection])

  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  function handleSave(e) {
    e.preventDefault()
    const result = setPeerApiBase(draft)
    if (result.error) {
      setError(result.error)
      setNotice(null)
      return
    }
    setError(null)
    setNotice(`Sačuvano: ${result.base}`)
  }

  function handleReset() {
    resetPeerApiBase()
    setError(null)
    setNotice('Vraćeno na podrazumevanu adresu.')
  }

  const online = Boolean(status?.connectedToTracker)

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="glass-panel glass-panel--strong modal-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="Podešavanja mreže"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-head">
          <div>
            <h2 className="section-title">Podešavanja mreže</h2>
            <p className="section-sub">
              GUI komunicira samo sa <em>lokalnim</em> peer-om; peer dalje priča sa trackerom.
            </p>
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Zatvori">
            ✕
          </button>
        </div>

        <div className="settings-block">
          <h3 className="settings-label">Trenutni peer</h3>
          <dl className="settings-grid">
            <dt>API adresa</dt>
            <dd>
              <code>{apiBase}</code>
            </dd>
            <dt>Peer ID</dt>
            <dd>
              <code>{status?.peerId ?? '—'}</code>
            </dd>
            <dt>TCP port (transfer)</dt>
            <dd>
              <code>{status?.tcpPort ?? '—'}</code>
            </dd>
            <dt>Tracker</dt>
            <dd>
              <code>{status?.trackerUrl ?? '—'}</code>
            </dd>
            <dt>Deljeni folder</dt>
            <dd>
              <code className="settings-path">{status?.sharedDir ?? '—'}</code>
            </dd>
          </dl>
        </div>

        <form className="settings-block" onSubmit={handleSave}>
          <h3 className="settings-label">Promeni peer (čuva se u browseru)</h3>
          <div className="search-row">
            <input
              className="input"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="7002  ·  192.168.0.15:7001  ·  http://localhost:7002/api"
              aria-label="Adresa lokalnog peer-a"
            />
            <button className="btn" type="submit">
              Sačuvaj
            </button>
          </div>
          <div className="settings-presets">
            {PRESETS.map((p) => (
              <button
                key={p.value}
                type="button"
                className="chip-btn"
                onClick={() => setDraft(p.value)}
              >
                {p.label}
              </button>
            ))}
            <button type="button" className="chip-btn" onClick={handleReset}>
              Podrazumevano ({DEFAULT_API_BASE})
            </button>
          </div>
          {error && <p className="error-text">{error}</p>}
        </form>

        <div className="settings-block">
          <h3 className="settings-label">Veza sa trackerom</h3>
          <div className="settings-connection">
            <span className={`status-dot ${online ? 'online' : ''}`} />
            <span className="status-text">
              {checking
                ? 'Proveravam…'
                : statusError
                  ? 'Peer nije dostupan'
                  : online
                    ? 'Povezan sa trackerom'
                    : 'Peer radi, tracker nedostupan'}
            </span>
          </div>
          {statusError && <p className="error-text">{statusError}</p>}
          {!statusError && notice && <p className="settings-notice">{notice}</p>}
          <div className="settings-presets">
            <button
              type="button"
              className="btn btn--small"
              disabled={checking}
              onClick={() => checkConnection({ reconnect: true })}
            >
              {checking ? 'Osvežavam…' : 'Osveži konekciju'}
            </button>
            <button
              type="button"
              className="chip-btn"
              disabled={checking}
              onClick={() => checkConnection()}
            >
              Samo proveri status
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
