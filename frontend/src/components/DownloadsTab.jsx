import { useState } from 'react'
import { api } from '../api'
import { usePeerApiBase } from '../hooks/usePeerApiBase'
import { usePolling } from '../hooks/usePolling'
import { formatBytes, formatSpeed } from '../utils'

const STATUS_LABEL = {
  IN_PROGRESS: 'U toku',
  VERIFYING: 'Verifikacija',
  COMPLETED: 'Završeno',
  FAILED: 'Neuspešno',
}

export default function DownloadsTab() {
  const apiBase = usePeerApiBase()
  const { data: downloads } = usePolling(api.downloads, 500, [apiBase])
  const { data: downloadedFiles } = usePolling(api.downloadedFiles, 2000, [apiBase])
  const [opening, setOpening] = useState(null)
  const [openError, setOpenError] = useState(null)
  const list = downloads ?? []
  const saved = downloadedFiles ?? []

  async function openFile(fileName) {
    setOpening(fileName)
    setOpenError(null)
    try {
      await api.openDownloadedFile(fileName)
    } catch (err) {
      setOpenError(err.message)
    } finally {
      setOpening(null)
    }
  }

  return (
    <section className="glass-panel">
      <h2 className="section-title">Preuzimanja</h2>
      <p className="section-sub">Status transfera i fajlovi sačuvani u downloads folderu.</p>

      {list.length === 0 ? (
        <div className="empty-state">Nema aktivnih preuzimanja.</div>
      ) : (
        <div className="result-list">
          {list.map((d) => {
            const pct = Math.round(d.progressPct ?? 0)
            const fillClass = d.status === 'COMPLETED' ? 'done' : d.status === 'FAILED' ? 'failed' : ''
            return (
              <div className="download-card" key={d.downloadId}>
                <div className="download-card-top">
                  <span className="file-name">{d.fileName ?? d.downloadId.slice(0, 8)}</span>
                  <span className={`status-badge ${d.status}`}>{STATUS_LABEL[d.status] ?? d.status}</span>
                </div>
                <div className="progress-track"><div className={`progress-fill ${fillClass}`} style={{ width: `${pct}%` }} /></div>
                <div className="download-footer">
                  <span>{formatBytes(d.bytesReceived)} / {formatBytes(d.size)} ({pct}%)</span>
                  <span>{d.status === 'IN_PROGRESS' ? formatSpeed(d.speedBytesPerSec) : ''}</span>
                </div>
                {d.errorMessage && <div className="error-text">{d.errorMessage}</div>}
              </div>
            )
          })}
        </div>
      )}

      <div className="folder-section">
        <h3 className="settings-label">Sačuvano u downloads folderu</h3>
        {openError && <p className="error-text">{openError}</p>}
        {saved.length === 0 ? (
          <p className="folder-empty">Folder je prazan.</p>
        ) : (
          <div className="file-list">
            {saved.map((file) => (
              <div className="file-row" key={file.fileName}>
                <div>
                  <div className="file-name">{file.fileName}</div>
                  <div className="file-meta">{formatBytes(file.size)}</div>
                </div>
                <button className="chip-btn" type="button" disabled={opening === file.fileName}
                        onClick={() => openFile(file.fileName)}>
                  {opening === file.fileName ? 'Otvaram…' : 'Otvori'}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
