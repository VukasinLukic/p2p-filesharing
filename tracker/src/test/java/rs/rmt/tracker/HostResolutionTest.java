package rs.rmt.tracker;

import rs.rmt.tracker.testutil.Assert;

/**
 * Which address the tracker hands out for a peer. Regression cover for the LAN bug where a peer
 * sharing a machine with the tracker was published as 127.0.0.1, so a peer on another machine
 * tried to download from its own port and got "Connection refused".
 */
public class HostResolutionTest {

    public void testRemoteAddressWinsWhenPeerIsOnAnotherMachine() {
        Assert.assertEquals("192.168.1.136", TrackerMain.chooseHost("192.168.1.136", null),
                "a peer that reached us over the network is published at the address we saw");
        Assert.assertEquals("192.168.1.136", TrackerMain.chooseHost("192.168.1.136", "10.0.0.5"),
                "the tracker's own view must not be overridable by what the peer claims");
    }

    public void testAdvertisedLanAddressIsUsedForLoopbackRegistrations() {
        Assert.assertEquals("192.168.1.119", TrackerMain.chooseHost("127.0.0.1", "192.168.1.119"),
                "a peer on the tracker's machine must be published at its LAN address");
        Assert.assertEquals("192.168.1.119", TrackerMain.chooseHost("::1", "192.168.1.119"),
                "IPv6 loopback counts as loopback too");
    }

    public void testFallsBackToLoopbackWhenThereIsNothingBetter() {
        // Single machine with no network: 127.0.0.1 is correct and the local demo still works.
        Assert.assertEquals("127.0.0.1", TrackerMain.chooseHost("127.0.0.1", null),
                "no advertised address means keep what we saw");
        Assert.assertEquals("127.0.0.1", TrackerMain.chooseHost("127.0.0.1", "127.0.0.1"),
                "an advertised loopback address is no improvement");
        Assert.assertEquals("127.0.0.1", TrackerMain.chooseHost("127.0.0.1", "localhost"),
                "'localhost' is no improvement either");
        Assert.assertEquals("127.0.0.1", TrackerMain.chooseHost("127.0.0.1", "   "),
                "blank advertised address is ignored");
    }

    public void testMalformedAdvertisedHostIsIgnored() {
        // Peers dial this value as host:port - never let junk through.
        Assert.assertEquals("127.0.0.1", TrackerMain.chooseHost("127.0.0.1", "192.168.1.5 evil"),
                "an address with whitespace is rejected");
        Assert.assertEquals("127.0.0.1", TrackerMain.chooseHost("127.0.0.1", "http://192.168.1.5/"),
                "a URL is not an address");
        Assert.assertEquals("127.0.0.1", TrackerMain.chooseHost("127.0.0.1", "a".repeat(300)),
                "absurdly long values are rejected");
    }
}
