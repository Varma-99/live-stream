package com.livestream.qos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rolling window for numeric samples (ingest kbps, etc.). */
final class RollingStats {

    private final int maxSamples;
    private final List<Long> samples = new ArrayList<>();

    RollingStats(int maxSamples) {
        this.maxSamples = Math.max(10, maxSamples);
    }

    void add(long value) {
        synchronized (samples) {
            samples.add(value);
            while (samples.size() > maxSamples) {
                samples.remove(0);
            }
        }
    }

    int count() {
        synchronized (samples) {
            return samples.size();
        }
    }

    long average() {
        synchronized (samples) {
            if (samples.isEmpty()) {
                return 0;
            }
            long sum = 0;
            for (long v : samples) {
                sum += v;
            }
            return sum / samples.size();
        }
    }

    long p95() {
        synchronized (samples) {
            if (samples.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);
            int idx = (int) Math.ceil(sorted.size() * 0.95) - 1;
            return sorted.get(Math.max(0, idx));
        }
    }

    double stdDev() {
        synchronized (samples) {
            if (samples.size() < 2) {
                return 0;
            }
            long avg = average();
            double sumSq = 0;
            for (long v : samples) {
                double d = v - avg;
                sumSq += d * d;
            }
            return Math.sqrt(sumSq / samples.size());
        }
    }

    void clear() {
        synchronized (samples) {
            samples.clear();
        }
    }
}
