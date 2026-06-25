package com.livestream.cluster;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.livestream.LiveStreamConfiguration;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class InternalApiAuthTest {

    @Test
    void acceptsMatchingToken() {
        LiveStreamConfiguration config = new LiveStreamConfiguration();
        config.setInternalApiToken("secret-token");
        assertDoesNotThrow(() -> InternalApiAuth.requireToken(config, "secret-token"));
    }

    @Test
    void rejectsWrongToken() {
        LiveStreamConfiguration config = new LiveStreamConfiguration();
        config.setInternalApiToken("secret-token");
        assertThrows(WebApplicationException.class, () -> InternalApiAuth.requireToken(config, "wrong"));
    }

    @Test
    void rejectsMissingToken() {
        LiveStreamConfiguration config = new LiveStreamConfiguration();
        config.setInternalApiToken("secret-token");
        assertThrows(WebApplicationException.class, () -> InternalApiAuth.requireToken(config, null));
    }

    @Test
    void rejectsWhenInternalApiDisabled() {
        LiveStreamConfiguration config = new LiveStreamConfiguration();
        config.setInternalApiToken("");
        assertThrows(WebApplicationException.class, () -> InternalApiAuth.requireToken(config, "any"));
    }
}
