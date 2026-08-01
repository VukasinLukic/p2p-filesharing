package rs.rmt.tracker.model;

public record PeerSummary(String peerId, String host, int port, int fileCount, long lastSeenAgoMs) {}
