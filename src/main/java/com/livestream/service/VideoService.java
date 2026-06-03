package com.livestream.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.api.dto.QualityOption;
import com.livestream.ffmpeg.FfmpegCommandBuilder;
import com.livestream.model.LiveStream;
import com.livestream.qos.StreamQoSService;
import io.dropwizard.lifecycle.Managed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Plane: FFmpeg ingest — either HLS files on disk or RTMP publish to MediaMTX.
 */
@Singleton
public class VideoService implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoService.class);

    private final LiveStreamConfiguration configuration;
    private final StreamQoSService streamQoSService;
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> degradedStreams = new ConcurrentHashMap<>();

    @Inject
    public VideoService(LiveStreamConfiguration configuration, StreamQoSService streamQoSService) {
        this.configuration = configuration;
        this.streamQoSService = streamQoSService;
    }

    public boolean isMediamtxDelivery() {
        return configuration.isMediamtxDelivery();
    }

    public boolean isAbrEnabled() {
        return isMediamtxDelivery() && configuration.isAbrEnabled();
    }

    public boolean isDegraded(long streamId) {
        return degradedStreams.getOrDefault(streamId, false);
    }

    public void startForStream(LiveStream stream) throws IOException {
        startFfmpeg(stream, false);
    }

    public void degradeStream(LiveStream stream) throws IOException {
        if (!isAbrEnabled()) {
            throw new IllegalStateException("Degrade demo requires MediaMTX ABR (abrEnabled: true)");
        }
        restartFfmpeg(stream, true);
    }

    public void restoreStreamQuality(LiveStream stream) throws IOException {
        if (!isAbrEnabled()) {
            throw new IllegalStateException("Restore requires MediaMTX ABR (abrEnabled: true)");
        }
        restartFfmpeg(stream, false);
    }

    private void restartFfmpeg(LiveStream stream, boolean degraded) throws IOException {
        Long streamId = stream.getId();
        if (!processes.containsKey(streamId)) {
            throw new IllegalStateException("FFmpeg is not running for stream " + streamId);
        }
        stopForStream(streamId);
        streamQoSService.recordEncodeRestart(streamId);
        startFfmpeg(stream, degraded);
        LOGGER.info("FFmpeg restarted for stream {} (degraded={})", streamId, degraded);
    }

    private void startFfmpeg(LiveStream stream, boolean degraded) throws IOException {
        Long streamId = stream.getId();
        if (processes.containsKey(streamId)) {
            throw new IllegalStateException("FFmpeg already running for stream " + streamId);
        }

        Path ffmpeg = Path.of(configuration.getFfmpegPath());
        if (!Files.isExecutable(ffmpeg)) {
            throw new IllegalStateException(
                    "FFmpeg not found at " + ffmpeg + " — install FFmpeg or set ffmpegPath in config");
        }

        Path outputDir = Paths.get(configuration.getHlsOutputDir()).resolve(String.valueOf(streamId));
        if (!isMediamtxDelivery()) {
            Files.createDirectories(outputDir);
        }

        List<String> command = buildFfmpegCommand(ffmpeg.toString(), outputDir, stream, degraded);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        processes.put(streamId, process);
        if (degraded) {
            degradedStreams.put(streamId, true);
        } else {
            degradedStreams.remove(streamId);
        }
        streamQoSService.recordEncodeStarted(streamId);

        LOGGER.info(
                "FFmpeg started for stream {} (pid={}, delivery={}, abr={}, degraded={}, input={})",
                streamId,
                process.pid(),
                configuration.getStreamDelivery(),
                isAbrEnabled(),
                degraded,
                resolveInputMode());
        if (isMediamtxDelivery()) {
            if (isAbrEnabled()) {
                String key = stream.getStreamKey();
                LOGGER.info(
                        "MediaMTX ABR RTMP: high={}, mid={}, low={}",
                        rtmpPublishUrl(key, "_high"),
                        rtmpPublishUrl(key, "_mid"),
                        rtmpPublishUrl(key, "_low"));
            } else {
                LOGGER.info("MediaMTX RTMP publish: {}", rtmpPublishUrl(stream.getStreamKey(), ""));
                LOGGER.info("WebRTC WHEP playback: {}", whepPlaybackUrl(stream.getStreamKey(), ""));
            }
        } else {
            LOGGER.info("HLS playlist: {}", hlsPlaybackUrl(streamId));
        }

        Thread logThread = new Thread(() -> drainLogs(streamId, process), "ffmpeg-log-" + streamId);
        logThread.setDaemon(true);
        logThread.start();
    }

    private List<String> buildFfmpegCommand(
            String ffmpegPath, Path outputDir, LiveStream stream, boolean degraded) {
        if (isMediamtxDelivery()) {
            String streamKey = stream.getStreamKey();
            if (isAbrEnabled()) {
                String high = rtmpPublishUrl(streamKey, "_high");
                String mid = rtmpPublishUrl(streamKey, "_mid");
                String low = rtmpPublishUrl(streamKey, "_low");
                return switch (resolveInputMode()) {
                    case "camera" -> {
                        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
                            throw new IllegalStateException(
                                    "videoInput=camera requires macOS. Use videoInput: test");
                        }
                        yield FfmpegCommandBuilder.avfoundationCameraToMediamtxAbr(
                                ffmpegPath, configuration.getCameraDevice(), high, mid, low, degraded);
                    }
                    default -> FfmpegCommandBuilder.testPatternToMediamtxAbr(ffmpegPath, high, mid, low, degraded);
                };
            }
            String rtmpUrl = rtmpPublishUrl(streamKey, "");
            return switch (resolveInputMode()) {
                case "camera" -> {
                    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
                        throw new IllegalStateException(
                                "videoInput=camera requires macOS. Use videoInput: test");
                    }
                    yield FfmpegCommandBuilder.avfoundationCameraToMediamtx(
                            ffmpegPath, configuration.getCameraDevice(), rtmpUrl);
                }
                default -> FfmpegCommandBuilder.testPatternToMediamtx(ffmpegPath, rtmpUrl);
            };
        }

        return switch (resolveInputMode()) {
            case "camera" -> {
                if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
                    throw new IllegalStateException(
                            "videoInput=camera is only supported on macOS (AVFoundation). Use videoInput: test");
                }
                yield FfmpegCommandBuilder.avfoundationCameraToHls(
                        ffmpegPath, outputDir, configuration.getCameraDevice());
            }
            case "rtmp" -> FfmpegCommandBuilder.rtmpListenToHls(
                    ffmpegPath, outputDir, configuration.getRtmpPort(), stream.getStreamKey());
            default -> FfmpegCommandBuilder.testPatternToHls(ffmpegPath, outputDir);
        };
    }

    public String rtmpPublishUrl(String streamKey, String suffix) {
        String base = configuration.getMediamtxRtmpPublishBase().replaceAll("/$", "");
        return base + "/live/" + streamKey + suffix;
    }

    /** Same-origin path; {@link com.livestream.api.WhepProxyResource} forwards to MediaMTX. */
    public String whepPlaybackUrl(String streamKey, String suffix) {
        return "/whep/live/" + streamKey + suffix + "/whep";
    }

    public List<QualityOption> qualityOptions(String streamKey) {
        if (!isMediamtxDelivery()) {
            return List.of();
        }
        if (!isAbrEnabled()) {
            return List.of(new QualityOption("high", "720p", whepPlaybackUrl(streamKey, ""), 800));
        }
        List<QualityOption> options = new ArrayList<>();
        options.add(new QualityOption("high", "720p", whepPlaybackUrl(streamKey, "_high"), 2000));
        options.add(new QualityOption("mid", "480p", whepPlaybackUrl(streamKey, "_mid"), 900));
        options.add(new QualityOption("low", "360p", whepPlaybackUrl(streamKey, "_low"), 400));
        return options;
    }

    public String hlsPlaybackUrl(Long streamId) {
        return "/hls/" + streamId + "/index.m3u8";
    }

    /** Sets API playback fields on the response (HLS path or WebRTC WHEP URL). */
    public void applyPlaybackUrls(com.livestream.api.dto.StreamResponse response, LiveStream stream) {
        if (isMediamtxDelivery()) {
            List<QualityOption> qualities = qualityOptions(stream.getStreamKey());
            response.setDelivery("webrtc");
            response.setQualities(qualities);
            response.setWebrtcWhepUrl(qualities.isEmpty() ? null : qualities.get(0).getWebrtcWhepUrl());
            response.setPlaybackUrl(null);
        } else {
            response.setDelivery("hls");
            response.setPlaybackUrl(hlsPlaybackUrl(stream.getId()));
            response.setWebrtcWhepUrl(null);
            response.setQualities(null);
        }
        response.setEncodeDegraded(isDegraded(stream.getId()));
    }

    private String resolveInputMode() {
        String mode = configuration.getVideoInput();
        if (mode == null || mode.isBlank()) {
            return configuration.isUseTestVideo() ? "test" : "rtmp";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    public void stopForStream(Long streamId) {
        Process process = processes.remove(streamId);
        degradedStreams.remove(streamId);
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        LOGGER.info("FFmpeg stopped for stream {}", streamId);
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        processes.keySet().forEach(this::stopForStream);
    }

    private void drainLogs(Long streamId, Process process) {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                streamQoSService.recordFfmpegLine(streamId, line);
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("error") || lower.contains("denied") || lower.contains("not found")) {
                    LOGGER.warn("[ffmpeg-{}] {}", streamId, line);
                } else {
                    LOGGER.debug("[ffmpeg-{}] {}", streamId, line);
                }
            }
        } catch (IOException e) {
            LOGGER.debug("FFmpeg log stream closed for stream {}", streamId);
        }
    }
}
