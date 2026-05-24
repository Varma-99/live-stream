package com.livestream.ffmpeg;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds FFmpeg command lines for HLS output (segments + .m3u8 playlist).
 */
public final class FfmpegCommandBuilder {

    /** Keyframe every 30 frames @ 30fps ≈ 1s (was 120 ≈ 4s on camera). */
    private static final String GOP_FRAMES = "30";

    /** HLS segment length in seconds — smaller = lower latency. */
    private static final String HLS_SEGMENT_SECONDS = "2";

    /** Segments kept in the live playlist sliding window. */
    private static final String HLS_LIST_SIZE = "6";

    private FfmpegCommandBuilder() {
    }

    /** Synthetic test pattern (no camera). */
    public static List<String> testPatternToHls(String ffmpegPath, Path outputDir) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("testsrc=size=1280x720:rate=30");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("sine=frequency=440:sample_rate=44100");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-g");
        command.add(GOP_FRAMES);
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        appendHlsOutput(command, outputDir);
        return command;
    }

    /**
     * macOS laptop camera + microphone via AVFoundation.
     * {@code device} is usually {@code "0:0"} (first camera + first mic). List devices:
     * {@code ffmpeg -f avfoundation -list_devices true -i ""}
     */
    public static List<String> avfoundationCameraToHls(String ffmpegPath, Path outputDir, String device) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("avfoundation");
        command.add("-framerate");
        command.add("30");
        command.add("-video_size");
        command.add("1280x720");
        command.add("-i");
        command.add(device);
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-g");
        command.add(GOP_FRAMES);
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        appendHlsOutput(command, outputDir);
        return command;
    }

    /** Listen for one RTMP publisher. */
    public static List<String> rtmpListenToHls(String ffmpegPath, Path outputDir, int rtmpPort, String streamKey) {
        String input = "rtmp://127.0.0.1:" + rtmpPort + "/live/" + streamKey;

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-listen");
        command.add("1");
        command.add("-i");
        command.add(input);
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-g");
        command.add(GOP_FRAMES);
        command.add("-c:a");
        command.add("aac");
        appendHlsOutput(command, outputDir);
        return command;
    }

    /** Publish FLV to MediaMTX RTMP (path must match WHEP URL, e.g. {@code .../live/{streamKey}}). */
    public static List<String> testPatternToMediamtx(String ffmpegPath, String rtmpPublishUrl) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("testsrc=size=1280x720:rate=30");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("sine=frequency=440:sample_rate=44100");
        command.add("-map");
        command.add("0:v:0");
        command.add("-map");
        command.add("1:a:0");
        appendMediamtxWebrtcEncoding(command);
        appendFlvPublish(command, rtmpPublishUrl);
        return command;
    }

    public static List<String> avfoundationCameraToMediamtx(String ffmpegPath, String device, String rtmpPublishUrl) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("avfoundation");
        command.add("-framerate");
        command.add("30");
        command.add("-video_size");
        command.add("1280x720");
        command.add("-i");
        command.add(device);
        appendMediamtxWebrtcEncoding(command);
        appendFlvPublish(command, rtmpPublishUrl);
        return command;
    }

    /**
     * H.264 without B-frames — required for MediaMTX WebRTC
     * (see log: "WebRTC doesn't support H264 streams with B-frames").
     */
    private static void appendMediamtxWebrtcEncoding(List<String> command) {
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-profile:v");
        command.add("baseline");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-g");
        command.add(GOP_FRAMES);
        command.add("-bf");
        command.add("0");
        command.add("-x264-params");
        command.add("bframes=0:rc-lookahead=0");
        command.add("-c:a");
        command.add("aac");
        command.add("-profile:a");
        command.add("aac_low");
        command.add("-ac");
        command.add("2");
        command.add("-ar");
        command.add("44100");
        command.add("-b:a");
        command.add("128k");
    }

    private static void appendVideoAudioEncoding(List<String> command) {
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-g");
        command.add(GOP_FRAMES);
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
    }

    private static void appendFlvPublish(List<String> command, String rtmpPublishUrl) {
        command.add("-f");
        command.add("flv");
        command.add(rtmpPublishUrl);
    }

    private static void appendHlsOutput(List<String> command, Path outputDir) {
        String segmentPattern = outputDir.resolve("segment_%03d.ts").toString();
        String playlist = outputDir.resolve("index.m3u8").toString();

        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add(HLS_SEGMENT_SECONDS);
        command.add("-hls_list_size");
        command.add(HLS_LIST_SIZE);
        command.add("-hls_flags");
        command.add("append_list+omit_endlist+program_date_time+independent_segments");
        command.add("-hls_segment_filename");
        command.add(segmentPattern);
        command.add(playlist);
    }
}
