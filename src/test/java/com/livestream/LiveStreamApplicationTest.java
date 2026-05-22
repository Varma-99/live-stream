package com.livestream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LiveStreamApplicationTest {

    @Test
    void applicationName() {
        assertEquals("live-stream", new LiveStreamApplication().getName());
    }
}
