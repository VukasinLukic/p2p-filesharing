import { useEffect, useRef, useState } from 'react'

/** Polls `fetcher` on an interval and exposes the latest result/error. Restarts when `deps` change. */
export function usePolling(fetcher, intervalMs, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  useEffect(() => {
    let cancelled = false
    let timer

    async function tick() {
      try {
        const result = await fetcherRef.current()
        if (!cancelled) {
          setData(result)
          setError(null)
        }
      } catch (e) {
        if (!cancelled) setError(e)
      } finally {
        if (!cancelled) timer = setTimeout(tick, intervalMs)
      }
    }

    tick()
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { data, error }
}
