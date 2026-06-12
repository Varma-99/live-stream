package com.livestream.srs;

/** Per-path ingest stats from SRS {@code /api/v1/streams/}. */
public record SrsPathStats(long recvBytes, long recvKbps30s) {
}
