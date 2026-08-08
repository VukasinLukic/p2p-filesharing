package rs.rmt.peer.util;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Finds the address other machines must dial to reach this peer.
 *
 * Needed because the tracker normally derives a peer's address from the incoming connection, which
 * breaks for a peer running on the same machine as the tracker: it connects over localhost, so the
 * tracker records 127.0.0.1 and hands that to a peer on another machine - which then tries to
 * download from its own port and gets "Connection refused".
 *
 * Picking the right address is the hard part on a typical Windows dev machine, which also has
 * Hyper-V, WSL, Docker, Bluetooth and VPN adapters. Java's NetworkInterface.isVirtual() does NOT
 * flag those (it only means "subinterface"), so plain enumeration happily returns something like
 * 172.27.224.1 that no other machine can reach. Hence the strategy below.
 */
public final class LocalAddress {
    private static final int PROBE_TIMEOUT_MS = 1500;
    /** Only used as a routing-table lookup target; see defaultRouteAddress(). */
    private static final String ROUTE_LOOKUP_TARGET = "8.8.8.8";

    private LocalAddress() {}

    /**
     * Best guess at this machine's externally reachable address, or null when there isn't one
     * (offline machine - the tracker's own view of the connection is then the right answer).
     */
    public static String towards(String trackerUrl) {
        String fromRoute = probeRouteTo(trackerUrl);
        if (fromRoute != null) return fromRoute;

        String fromDefaultRoute = defaultRouteAddress();
        if (fromDefaultRoute != null) return fromDefaultRoute;

        return primaryLanAddress();
    }

    /**
     * Asks the OS which local interface it would use to reach the tracker, by opening a throwaway
     * connection and reading the socket's own address. Most accurate answer when the tracker is on
     * another machine; yields loopback - i.e. nothing useful - when it is on this one.
     */
    private static String probeRouteTo(String trackerUrl) {
        try {
            URI uri = URI.create(trackerUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            if (host == null) return null;

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
                InetAddress local = socket.getLocalAddress();
                return isUsable(local) ? local.getHostAddress() : null;
            }
        } catch (Exception e) {
            // Tracker down / bad URL: fall through to the routing-table lookups.
            return null;
        }
    }

    /**
     * Source address of this machine's default route - i.e. the adapter that actually carries
     * traffic off this box (the Wi-Fi/Ethernet one, not a Hyper-V switch).
     *
     * connect() on a UDP socket sends NOTHING: it only makes the OS resolve the route and bind a
     * local address, which is then read back. No packet reaches 8.8.8.8, and this works without
     * internet access as long as a default route exists.
     */
    public static String defaultRouteAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(ROUTE_LOOKUP_TARGET), 53);
            InetAddress local = socket.getLocalAddress();
            return isUsable(local) ? local.getHostAddress() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Last resort: enumerate interfaces. Private ranges are tried in the order that best matches a
     * real home/faculty LAN, because 172.16-31.x is where Docker and Hyper-V put their fake ones.
     */
    public static String primaryLanAddress() {
        List<String> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            for (NetworkInterface nic : Collections.list(interfaces)) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (isUsable(address) && address.isSiteLocalAddress()) {
                        candidates.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LocalAddress] could not enumerate network interfaces: " + e.getMessage());
            return null;
        }

        for (String prefix : new String[]{"192.168.", "10."}) {
            for (String candidate : candidates) {
                if (candidate.startsWith(prefix)) return candidate;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** IPv4 only: the transfer protocol addresses are put straight into host:port strings. */
    private static boolean isUsable(InetAddress address) {
        return address != null
                && address.getAddress().length == 4
                && !address.isLoopbackAddress()
                && !address.isAnyLocalAddress()
                && !address.isLinkLocalAddress();
    }
}
