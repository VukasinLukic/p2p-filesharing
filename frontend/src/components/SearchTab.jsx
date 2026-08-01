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
                    {r.peerCount} peer{r.peerCount === 1 ? '' : '-a'}
                  </span>
                  {r.alreadyOwned && <span className="badge owned">already owned</span>}
                </div>
              </div>
              <button
                className="btn btn--small"
                disabled={r.alreadyOwned || startingHash === r.fileHash}
                onClick={() => handleDownload(r)}
              >
                {r.alreadyOwned ? 'Owned' : startingHash === r.fileHash ? 'Pokrećem...' : 'Download'}
              </button>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
