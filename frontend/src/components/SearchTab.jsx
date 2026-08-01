import { useState } from 'react'
import { api } from '../api'
import { formatBytes } from '../utils'

export default function SearchTab({ onDownloadStarted }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [startingHash, setStartingHash] = useState(null)

  async function runSearch(e) {
    e?.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const data = await api.search(query)
      setResults(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleDownload(result) {
    setStartingHash(result.fileHash)
    try {
      await api.startDownload(result.fileHash, result.fileName, result.size)
      onDownloadStarted?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setStartingHash(null)
    }
  }

  const hasOwnedResult = results.some((r) => r.alreadyOwned)

  return (
    <section className="glass-panel">
      <h2 className="section-title">Pretraga mreže</h2>
      <p className="section-sub">Pretraži fajlove koje dele drugi peer-ovi u mreži.</p>

      <form className="search-row" onSubmit={runSearch}>
        <input
          className="input"
          placeholder="naziv fajla..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="btn" type="submit" disabled={loading}>
          {loading ? 'Traži...' : 'Pretraži'}
        </button>
      </form>

      {error && <p className="error-text">{error}</p>}

      {results.length === 0 && !loading ? (
        <div className="empty-state">Nema rezultata — pokušaj drugu pretragu.</div>
      ) : (
        <div className="result-list">
          {results.map((r) => (
            <div className="result-row" key={r.fileHash}>
              <div>
                <div className="file-name">{r.fileName}</div>
                <div className="file-meta">
                  <span>{formatBytes(r.size)}</span>
                  <span>
                    {r.peerCount} {r.peerCount === 1 ? 'izvor' : 'izvora'}
                  </span>
                  {r.alreadyOwned && <span className="badge owned">već u biblioteci</span>}
                </div>
              </div>
              <button
                className="btn btn--small"
                disabled={r.alreadyOwned || startingHash === r.fileHash}
                onClick={() => handleDownload(r)}
              >
                {r.alreadyOwned ? 'U biblioteci' : startingHash === r.fileHash ? 'Pokrećem...' : 'Preuzmi'}
              </button>
            </div>
          ))}
        </div>
      )}

      {hasOwnedResult && (
        <div className="hint-banner">
          <span className="hint-banner__icon">ℹ️</span>
          <span>
            Fajl obeležen sa <strong>„već u biblioteci"</strong> znači da ga <em>ovaj</em> peer (na kom
            gledaš GUI) već ima — bilo zato što ga deli iz svog `shared` foldera, bilo zato što ga je
            ranije preuzeo. To je očekivano ako testiraš na jednom peer-u. Da vidiš pravo preuzimanje
            preko mreže, pokreni <strong>drugi</strong> peer (drugi port) koji fajl <em>nema</em>, i otvori
            GUI za njega u novom tabu — npr. <code>?port=7002</code>. Detaljno uputstvo: DEMO_SCRIPT.md.
          </span>
        </div>
      )}
    </section>
  )
}
