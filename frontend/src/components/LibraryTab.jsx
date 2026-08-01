import { api } from '../api'
import { usePolling } from '../hooks/usePolling'
import { formatBytes } from '../utils'

export default function LibraryTab() {
  const { data: files } = usePolling(api.library, 2000, [])
  const list = files ?? []

  return (
    <section className="glass-panel">
      <h2 className="section-title">Moja biblioteka</h2>
      <p className="section-sub">Fajlovi koje ovaj peer trenutno nudi mreži.</p>

      {list.length === 0 ? (
        <div className="empty-state">Deljeni folder je prazan.</div>
      ) : (
        <div className="file-list">
          {list.map((f) => (
            <div className="file-row" key={f.fileHash}>
              <div>
                <div className="file-name">{f.fileName}</div>
                <div className="file-meta">
                  <span>{formatBytes(f.size)}</span>
                  <span title={f.fileHash}>{f.fileHash.slice(0, 12)}…</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
