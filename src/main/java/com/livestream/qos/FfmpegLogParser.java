package com.livestream.qos;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses FFmpeg stderr progress lines for encoder QoS. */
public final class FfmpegLogParser {

    private static final Pattern PROGRESS = Pattern.compile(
            "fps=\\s*([\\d.]+).*?speed=\\s*([\\d.]+)x", Pattern.CASE_INSENSITIVE);
    private static final Pattern BITRATE = Pattern.compile("bitrate=\\s*([\\d.]+)kbits/s", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_DUP =
            Pattern.compile("drop=\\s*(\\d+).*?dup=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private FfmpegLogParser() {}

    public static Optional<EncoderParseResult> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (!lower.contains("fps=") && !lower.contains("speed=")) {
            if (lower.contains("error") || lower.contains("failed")) {
                return Optional.of(EncoderParseResult.error(line.trim()));
            }
            return Optional.empty();
        }

        Matcher progress = PROGRESS.matcher(line);
        if (!progress.find()) {
            return Optional.empty();
        }

        double fps = Double.parseDouble(progress.group(1));
        double speed = Double.parseDouble(progress.group(2));
        Double bitrateKbps = null;
        Matcher br = BITRATE.matcher(line);
        if (br.find()) {
            bitrateKbps = Double.parseDouble(br.group(1));
        }

        long drop = 0;
        long dup = 0;
        Matcher dd = DROP_DUP.matcher(line);
        if (dd.find()) {
            drop = Long.parseLong(dd.group(1));
            dup = Long.parseLong(dd.group(2));
        }

        return Optional.of(new EncoderParseResult(fps, speed, bitrateKbps, drop, dup, null));
    }

    public record EncoderParseResult(
            Double fps,
            Double speedRatio,
            Double bitrateKbps,
            long droppedFrames,
            long duplicatedFrames,
            String errorMessage) {

        static EncoderParseResult error(String message) {
            return new EncoderParseResult(null, null, null, 0, 0, message);
        }

        boolean isError() {
            return errorMessage != null;
        }
    }
}
