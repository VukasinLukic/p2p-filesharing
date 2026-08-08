import { useSyncExternalStore } from 'react'
import { getPeerApiBase, subscribePeerApiBase } from '../settings'

/** Re-renders whatever reads the peer API address whenever the network settings modal changes it. */
export function usePeerApiBase() {
  return useSyncExternalStore(subscribePeerApiBase, getPeerApiBase)
}
