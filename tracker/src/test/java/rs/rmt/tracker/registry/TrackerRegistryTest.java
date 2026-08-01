package rs.rmt.tracker.registry;

import rs.rmt.tracker.model.FileMeta;
import rs.rmt.tracker.model.FileSearchResult;
import rs.rmt.tracker.model.PeerInfo;
import rs.rmt.tracker.model.PeerRef;
import rs.rmt.tracker.testutil.Assert;

import java.util.List;

public class TrackerRegistryTest {

    public void testRegisterGeneratesIdWhenNoneProvided() {
        TrackerRegistry registry = new TrackerRegistry();
        PeerInfo info = registry.register(null, "127.0.0.1", 9001);
        Assert.assertNotNull(info.peerId(), "generated peerId");
        Assert.assertEquals("127.0.0.1", info.host(), "host stored");
        Assert.assertEquals(9001, info.port(), "port stored");
    }

    public void testRegisterKeepsRequestedId() {
        TrackerRegistry registry = new TrackerRegistry();
        PeerInfo info = registry.register("fixed-id", "127.0.0.1", 9001);
        Assert.assertEquals("fixed-id", info.peerId(), "requested peerId honored");
    }

    public void testAnnounceUnknownPeerReturnsFalse() {
        TrackerRegistry registry = new TrackerRegistry();
        boolean ok = registry.announceFiles("ghost", List.of(new FileMeta("h1", "a.txt", 10)));
        Assert.assertFalse(ok, "announcing files for an unregistered peer must fail");
    }

    public void testHeartbeatUnknownPeerReturnsFalse() {
        TrackerRegistry registry = new TrackerRegistry();
        Assert.assertFalse(registry.heartbeat("ghost"), "heartbeat for unregistered peer must fail");
    }

    public void testSearchIsCaseInsensitiveSubstring() {
        TrackerRegistry registry = new TrackerRegistry();
        PeerInfo p = registry.register(null, "127.0.0.1", 9001);
        registry.announceFiles(p.peerId(), List.of(new FileMeta("h1", "MyDocument.PDF", 100)));

        List<FileSearchResult> results = registry.search("document");
        Assert.assertEquals(1, results.size(), "case-insensitive substring match");
        Assert.assertEquals("MyDocument.PDF", results.get(0).fileName(), "returns original file name");
    }

    public void testSearchAggregatesPeerCountAcrossPeers() {
        TrackerRegistry registry = new TrackerRegistry();
        PeerInfo p1 = registry.register(null, "127.0.0.1", 9001);
        PeerInfo p2 = registry.register(null, "127.0.0.1", 9002);
        registry.announceFiles(p1.peerId(), List.of(new FileMeta("sharedhash", "movie.mp4", 500)));
        registry.announceFiles(p2.peerId(), List.of(new FileMeta("sharedhash", "movie.mp4", 500)));

        List<FileSearchResult> results = registry.search("movie");
        Assert.assertEquals(1, results.size(), "same fileHash across peers collapses into one result");
        Assert.assertEquals(2, results.get(0).peerCount(), "peerCount reflects both peers");
    }

    public void testPeersForFileReturnsOnlyPeersThatHaveIt() {
        TrackerRegistry registry = new TrackerRegistry();
        PeerInfo p1 = registry.register(null, "127.0.0.1", 9001);
        PeerInfo p2 = registry.register(null, "127.0.0.1", 9002);
        registry.announceFiles(p1.peerId(), List.of(new FileMeta("h1", "a.txt", 10)));
        registry.announceFiles(p2.peerId(), List.of(new FileMeta("h2", "b.txt", 20)));

        List<PeerRef> forH1 = registry.peersForFile("h1");
        Assert.assertEquals(1, forH1.size(), "only the owning peer is returned");
        Assert.assertEquals(p1.peerId(), forH1.get(0).peerId(), "correct peer returned");
    }

    public void testAnnounceReplacesPreviousFileList() {
        TrackerRegistry registry = new TrackerRegistry();
        PeerInfo p = registry.register(null, "127.0.0.1", 9001);
        registry.announceFiles(p.peerId(), List.of(new FileMeta("old", "old.txt", 1)));
        registry.announceFiles(p.peerId(), List.of(new FileMeta("new", "new.txt", 2)));

        Assert.assertTrue(registry.peersForFile("old").isEmpty(), "old file must be gone after re-announce (replace semantics)");
        Assert.assertEquals(1, registry.peersForFile("new").size(), "new file present after re-announce");
    }

    public void testHeartbeatPreventsEviction() throws InterruptedException {
        TrackerRegistry registry = new TrackerRegistry();
        PeerInfo p = registry.register(null, "127.0.0.1", 9001);
        Thread.sleep(80);
        Assert.assertTrue(registry.heartbeat(p.peerId()), "heartbeat on a known peer succeeds");

        int removed = registry.evictDead(50); // cutoff 50ms ago; heartbeat just refreshed lastSeen to "now"
        Assert.assertEquals(0, removed, "heartbeat must refresh lastSeen and prevent eviction");
    }

    public void testEvictDeadRemovesOnlyStalePeers() throws InterruptedException {
        TrackerRegistry registry = new TrackerRegistry();
        PeerInfo stale = registry.register(null, "127.0.0.1", 9001);
        Thread.sleep(120);
        PeerInfo fresh = registry.register(null, "127.0.0.1", 9002); // registered "now"

        int removed = registry.evictDead(100); // ttl 100ms: stale (120ms old) evicted, fresh (~0ms old) kept
        Assert.assertEquals(1, removed, "exactly one peer evicted");
        Assert.assertTrue(registry.peersForFile("anything").isEmpty(), "sanity: no files were ever announced");
        boolean staleGone = registry.allPeersSummary().stream().noneMatch(s -> s.peerId().equals(stale.peerId()));
        boolean freshKept = registry.allPeersSummary().stream().anyMatch(s -> s.peerId().equals(fresh.peerId()));
        Assert.assertTrue(staleGone, "stale peer removed");
        Assert.assertTrue(freshKept, "fresh peer retained");
    }
}
