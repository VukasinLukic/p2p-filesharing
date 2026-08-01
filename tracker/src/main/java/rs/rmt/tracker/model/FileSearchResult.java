package rs.rmt.tracker.model;

public record FileSearchResult(String fileHash, String fileName, long size, int peerCount) {}
