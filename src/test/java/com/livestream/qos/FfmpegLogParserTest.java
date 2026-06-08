package com.livestream.qos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FfmpegLogParserTest {

    @Test
    void parsesProgressLine() {
        String line = "frame=  120 fps= 29 q=28.0 size=    2048kB time=00:00:04.00 bitrate=2048.0kbits/s speed=0.98x";
        var result = FfmpegLogParser.parseLine(line);
        assertTrue(result.isPresent());
        assertEquals(29.0, result.get().fps());
        assertEquals(0.98, result.get().speedRatio());
    }

    @Test
    void parsesErrorLine() {
        var result = FfmpegLogParser.parseLine("Error opening input: Device not found");
        assertTrue(result.isPresent());
        assertTrue(result.get().isError());
    }

    @Test
    void parsesDropDup() {
        String line = "frame=  50 fps= 30.0 q=28.0 size=  512kB time=00:00:01.66 bitrate=2500.0kbits/s speed=1.02x drop=3 dup=1";
        var result = FfmpegLogParser.parseLine(line);
        assertTrue(result.isPresent());
        assertEquals(3L, result.get().droppedFrames());
        assertEquals(1L, result.get().duplicatedFrames());
    }

    @Test
    void ignoresGarbage() {
        assertTrue(FfmpegLogParser.parseLine("hello world").isEmpty());
        assertTrue(FfmpegLogParser.parseLine("").isEmpty());
        assertTrue(FfmpegLogParser.parseLine(null).isEmpty());
    }
}
