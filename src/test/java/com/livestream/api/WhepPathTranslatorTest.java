package com.livestream.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WhepPathTranslatorTest {

    @Test
    void mediamtxPassthrough() {
        assertEquals(
                "http://127.0.0.1:8889/live/key_high/whep",
                WhepPathTranslator.mediamtxTarget("http://127.0.0.1:8889", "live/key_high/whep"));
    }

    @Test
    void srsQueryParamFromAppPath() {
        assertEquals(
                "http://127.0.0.1:1985/rtc/v1/whep/?app=live&stream=key_high",
                WhepPathTranslator.srsTarget("http://127.0.0.1:1985", "live/key_high/whep"));
        assertEquals(
                "http://127.0.0.1:1985/rtc/v1/whep/?app=live&stream=phase1test",
                WhepPathTranslator.srsTarget("http://127.0.0.1:1985", "/live/phase1test/whep"));
    }

    @Test
    void srsRejectsBadPath() {
        assertThrows(IllegalArgumentException.class, () -> WhepPathTranslator.srsTarget("http://127.0.0.1:1985", "bad/path"));
    }
}
