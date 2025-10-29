/* ------------------------------------------------------
 * Copyright [2025] [Copyright 2025 Alfonso Antolínez García and Marina Antolínez Cabrero]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is part of the CropScope(R) suite.
 * Authors:
 * - Alfonso Antolínez García
 * - Marina Antolínez Cabrero
 * -------------------------------------------------------- */

package com.cropscope.cloudbackuptool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class Performance {
    private long startNanos;
    private long endNanos;
    private boolean sessionActive;

    private String sessionStartUtcStamp;

    private int totalCrops = 0;

    private final Map<String, Integer> countsBySize = new LinkedHashMap<String, Integer>();

    public Performance() {
        reset();
    }

    public void startSession() {
        reset();
        this.startNanos = System.nanoTime();
        this.sessionActive = true;
        this.sessionStartUtcStamp = ExportUtils.nowUtcStamp();
    }

    public void stopSession() {
        if (sessionActive) {
            this.endNanos = System.nanoTime();
            this.sessionActive = false;
        }
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    public boolean isSessionActiveOrFinished() {
        return sessionStartUtcStamp != null;
    }

    public void incrementCropCount() {
        if (sessionActive) {
            totalCrops++;
        }
    }

    public void incrementCropCountForSize(int w, int h) {
        if (!sessionActive) return;
        totalCrops++;
        String key = w + "x" + h;
        Integer cur = countsBySize.get(key);
        countsBySize.put(key, (cur == null ? 1 : (cur.intValue() + 1)));
    }

    public int getTotalCrops() {
        return totalCrops;
    }

    public double getSessionTimeInMinutes() {
        if (startNanos == 0L || endNanos <= startNanos) return 0.0;
        double secs = (endNanos - startNanos) / 1_000_000_000.0;
        return secs / 60.0;
    }

    public double getImagesPerSecond() {
        if (startNanos == 0L || endNanos <= startNanos) return 0.0;
        double secs = (endNanos - startNanos) / 1_000_000_000.0;
        return (secs > 0) ? (totalCrops / secs) : 0.0;
    }

    public double getImagesPerMinute() {
        return getImagesPerSecond() * 60.0;
    }

    public String getMetricsReport() {
        if (!isSessionActiveOrFinished()) {
            return "No performance data available. Please start and stop a performance session.";
        }
        Snapshot s = getSnapshot();
        double minutes = s.elapsedSeconds / 60.0;
        double totalPerSec = (s.elapsedSeconds > 0 ? (s.totalCrops / (double) s.elapsedSeconds) : 0.0);
        double totalPerMin = totalPerSec * 60.0;

        StringBuilder sb = new StringBuilder(512);
        sb.append("Total crops taken: ").append(s.totalCrops).append('\n');
        sb.append("Time session in minutes: ")
                .append(String.format(Locale.ROOT, "%.2f", minutes)).append('\n');
        sb.append("Total images cropped per second: ")
                .append(String.format(Locale.ROOT, "%.2f", totalPerSec)).append('\n');
        sb.append("Total images cropped per minute: ")
                .append(String.format(Locale.ROOT, "%.2f", totalPerMin)).append('\n');

        if (!s.countsBySize.isEmpty()) {
            sb.append("\nBy crop size:\n");
            for (Map.Entry<String, Integer> e : s.countsBySize.entrySet()) {
                String size = e.getKey();
                int count = e.getValue();
                double perSec = (s.elapsedSeconds > 0 ? (count / (double) s.elapsedSeconds) : 0.0);
                double perMin = perSec * 60.0;

                sb.append("  ").append(size).append(":\n");
                sb.append("   - Total images cropped: ").append(count).append('\n');
                sb.append("   - Total images cropped per second: ")
                        .append(String.format(Locale.ROOT, "%.2f", perSec)).append('\n');
                sb.append("   - Total images cropped per minute: ")
                        .append(String.format(Locale.ROOT, "%.2f", perMin)).append('\n');
            }
        }

        return sb.toString();
    }


    public Snapshot getSnapshot() {
        final long now = System.nanoTime();
        final long end = sessionActive ? now : endNanos;
        long elapsedSeconds = 0L;
        if (startNanos > 0L && end > startNanos) {
            elapsedSeconds = (end - startNanos) / 1_000_000_000L;
            if (elapsedSeconds <= 0L) elapsedSeconds = 1L;
        }

        Snapshot s = new Snapshot();
        s.sessionStartUtcStamp = (sessionStartUtcStamp != null ? sessionStartUtcStamp : ExportUtils.nowUtcStamp());
        s.elapsedSeconds = elapsedSeconds;
        s.totalCrops = totalCrops;
        s.countsBySize = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(countsBySize));
        return s;
    }

    private void reset() {
        startNanos = 0L;
        endNanos = 0L;
        sessionActive = false;
        sessionStartUtcStamp = null;
        totalCrops = 0;
        countsBySize.clear();
    }

    public static class Snapshot {
        public String sessionStartUtcStamp;
        public long elapsedSeconds;
        public int totalCrops;
        public Map<String, Integer> countsBySize;
    }
}
