// The GUI talks exclusively to the local Peer Node's REST API (never to the tracker directly).
// Each peer instance runs its HTTP API on its own port; the address is resolved per request from
// settings.js, so the "Podesavanja mreze" modal can repoint the GUI at another peer live.
import { getPeerApiBase } from './settings'

async function request(path, options = {}) {
  const method = options.method ?? 'GET'
  const url = `${getPeerApiBase()}${path}`
  const isPoll = method === 'GET' && ['/status', '/downloads', '/downloads/files', '/library'].includes(path)
  if (!isPoll) console.info('[P2P GUI] API request', method, url)
  let res
  try {
    res = await fetch(url, {
      headers: { 'Content-Type': 'application/json' },
      signal: AbortSignal.timeout(8000),
      ...options,
    })
  } catch (err) {
    const message = err.name === 'TimeoutError' ? 'Peer node not responding (timeout)' : 'Peer node unreachable'
    console.error('[P2P GUI] API failed', method, url, message, err)
    throw new Error(message)
  }
  if (!res.ok) {
    let message = `HTTP ${res.status}`
    try {
      const body = await res.json()
      if (body?.error) message = body.error
    } catch {
      // ignore, use default message
    }
    console.error('[P2P GUI] API returned error', method, url, res.status, message)
    throw new Error(message)
  }
  if (!isPoll) console.info('[P2P GUI] API response', method, url, res.status)
  return res.json()
}

export const api = {
  search: (query) => request(`/search?q=${encodeURIComponent(query)}`),
  library: () => request('/library'),
  status: () => request('/status'),
  downloads: () => request('/downloads'),
  downloadedFiles: () => request('/downloads/files'),
  openDownloadedFile: (fileName) =>
    request('/downloads/open', {
      method: 'POST',
      body: JSON.stringify({ fileName }),
    }),
  uploadToLibrary: (file) =>
    request('/library/upload', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/octet-stream',
        'X-File-Name': encodeURIComponent(file.name),
      },
      body: file,
    }),
  startDownload: (fileHash, fileName, size) =>
    request('/downloads', {
      method: 'POST',
      body: JSON.stringify({ fileHash, fileName, size }),
    }),
  /** Forces an immediate re-register/announce instead of waiting for the next 10s heartbeat. */
  reconnectTracker: () => request('/tracker/reconnect', { method: 'POST' }),
  chunks: (fileHash) => request(`/files/${encodeURIComponent(fileHash)}/chunks`),
}
