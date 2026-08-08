package rs.rmt.peer.util;

import rs.rmt.peer.testutil.Assert;

/**
 * Detection of the address this peer must advertise. Machine-dependent by nature (a build agent
 * may have no LAN at all), so these assert the contract - never loopback, always a dialable IPv4 -
 * rather than one specific address.
 */
public class LocalAddressTest {

    public void testDetectedAddressIsNeverLoopbackOrGarbage() {
        assertUsableOrNull(LocalAddress.primaryLanAddress(), "primaryLanAddress()");
        assertUsableOrNull(LocalAddress.defaultRouteAddress(), "defaultRouteAddress()");
    }

    public void testDefaultRouteAddressIsOneOfThisMachinesOwnAddresses() throws Exception {
        String detected = LocalAddress.defaultRouteAddress();
        if (detected == null) return; // machine without a default route

        boolean owned = false;
        for (java.net.NetworkInterface nic : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
            for (java.net.InetAddress address : java.util.Collections.list(nic.getInetAddresses())) {
                if (detected.equals(address.getHostAddress())) owned = true;
            }
        }
        Assert.assertTrue(owned, "detected address must actually belong to this machine: " + detected);
    }

    public void testTowardsALocalTrackerStillFindsTheLanAddress() {
        // The exact failing case: the tracker is on this machine, so routing to it yields
        // loopback and detection must fall back to enumerating interfaces.
        assertUsableOrNull(LocalAddress.towards("http://localhost:8080"), "towards(localhost)");
        assertUsableOrNull(LocalAddress.towards("http://127.0.0.1:8080"), "towards(127.0.0.1)");
    }

    public void testUnreachableOrMalformedTrackerUrlDoesNotThrow() {
        // Detection runs before the tracker is known to be up; it must degrade, not explode.
        assertUsableOrNull(LocalAddress.towards("http://192.0.2.1:8080"), "towards(unroutable)");
        assertUsableOrNull(LocalAddress.towards("not a url at all"), "towards(garbage)");
        assertUsableOrNull(LocalAddress.towards("http://"), "towards(no host)");
    }

    /** Null is a legitimate answer (offline machine); anything else must be a usable IPv4. */
    private static void assertUsableOrNull(String address, String label) {
        if (address == null) return;
        Assert.assertFalse(address.startsWith("127."), label + " must not return loopback: " + address);
        Assert.assertFalse(address.equals("0.0.0.0"), label + " must not return the wildcard address");
        Assert.assertFalse(address.startsWith("169.254."), label + " must not return a link-local address");
        Assert.assertTrue(address.matches("\\d{1,3}(\\.\\d{1,3}){3}"), label + " must be IPv4: " + address);
    }
}
