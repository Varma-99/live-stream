package com.livestream.ffmpeg;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FfmpegCommandBuilderTest {

    @Test
    void testPatternToHlsIncludesHlsFlags() {
        List<String> cmd = FfmpegCommandBuilder.testPatternToHls("/usr/bin/ffmpeg", Path.of("/tmp/hls/1"));
        assertTrue(cmd.contains("-f"));
        assertTrue(cmd.contains("hls"));
        assertTrue(cmd.stream().anyMatch(s -> s.contains("index.m3u8")));
    }

    @Test
    void mediamtxAbrHasThreeOutputs() {
        List<String> cmd = FfmpegCommandBuilder.testPatternToMediamtxAbr(
                "/usr/bin/ffmpeg",
                "rtmp://127.0.0.1/live/key_high",
                "rtmp://127.0.0.1/live/key_mid",
                "rtmp://127.0.0.1/live/key_low",
                false);
        long flvCount = cmd.stream().filter("flv"::equals).count();
        assertTrue(flvCount >= 3);
        assertTrue(cmd.contains("-filter_complex"));
    }

    @Test
    void degradedHlsUsesLowerBitrate() {
        List<String> cmd = FfmpegCommandBuilder.testPatternToHlsDegraded("/usr/bin/ffmpeg", Path.of("/tmp/hls/1"));
        assertTrue(cmd.contains("100k"));
        assertTrue(cmd.contains("-b:v"));
    }

    @Test
    void degradedAbrUsesFpsFilter() {
        List<String> cmd = FfmpegCommandBuilder.testPatternToMediamtxAbr(
                "/usr/bin/ffmpeg",
                "rtmp://127.0.0.1/live/k1",
                "rtmp://127.0.0.1/live/k2",
                "rtmp://127.0.0.1/live/k3",
                true);
        String filter = cmd.get(cmd.indexOf("-filter_complex") + 1);
        assertTrue(filter.contains("fps=5"));
    }
}
