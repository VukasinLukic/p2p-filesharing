// Where the GUI's local Peer Node lives. Resolved once at startup and changeable at runtime
// from the "Podesavanja mreze" modal, so a demo never needs a rebuild or a hand-edited URL.
//
// Precedence: ?api=<url> / ?port=<n> in the URL  >  localStorage  >  http://localhost:7001/api
// A URL parameter wins on purpose: START-*.bat opens the browser with ?port=7002 for Peer B, and
// that explicit intent must not be overridden by whatever was saved in an earlier session.

const STORAGE_KEY = 'p2p.peerApiBase'

export const DEFAULT_PEER_PORT = '7001'
export const DEFAULT_API_BASE = `http://localhost:${DEFAULT_PEER_PORT}/api`

/**
 * Accepts what a person would actually type: "7002", "localhost:7002", "192.168.0.15:7001",
 * "http://192.168.0.15:7001" or a full ".../api" URL.
 * Returns { base } on success or { error } with a message to show in the modal.
 */
export function normalizeApiBase(raw) {
  const input = String(raw ?? '').trim()
  if (!input) return { error: 'Unesi port ili adresu peer-a.' }

  if (/^\d+$/.test(input)) {
    const port = Number(input)
    if (port < 1 || port > 65535) return { error: 'Port mora biti između 1 i 65535.' }
    return { base: `http://localhost:${port}/api` }
  }

  const withScheme = /^https?:\/\//i.test(input) ? input : `http://${input}`
  let url
  try {
    url = new URL(withScheme)
  } catch {
    return { error: 'Neispravna adresa. Primer: 7002 ili 192.168.0.15:7001' }
  }
  if (!url.port) {
    return { error: 'Nedostaje port. Primer: 192.168.0.15:7001' }
  }

  const path = url.pathname.replace(/\/+$/, '')
  return { base: `${url.origin}${path === '' ? '/api' : path}` }
}

function readInitialBase() {
  const params = new URLSearchParams(window.location.search)
  const fromUrl = params.get('api') || params.get('port')
  if (fromUrl) {
    const { base } = normalizeApiBase(fromUrl)
    if (base) {
      console.info('[P2P GUI] API selected from URL:', base, 'page:', window.location.href)
      return base
    }
  }
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const { base } = normalizeApiBase(stored)
      if (base) {
        console.info('[P2P GUI] API selected from browser storage:', base)
        return base
      }
    }
  } catch {
    // localStorage can throw in private-mode / blocked-cookies browsers - fall through to default.
  }
  console.info('[P2P GUI] API selected from default:', DEFAULT_API_BASE)
  return DEFAULT_API_BASE
}

let currentBase = readInitialBase()
const listeners = new Set()

export function getPeerApiBase() {
  return currentBase
}

/** Persists the new base and notifies React; returns the same shape as normalizeApiBase(). */
export function setPeerApiBase(raw) {
  const result = normalizeApiBase(raw)
  if (result.error) {
    console.warn('[P2P GUI] rejected API address:', raw, result.error)
    return result
  }

  currentBase = result.base
  console.info('[P2P GUI] API address changed:', currentBase)
  try {
    window.localStorage.setItem(STORAGE_KEY, result.base)
  } catch {
    // Not fatal: the change still applies to this tab, it just won't survive a reload.
  }
  listeners.forEach((fn) => fn())
  return result
}

export function resetPeerApiBase() {
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore
  }
  currentBase = DEFAULT_API_BASE
  console.info('[P2P GUI] API address reset:', currentBase)
  listeners.forEach((fn) => fn())
  return { base: currentBase }
}

export function subscribePeerApiBase(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** "http://localhost:7002/api" -> "localhost:7002", for compact display in the header. */
export function hostLabel(base) {
  try {
    return new URL(base).host
  } catch {
    return base
  }
}
