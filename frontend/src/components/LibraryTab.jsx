import { useRef, useState } from 'react'
import { api } from '../api'
import { usePeerApiBase } from '../hooks/usePeerApiBase'
import { usePolling } from '../hooks/usePolling'
import { formatBytes } from '../utils'

export default function LibraryTab() {
  const apiBase = usePeerApiBase()
  const { data: files } = usePolling(api.library, 2000, [apiBase])
  const list = files ?? []
  const inputRef = useRef(null)
  const [uploading, setUploading] = useState(false)
  const [notice, setNotice] = useState(null)
  const [error, setError] = useState(null)

  async function uploadFile(event) {
    const file = event.target.files?.[0]
    if (!file) return
    setUploading(true)
    setError(null)
    setNotice(null)
    try {
      const result = await api.uploadToLibrary(file)
      setNotice(`Dodat fajl: ${result.fileName}${result.announced ? ' — objavljen mreži.' : ''}`)
      console.info('[P2P GUI] file added to shared library:', result)
    } catch (err) {
      setError(err.message)
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  return (
    <section className="glass-panel">
      <h2 className="section-title">Moja biblioteka</h2>
      <p className="section-sub">Fajlovi dostupni ovom peer-u. Dodati fajl se odmah objavljuje trackeru.</p>

      <div className="library-upload">
        <input ref={inputRef} className="visually-hidden" type="file" onChange={uploadFile} />
        <button className="btn btn--small" type="button" disabled={uploading} onClick={() => inputRef.current?.click()}>
          {uploading ? 'Dodajem…' : 'Dodaj fajl u shared'}
        </button>
        <span className="upload-note">Maksimalna veličina: 512 MB</span>
      </div>
      {notice && <p className="settings-notice">{notice}</p>}
      {error && <p className="error-text">{error}</p>}

      {list.length === 0 ? (
        <div className="empty-state">Biblioteka je prazna.</div>
      ) : (
        <div className="file-list">
          {list.map((f) => (
            <div className="file-row" key={f.fileHash}>
              <div>
                <div className="file-name">{f.fileName}</div>
                <div className="file-meta">
                  <span>{formatBytes(f.size)}</span>
                  <span title={f.fileHash}>{f.fileHash.slice(0, 12)}…</span>
                  {f.shared && <span className="badge owned">shared</span>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
