package com.livestream.qos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RollingStatsTest {

    @Test
    void emptyWindowReturnsZero() {
        RollingStats stats = new RollingStats(5);
        assertEquals(0, stats.count());
        assertEquals(0, stats.average());
        assertEquals(0, stats.p95());
        assertEquals(0.0, stats.stdDev());
    }

    @Test
    void averageAndP95() {
        RollingStats stats = new RollingStats(10);
        for (int i = 1; i <= 10; i++) {
            stats.add(i * 100L);
        }
        assertEquals(10, stats.count());
        assertEquals(550, stats.average());
        assertTrue(stats.p95() >= 900);
    }

    @Test
    void evictsOldestWhenFull() {
        RollingStats stats = new RollingStats(10);
        for (int i = 0; i < 15; i++) {
            stats.add(i);
        }
        assertEquals(10, stats.count());
        assertEquals(9, stats.average());
    }

    @Test
    void identicalSamplesZeroStdDev() {
        RollingStats stats = new RollingStats(5);
        stats.add(500);
        stats.add(500);
        stats.add(500);
        assertEquals(0.0, stats.stdDev());
    }
}
