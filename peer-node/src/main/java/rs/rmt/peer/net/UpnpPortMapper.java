package rs.rmt.peer.net;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;

/**
 * Best-effort UPnP IGD port mapping so this peer's TCP listener is reachable from outside its own
 * router without the operator having to log into the router themselves. Internet-facing peers need
 * this (or a manual port forward - see README) so that peers on other networks can dial in; a LAN-only
 * demo never needs it and nothing here blocks startup if it's missing.
 */
public final class UpnpPortMapper {
    private static final int DISCOVERY_TIMEOUT_MS = 3000;

    private UpnpPortMapper() {}

    /**
     * Tries to map {@code port} to itself (same external and internal number, matching what this
     * peer already advertises to the tracker) on the first UPnP-capable gateway found. Never throws:
     * a router without UPnP, or with it turned off, is a normal outcome and just means the operator
     * has to forward the port by hand.
     */
    public static boolean tryMapPort(int port) {
        try {
            GatewayDiscover discover = new GatewayDiscover();
            discover.setTimeout(DISCOVERY_TIMEOUT_MS);
            discover.discover();
            GatewayDevice gateway = discover.getValidGateway();
            if (gateway == null) {
                System.out.println("[UPnP] no UPnP-capable gateway found - forward TCP port " + port
                        + " manually on your router if peers outside this network need to reach you");
                return false;
            }
            String localAddress = gateway.getLocalAddress().getHostAddress();
            boolean mapped = gateway.addPortMapping(port, port, localAddress, "TCP", "p2p-filesharing");
            if (mapped) {
                System.out.println("[UPnP] mapped external TCP port " + port + " -> " + localAddress + ":" + port
                        + " on gateway " + gateway.getFriendlyName());
            } else {
                System.out.println("[UPnP] gateway rejected the mapping for TCP port " + port
                        + " - forward it manually on your router if peers outside this network need to reach you");
            }
            return mapped;
        } catch (Exception e) {
            System.out.println("[UPnP] port mapping failed (" + e.getMessage() + ") - forward TCP port " + port
                    + " manually on your router if peers outside this network need to reach you");
            return false;
        }
    }
}
