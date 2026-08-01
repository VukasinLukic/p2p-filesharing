package rs.rmt.peer.state;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared mutable status flags read by the local REST API and updated by the heartbeat loop. */
public final class PeerState {
    public volatile String peerId;
    public final AtomicBoolean connectedToTracker = new AtomicBoolean(false);
    /** False right after (re-)registration until announceFiles() succeeds at least once for this peerId. */
    public final AtomicBoolean filesAnnounced = new AtomicBoolean(false);
}
