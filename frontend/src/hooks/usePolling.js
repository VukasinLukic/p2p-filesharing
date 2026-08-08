import { useEffect, useRef, useState } from 'react'

/** Polls `fetcher` on an interval and exposes the latest result/error. Restarts when `deps` change. */
export function usePolling(fetcher, intervalMs, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const fetcherRef = useRef(fetcher)
  const failedRef = useRef(false)
  fetcherRef.current = fetcher

  useEffect(() => {
    let cancelled = false
    let timer

    async function tick() {
      try {
        const result = await fetcherRef.current()
        if (!cancelled) {
          if (failedRef.current) console.info('[P2P GUI] polling recovered')
          failedRef.current = false
          setData(result)
          setError(null)
        }
      } catch (e) {
        if (!cancelled) {
          if (!failedRef.current) {
            console.warn('[P2P GUI] polling failed; retrying every 5 seconds:', e.message)
          }
          failedRef.current = true
          setError(e)
        }
      } finally {
        // When a peer has not started yet, one request per second only floods DevTools with
        // ERR_CONNECTION_REFUSED. A short backoff still notices a late-starting peer quickly.
        if (!cancelled) timer = setTimeout(tick, failedRef.current ? Math.max(intervalMs, 5000) : intervalMs)
      }
    }

    tick()
    return () => {
      cancelled = true
      clearTimeout(timer)
      failedRef.current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { data, error }
}
