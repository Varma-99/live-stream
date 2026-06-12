package com.livestream.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Maps app WHEP paths to upstream media-server URLs. */
final class WhepPathTranslator {

    private WhepPathTranslator() {
    }

    /**
     * MediaMTX path passthrough: {@code live/{stream}/whep} on WebRTC base.
     */
    static String mediamtxTarget(String webrtcBase, String path) {
        return webrtcBase.replaceAll("/$", "") + "/" + normalizePath(path);
    }

    /**
     * SRS query-param WHEP: {@code /rtc/v1/whep/?app=live&stream={stream}}.
     * Input path shape: {@code live/{streamKey}[_high|_mid|_low]/whep}.
     */
    static String srsTarget(String whepBase, String path) {
        String normalized = normalizePath(path);
        if (!normalized.endsWith("/whep")) {
            throw new IllegalArgumentException("Not a WHEP path: " + path);
        }
        String withoutWhep = normalized.substring(0, normalized.length() - "/whep".length());
        int slash = withoutWhep.indexOf('/');
        if (slash < 0 || !"live".equals(withoutWhep.substring(0, slash))) {
            throw new IllegalArgumentException("Expected live/{stream}/whep, got: " + path);
        }
        String stream = withoutWhep.substring(slash + 1);
        if (stream.isBlank()) {
            throw new IllegalArgumentException("Missing stream name in WHEP path: " + path);
        }
        String base = whepBase.replaceAll("/$", "");
        String encoded = URLEncoder.encode(stream, StandardCharsets.UTF_8);
        return base + "/rtc/v1/whep/?app=live&stream=" + encoded;
    }

    private static String normalizePath(String path) {
        String trimmed = path == null ? "" : path.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
