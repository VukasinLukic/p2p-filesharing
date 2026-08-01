// The GUI talks exclusively to the local Peer Node's REST API (never to the tracker directly).
// Each peer instance runs its HTTP API on its own port, so ?port=7002 lets you point a second
// browser tab at a second local peer during a demo.
const params = new URLSearchParams(window.location.search)
const peerPort = params.get('port') || '7001'
export const PEER_HTTP_PORT = peerPort
export const API_BASE = `http://localhost:${peerPort}/api`

async function request(path, options = {}) {
  let res
  try {
    res = await fetch(`${API_BASE}${path}`, {
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
}
