// The GUI talks exclusively to the local Peer Node's REST API (never to the tracker directly).
// Each peer instance runs its HTTP API on its own port; the address is resolved per request from
// settings.js, so the "Podesavanja mreze" modal can repoint the GUI at another peer live.
import { getPeerApiBase } from './settings'

async function request(path, options = {}) {
  let res
  try {
    res = await fetch(`${getPeerApiBase()}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      signal: AbortSignal.timeout(8000),
      ...options,
    })
  } catch (err) {
    throw new Error(err.name === 'TimeoutError' ? 'Peer node not responding (timeout)' : 'Peer node unreachable')
  }
  if (!res.ok) {
    let message = `HTTP ${res.status}`
    try {
      const body = await res.json()
      if (body?.error) message = body.error
    } catch {
      // ignore, use default message
    }
    throw new Error(message)
  }
  return res.json()
}

export const api = {
  search: (query) => request(`/search?q=${encodeURIComponent(query)}`),
  library: () => request('/library'),
  status: () => request('/status'),
  downloads: () => request('/downloads'),
  startDownload: (fileHash, fileName, size) =>
    request('/downloads', {
      method: 'POST',
      body: JSON.stringify({ fileHash, fileName, size }),
    }),
  /** Forces an immediate re-register/announce instead of waiting for the next 10s heartbeat. */
  reconnectTracker: () => request('/tracker/reconnect', { method: 'POST' }),
  chunks: (fileHash) => request(`/files/${encodeURIComponent(fileHash)}/chunks`),
}
