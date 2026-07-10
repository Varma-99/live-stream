package com.livestream.ffmpeg;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Builds FFmpeg command lines for HLS output (segments + .m3u8 playlist).
 */
public final class FfmpegCommandBuilder {

    public enum EncoderType {
        LIBX264,
        VIDEOTOOLBOX,
        NVENC
    }

    /** Keyframe every 30 frames @ 30fps ≈ 1s (HLS). */
    private static final String GOP_FRAMES = "30";

    /** WebRTC/RTMP: 30 frames @ 30fps ≈ 1s between keyframes (reduces aligned ABR keyframe spikes). */
    private static final String GOP_FRAMES_MEDIAMTX = "30";

    private static final String GOP_FRAMES_DEGRADED = "5";

    private static final String DEGRADED_VIDEO_BITRATE = "100k";

    private static final String ABR_FILTER_NORMAL =
            "[0:v]split=3[v1][v2][v3];[v1]scale=1280:720:flags=fast_bilinear[vhi];[v2]scale=854:480:flags=fast_bilinear[vmd];[v3]scale=640:360:flags=fast_bilinear[vlo]";

    private static final String ABR_FILTER_DEGRADED =
            "[0:v]split=3[v1][v2][v3];[v1]scale=1280:720:flags=fast_bilinear,fps=5[vhi];[v2]scale=854:480:flags=fast_bilinear,fps=5[vmd];[v3]scale=640:360:flags=fast_bilinear,fps=5[vlo]";

    /** HLS segment length in seconds — smaller = lower latency. */
    private static final String HLS_SEGMENT_SECONDS = "2";

    /** Segments kept in the live playlist sliding window. */
    private static final String HLS_LIST_SIZE = "6";

    private FfmpegCommandBuilder() {
    }

    /** Picks the best H.264 encoder available in the configured FFmpeg binary. */
    public static EncoderType probeBestEncoder(String ffmpegPath) {
        if (ffmpegPath == null || ffmpegPath.isBlank()) {
            return EncoderType.LIBX264;
        }
        Process process = null;
        try {
            process = new ProcessBuilder(ffmpegPath, "-hide_banner", "-encoders")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                return EncoderType.LIBX264;
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("mac") && out.contains("h264_videotoolbox")) {
                return EncoderType.VIDEOTOOLBOX;
            }
            if (!os.contains("mac") && out.contains("h264_nvenc")) {
                return EncoderType.NVENC;
            }
        } catch (Exception e) {
            return EncoderType.LIBX264;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
        return EncoderType.LIBX264;
    }

    /** HLS at reduced bitrate for degrade demo. */
    public static List<String> testPatternToHlsDegraded(String ffmpegPath, Path outputDir) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("testsrc=size=640x360:rate=15");
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
        command.add("-b:v");
        command.add(DEGRADED_VIDEO_BITRATE);
        command.add("-g");
        command.add(GOP_FRAMES_DEGRADED);
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("64k");
        appendHlsOutput(command, outputDir);
        return command;
    }

    public static List<String> avfoundationCameraToHlsDegraded(String ffmpegPath, Path outputDir, String device) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("avfoundation");
        command.add("-framerate");
        command.add("15");
        command.add("-video_size");
        command.add("640x360");
        command.add("-i");
        command.add(device);
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-b:v");
        command.add(DEGRADED_VIDEO_BITRATE);
        command.add("-g");
        command.add(GOP_FRAMES_DEGRADED);
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("64k");
        appendHlsOutput(command, outputDir);
        return command;
    }

    public static List<String> rtmpListenToHlsDegraded(
            String ffmpegPath, Path outputDir, int rtmpPort, String streamKey) {
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
        command.add("-b:v");
        command.add(DEGRADED_VIDEO_BITRATE);
        command.add("-g");
        command.add(GOP_FRAMES_DEGRADED);
        command.add("-c:a");
        command.add("aac");
        appendHlsOutput(command, outputDir);
        return command;
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

    public static List<String> avfoundationCameraToMediamtx(
            String ffmpegPath, String device, String rtmpPublishUrl, EncoderType encoder) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        appendMediamtxInputLatency(command);
        command.add("-f");
        command.add("avfoundation");
        command.add("-framerate");
        command.add("30");
        command.add("-video_size");
        command.add("1280x720");
        command.add("-i");
        command.add(device);
        appendAvfoundationCaptureSync(command);
        appendMediamtxVideoEncoding(command, "2500k", false, encoder);
        appendFlvPublish(command, rtmpPublishUrl);
        return command;
    }

    /**
     * Three RTMP outputs (high / mid / low) for fallback ABR — separate MediaMTX paths + WHEP URLs.
     */
    public static List<String> avfoundationCameraToMediamtxAbr(
            String ffmpegPath,
            String device,
            String rtmpPublishUrlHigh,
            String rtmpPublishUrlMid,
            String rtmpPublishUrlLow,
            boolean degraded,
            EncoderType encoder) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        appendMediamtxInputLatency(command);
        command.add("-f");
        command.add("avfoundation");
        command.add("-framerate");
        command.add("30");
        command.add("-video_size");
        command.add("1280x720");
        command.add("-i");
        command.add(device);
        appendAvfoundationCaptureSync(command);
        command.add("-filter_complex");
        command.add(degraded ? ABR_FILTER_DEGRADED : ABR_FILTER_NORMAL);
        if (degraded) {
            appendMediamtxAbrOutput(command, "[vhi]", "0:a:0", DEGRADED_VIDEO_BITRATE, rtmpPublishUrlHigh, true, encoder);
            appendMediamtxAbrOutput(command, "[vmd]", "0:a:0", DEGRADED_VIDEO_BITRATE, rtmpPublishUrlMid, true, encoder);
            appendMediamtxAbrOutput(command, "[vlo]", "0:a:0", DEGRADED_VIDEO_BITRATE, rtmpPublishUrlLow, true, encoder);
        } else {
            appendMediamtxAbrOutput(command, "[vhi]", "0:a:0", "2500k", rtmpPublishUrlHigh, false, encoder);
            appendMediamtxAbrOutput(command, "[vmd]", "0:a:0", "1200k", rtmpPublishUrlMid, false, encoder);
            appendMediamtxAbrOutput(command, "[vlo]", "0:a:0", "600k", rtmpPublishUrlLow, false, encoder);
        }
        return command;
    }

    public static List<String> testPatternToMediamtxAbr(
            String ffmpegPath,
            String rtmpPublishUrlHigh,
            String rtmpPublishUrlMid,
            String rtmpPublishUrlLow,
            boolean degraded) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add(degraded ? "testsrc=size=1280x720:rate=5" : "testsrc=size=1280x720:rate=30");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("sine=frequency=440:sample_rate=44100");
        command.add("-filter_complex");
        command.add(degraded ? ABR_FILTER_DEGRADED : ABR_FILTER_NORMAL);
        if (degraded) {
            appendMediamtxAbrOutput(command, "[vhi]", "1:a:0", DEGRADED_VIDEO_BITRATE, rtmpPublishUrlHigh, true, EncoderType.LIBX264);
            appendMediamtxAbrOutput(command, "[vmd]", "1:a:0", DEGRADED_VIDEO_BITRATE, rtmpPublishUrlMid, true, EncoderType.LIBX264);
            appendMediamtxAbrOutput(command, "[vlo]", "1:a:0", DEGRADED_VIDEO_BITRATE, rtmpPublishUrlLow, true, EncoderType.LIBX264);
        } else {
            appendMediamtxAbrOutput(command, "[vhi]", "1:a:0", "2500k", rtmpPublishUrlHigh, false, EncoderType.LIBX264);
            appendMediamtxAbrOutput(command, "[vmd]", "1:a:0", "1200k", rtmpPublishUrlMid, false, EncoderType.LIBX264);
            appendMediamtxAbrOutput(command, "[vlo]", "1:a:0", "600k", rtmpPublishUrlLow, false, EncoderType.LIBX264);
        }
        return command;
    }

    private static void appendMediamtxAbrOutput(
            List<String> command,
            String videoLabel,
            String audioMap,
            String videoBitrate,
            String rtmpUrl,
            boolean degraded,
            EncoderType encoder) {
        command.add("-map");
        command.add(videoLabel);
        command.add("-map");
        command.add(audioMap);
        appendMediamtxVideoEncoding(command, videoBitrate, degraded, encoder);
        appendFlvPublish(command, rtmpUrl);
    }

    private static void appendMediamtxVideoEncoding(
            List<String> command, String videoBitrate, boolean degraded, EncoderType encoder) {
        EncoderType effective = encoder != null ? encoder : EncoderType.LIBX264;
        switch (effective) {
            case VIDEOTOOLBOX -> {
                command.add("-c:v");
                command.add("h264_videotoolbox");
                command.add("-b:v");
                command.add(videoBitrate);
                command.add("-realtime");
                command.add("1");
            }
            case NVENC -> {
                command.add("-c:v");
                command.add("h264_nvenc");
                command.add("-preset");
                command.add("llhq");
                command.add("-b:v");
                command.add(videoBitrate);
                command.add("-maxrate");
                command.add(videoBitrate);
                command.add("-bufsize");
                command.add(bufferSize(videoBitrate));
                command.add("-rc");
                command.add("vbr_hq");
            }
            default -> {
                command.add("-c:v");
                command.add("libx264");
                command.add("-preset");
                command.add("ultrafast");
                command.add("-tune");
                command.add("zerolatency");
                command.add("-b:v");
                command.add(videoBitrate);
                command.add("-maxrate");
                command.add(videoBitrate);
                command.add("-bufsize");
                command.add(bufferSize(videoBitrate));
                command.add("-x264-params");
                command.add("bframes=0:rc-lookahead=0:sync-lookahead=0:scenecut=0");
            }
        }
        command.add("-profile:v");
        command.add("baseline");
        command.add("-pix_fmt");
        command.add("yuv420p");
        String gop = degraded ? GOP_FRAMES_DEGRADED : GOP_FRAMES_MEDIAMTX;
        command.add("-g");
        command.add(gop);
        command.add("-keyint_min");
        command.add(gop);
        command.add("-bf");
        command.add("0");
        command.add("-c:a");
        command.add("aac");
        command.add("-profile:a");
        command.add("aac_low");
        command.add("-ac");
        command.add("2");
        command.add("-ar");
        command.add("44100");
        command.add("-b:a");
        command.add("96k");
    }

    private static String bufferSize(String videoBitrate) {
        String digits = videoBitrate.replace("k", "").replace("K", "");
        try {
            int kbps = Integer.parseInt(digits);
            return Math.max(kbps * 2, 400) + "k";
        } catch (NumberFormatException e) {
            return "1000k";
        }
    }

    /** Reduce capture/mux buffering before encode (MediaMTX path only). */
    private static void appendMediamtxInputLatency(List<String> command) {
        command.add("-fflags");
        command.add("nobuffer");
        command.add("-flags");
        command.add("low_delay");
    }

    /** Constant frame rate + A/V sync for live avfoundation capture (avoids PTS gaps on RTMP). */
    private static void appendAvfoundationCaptureSync(List<String> command) {
        command.add("-vsync");
        command.add("cfr");
        command.add("-async");
        command.add("1");
    }

    /**
     * H.264 without B-frames — required for MediaMTX WebRTC
     * (see log: "WebRTC doesn't support H264 streams with B-frames").
     */
    private static void appendMediamtxWebrtcEncoding(List<String> command) {
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("ultrafast");
        command.add("-tune");
        command.add("zerolatency");
        command.add("-profile:v");
        command.add("baseline");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-g");
        command.add(GOP_FRAMES_MEDIAMTX);
        command.add("-keyint_min");
        command.add(GOP_FRAMES_MEDIAMTX);
        command.add("-sc_threshold");
        command.add("0");
        command.add("-bf");
        command.add("0");
        command.add("-x264-params");
        command.add("bframes=0:rc-lookahead=0:sync-lookahead=0:scenecut=0");
        command.add("-c:a");
        command.add("aac");
        command.add("-profile:a");
        command.add("aac_low");
        command.add("-ac");
        command.add("2");
        command.add("-ar");
        command.add("44100");
        command.add("-b:a");
        command.add("96k");
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
        command.add("-muxdelay");
        command.add("0");
        command.add("-muxpreload");
        command.add("0");
        command.add("-flush_packets");
        command.add("1");
        command.add("-f");
        command.add("flv");
        command.add("-flvflags");
        command.add("no_duration_filesize");
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
