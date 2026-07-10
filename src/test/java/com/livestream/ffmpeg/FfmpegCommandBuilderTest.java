package com.livestream.ffmpeg;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void avfoundationMediamtxAbrUsesCfrSyncAndOneSecondGop() {
        List<String> cmd = FfmpegCommandBuilder.avfoundationCameraToMediamtxAbr(
                "/usr/bin/ffmpeg",
                "0:0",
                "rtmp://127.0.0.1/live/key_high",
                "rtmp://127.0.0.1/live/key_mid",
                "rtmp://127.0.0.1/live/key_low",
                false,
                FfmpegCommandBuilder.EncoderType.LIBX264);
        assertTrue(cmd.contains("-vsync"));
        assertTrue(cmd.contains("cfr"));
        assertTrue(cmd.contains("-async"));
        assertTrue(cmd.contains("1"));
        assertTrue(cmd.contains("-g"));
        assertTrue(cmd.contains("30"));
    }

    @Test
    void avfoundationMediamtxSinglePathUsesCfrSync() {
        List<String> cmd = FfmpegCommandBuilder.avfoundationCameraToMediamtx(
                "/usr/bin/ffmpeg",
                "0:0",
                "rtmp://127.0.0.1/live/key",
                FfmpegCommandBuilder.EncoderType.LIBX264);
        int deviceIdx = cmd.indexOf("0:0");
        assertTrue(deviceIdx >= 0);
        assertTrue(cmd.subList(deviceIdx, cmd.size()).contains("-vsync"));
        assertTrue(cmd.subList(deviceIdx, cmd.size()).contains("cfr"));
    }

    @Test
    void avfoundationCameraMediamtxUsesVideotoolboxWhenRequested() {
        List<String> cmd = FfmpegCommandBuilder.avfoundationCameraToMediamtx(
                "/usr/bin/ffmpeg",
                "0:0",
                "rtmp://127.0.0.1/live/key",
                FfmpegCommandBuilder.EncoderType.VIDEOTOOLBOX);
        assertTrue(cmd.contains("h264_videotoolbox"));
        assertTrue(cmd.contains("-realtime"));
        assertFalse(cmd.contains("x264-params"));
    }
}
