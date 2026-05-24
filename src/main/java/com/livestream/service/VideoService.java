package com.livestream.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.ffmpeg.FfmpegCommandBuilder;
import com.livestream.model.LiveStream;
import io.dropwizard.lifecycle.Managed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();

    @Inject
    public VideoService(LiveStreamConfiguration configuration) {
        this.configuration = configuration;
    }

    public boolean isMediamtxDelivery() {
        return configuration.isMediamtxDelivery();
    }

    public void startForStream(LiveStream stream) throws IOException {
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

        List<String> command = buildFfmpegCommand(ffmpeg.toString(), outputDir, stream);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        processes.put(streamId, process);
        LOGGER.info(
                "FFmpeg started for stream {} (pid={}, delivery={}, input={})",
                streamId,
                process.pid(),
                configuration.getStreamDelivery(),
                resolveInputMode());
        if (isMediamtxDelivery()) {
            LOGGER.info("MediaMTX RTMP publish: {}", rtmpPublishUrl(stream.getStreamKey()));
            LOGGER.info("WebRTC WHEP playback: {}", whepPlaybackUrl(stream.getStreamKey()));
        } else {
            LOGGER.info("HLS playlist: {}", hlsPlaybackUrl(streamId));
        }

        Thread logThread = new Thread(() -> drainLogs(streamId, process), "ffmpeg-log-" + streamId);
        logThread.setDaemon(true);
        logThread.start();
    }

    private List<String> buildFfmpegCommand(String ffmpegPath, Path outputDir, LiveStream stream) {
        if (isMediamtxDelivery()) {
            String rtmpUrl = rtmpPublishUrl(stream.getStreamKey());
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

    public String rtmpPublishUrl(String streamKey) {
        String base = configuration.getMediamtxRtmpPublishBase().replaceAll("/$", "");
        return base + "/live/" + streamKey;
    }

    public String whepPlaybackUrl(String streamKey) {
        String base = configuration.getMediamtxWebrtcBase().replaceAll("/$", "");
        return base + "/live/" + streamKey + "/whep";
    }

    public String hlsPlaybackUrl(Long streamId) {
        return "/hls/" + streamId + "/index.m3u8";
    }

    /** Sets API playback fields on the response (HLS path or WebRTC WHEP URL). */
    public void applyPlaybackUrls(com.livestream.api.dto.StreamResponse response, LiveStream stream) {
        if (isMediamtxDelivery()) {
            response.setDelivery("webrtc");
            response.setWebrtcWhepUrl(whepPlaybackUrl(stream.getStreamKey()));
            response.setPlaybackUrl(null);
        } else {
            response.setDelivery("hls");
            response.setPlaybackUrl(hlsPlaybackUrl(stream.getId()));
            response.setWebrtcWhepUrl(null);
        }
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
