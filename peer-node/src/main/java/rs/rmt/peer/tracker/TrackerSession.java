package rs.rmt.peer.tracker;

import rs.rmt.peer.config.PeerConfig;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.state.PeerState;

/**
 * Owns this peer's relationship with the tracker: registration, file announcement and the
 * heartbeat loop, plus the manual "reconnect" the GUI can trigger from network settings.
 *
 * All methods are synchronized: the heartbeat thread and an HTTP request thread can otherwise
 * register concurrently and leave the peer with two identities on the tracker.
 */
public final class TrackerSession {
    private final PeerConfig config;
    private final PeerState state;
    private final TrackerClient client;
    private final LibraryService library;

    public TrackerSession(PeerConfig config, PeerState state, TrackerClient client, LibraryService library) {
        this.config = config;
        this.state = state;
        this.client = client;
        this.library = library;
    }

    /** Registers (only if we don't have an id yet) and makes sure the file list has been announced. */
    public synchronized boolean registerAndAnnounce() {
        try {
            if (state.peerId == null) {
                TrackerClient.RegisterResult reg = client.register(null, config.tcpPort);
                state.peerId = reg.peerId();
                System.out.println("[Register] peerId=" + state.peerId + " host=" + reg.host());
            }
            boolean announced = client.announceFiles(state.peerId, library.allFiles());
            state.filesAnnounced.set(announced);
            state.connectedToTracker.set(announced);
            if (!announced) {
                // The tracker no longer knows this peerId (it restarted) - drop it so the next
                // attempt registers from scratch instead of re-announcing into the void.
                state.peerId = null;
            }
            return announced;
        } catch (Exception e) {
            state.connectedToTracker.set(false);
            System.err.println("[Register/Announce] failed, will retry on next heartbeat: " + e.getMessage());
            return false;
        }
    }

    /** One heartbeat tick: re-registers automatically if the tracker has forgotten us. */
    public synchronized void heartbeat() {
        try {
            if (state.peerId == null || !state.filesAnnounced.get()) {
                registerAndAnnounce();
                return;
            }
            if (client.heartbeat(state.peerId)) {
                state.connectedToTracker.set(true);
                return;
            }
            System.out.println("[Heartbeat] tracker forgot us (restarted?) - re-registering");
            state.peerId = null;
            state.filesAnnounced.set(false);
            registerAndAnnounce();
        } catch (Exception e) {
            state.connectedToTracker.set(false);
            System.err.println("[Heartbeat] tracker unreachable: " + e.getMessage());
        }
    }

    /**
     * Re-registers and re-announces right now, without waiting for the next heartbeat.
     * Keeps the existing peerId when there is one so the tracker updates our entry in place
     * instead of leaving a stale duplicate around until its TTL eviction.
     */
    public synchronized boolean forceReconnect() {
        try {
            TrackerClient.RegisterResult reg = client.register(state.peerId, config.tcpPort);
            state.peerId = reg.peerId();
            System.out.println("[Reconnect] peerId=" + state.peerId + " host=" + reg.host());
        } catch (Exception e) {
            state.connectedToTracker.set(false);
            state.filesAnnounced.set(false);
            System.err.println("[Reconnect] register failed: " + e.getMessage());
            return false;
        }
        // register() replaces the tracker-side entry, wiping its file list - re-announce always.
        state.filesAnnounced.set(false);
        return registerAndAnnounce();
    }
}
