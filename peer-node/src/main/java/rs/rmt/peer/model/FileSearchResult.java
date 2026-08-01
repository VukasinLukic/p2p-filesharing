package rs.rmt.peer.model;

public record FileSearchResult(String fileHash, String fileName, long size, int peerCount) {}
