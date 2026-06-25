package com.livestream.cluster;

import com.livestream.LiveStreamConfiguration;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Validates {@code X-Internal-Token} for instance-to-instance calls. */
public final class InternalApiAuth {

    public static final String HEADER = "X-Internal-Token";

    private InternalApiAuth() {}

    public static void requireToken(LiveStreamConfiguration configuration, String token) {
        if (!configuration.isInternalApiEnabled()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                            .entity("Internal API is not configured on this instance")
                            .build());
        }
        byte[] expected = configuration.getInternalApiToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = token != null ? token.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (expected.length != actual.length || !MessageDigest.isEqual(expected, actual)) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }
}
