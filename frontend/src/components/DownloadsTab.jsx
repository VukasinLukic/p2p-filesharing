import { api } from '../api'
import { usePolling } from '../hooks/usePolling'
import { formatBytes, formatSpeed } from '../utils'

const STATUS_LABEL = {
  IN_PROGRESS: 'In progress',
  VERIFYING: 'Verifying',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
}

export default function DownloadsTab() {
  const { data: downloads } = usePolling(api.downloads, 500, [])
  const list = downloads ?? []

  return (
    <section className="glass-panel">
      <h2 className="section-title">Preuzimanja</h2>
      <p className="section-sub">Live status svih transfera u toku i završenih.</p>

      {list.length === 0 ? (
        <div className="empty-state">Nema aktivnih preuzimanja.</div>
      ) : (
        <div className="result-list">
          {list.map((d) => {
            const pct = Math.round(d.progressPct ?? 0)
            const fillClass =
              d.status === 'COMPLETED' ? 'done' : d.status === 'FAILED' ? 'failed' : ''
            return (
              <div className="download-card" key={d.downloadId}>
                <div className="download-card-top">
                  <span className="file-name">{d.fileName ?? d.downloadId.slice(0, 8)}</span>
                  <span className={`status-badge ${d.status}`}>{STATUS_LABEL[d.status] ?? d.status}</span>
                </div>
                <div className="progress-track">
                  <div className={`progress-fill ${fillClass}`} style={{ width: `${pct}%` }} />
                </div>
                <div className="download-footer">
                  <span>
                    {formatBytes(d.bytesReceived)} / {formatBytes(d.size)} ({pct}%)
                  </span>
                  <span>{d.status === 'IN_PROGRESS' ? formatSpeed(d.speedBytesPerSec) : ''}</span>
                </div>
                {d.errorMessage && <div className="error-text">{d.errorMessage}</div>}
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}
