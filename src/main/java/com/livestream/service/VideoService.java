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
 * Data Plane: spawns FFmpeg via {@link ProcessBuilder} and writes HLS under {@code data/hls/{streamId}/}.
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
        Files.createDirectories(outputDir);

        List<String> command = buildFfmpegCommand(ffmpeg.toString(), outputDir, stream);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        processes.put(streamId, process);
        LOGGER.info("FFmpeg started for stream {} (pid={}, input={})", streamId, process.pid(), resolveInputMode());
        LOGGER.info("HLS playlist: {}", playbackUrl(streamId));

        Thread logThread = new Thread(() -> drainLogs(streamId, process), "ffmpeg-log-" + streamId);
        logThread.setDaemon(true);
        logThread.start();
    }

    private List<String> buildFfmpegCommand(String ffmpegPath, Path outputDir, LiveStream stream) {
        String mode = resolveInputMode();

        return switch (mode) {
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

    public String playbackUrl(Long streamId) {
        return "/hls/" + streamId + "/index.m3u8";
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
