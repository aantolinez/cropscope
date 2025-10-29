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

package com.cropscope.videoframeextractor;

import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

public class FrameExtractionWorker extends SwingWorker<Void, String> {
    private final File input;
    private final File outDir;
    private final List<VideoFrameExtractor.Segment> segments;
    private final String projectSetting;
    private final String userSetting;
    private ExtractionOptions extractionOptions;
    private String filterString;
    private final JProgressBar progressBar;
    private final JLabel lblPercent;
    private final JLabel lblDropped;

    private int droppedTotal = 0;
    private final List<String> segReports = new ArrayList<String>();
    private int totalFramesExtracted = 0;
    private long extractionStartTime;
    private long extractionEndTime;
    private double videoDuration = -1;
    private final Map<String, Double> segmentProcessingTimes = new HashMap<>();
    private final Map<String, Integer> segmentFrameCounts = new HashMap<>();
    private final Map<String, Object> additionalMetrics = new HashMap<>();
    private volatile Process currentProcess;
    private final ExportManager exportManager;

    public FrameExtractionWorker(File in, File out, List<VideoFrameExtractor.Segment> segs,
                                 String projectSetting, String userSetting,
                                 JProgressBar progressBar, JLabel lblPercent, JLabel lblDropped) {
        this(in, out, segs, projectSetting, userSetting, null, null, progressBar, lblPercent, lblDropped);
    }

    public FrameExtractionWorker(File in, File out, List<VideoFrameExtractor.Segment> segs,
                                 String projectSetting, String userSetting,
                                 ExtractionOptions extractionOptions, String filterString,
                                 JProgressBar progressBar, JLabel lblPercent, JLabel lblDropped) {
        this.input = in;
        this.outDir = out;
        this.segments = segs;
        this.projectSetting = projectSetting;
        this.userSetting = userSetting;
        this.exportManager = new ExportManager(out);
        this.progressBar = progressBar;
        this.lblPercent = lblPercent;
        this.lblDropped = lblDropped;

        if (extractionOptions != null) {
            this.extractionOptions = extractionOptions;
            this.filterString = filterString;
        } else {
            this.extractionOptions = new ExtractionOptions();
            this.filterString = "";
        }
    }

    public void requestStopAndDestroy() {
        cancel(true);
        Process p = currentProcess;
        if (p != null) {
            try {
                p.destroy();
            } catch (Exception ignore) {
            }
        }
    }

    public void setExtractionOptions(ExtractionOptions options) {
        this.extractionOptions = options;
    }

    public void setFilterString(String filterString) {
        this.filterString = filterString;
    }

    @Override
    protected Void doInBackground() throws Exception {
        extractionStartTime = System.currentTimeMillis();
        long originalVideoSize = input.length();
        additionalMetrics.put("originalVideoSizeBytes", originalVideoSize);
        try {
            videoDuration = getVideoDuration(input);
        } catch (Exception e) {
            publish("Warning: Could not determine video duration: " + e.getMessage());
        }
        additionalMetrics.put("systemInfo", getSystemInfo());
        additionalMetrics.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        try {
            additionalMetrics.put("ffmpegVersion", getFFmpegVersion());
        } catch (Exception e) {
            publish("Warning: Could not get FFmpeg version: " + e.getMessage());
        }
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        additionalMetrics.put("initialMemoryUsageBytes", initialMemory);

        int segIdx = 0;
        for (VideoFrameExtractor.Segment seg : segments) {
            if (isCancelled()) break;
            segIdx++;
            publish("Segment " + segIdx + "/" + segments.size() + " → " + seg);

            long segmentStartTime = System.currentTimeMillis();

            List<String> cmd = new ArrayList<String>();
            cmd.add("ffmpeg");
            cmd.add("-y");
            cmd.add("-hide_banner");
            if (!seg.isFull() && seg.start >= 0) {
                cmd.add("-ss");
                cmd.add(String.valueOf(seg.start));
            }
            if (!seg.isFull() && seg.end >= 0) {
                cmd.add("-to");
                cmd.add(String.valueOf(seg.end));
            }
            boolean usingMpdecimate = (filterString != null && filterString.contains("mpdecimate"));
            String vfFromPanel = filterString;
            cmd.add("-i");
            cmd.add(input.getAbsolutePath());

            if (vfFromPanel != null) {
                cmd.add("-vf");
                cmd.add(vfFromPanel);
            }
            cmd.add("-vsync");
            cmd.add("vfr");
            cmd.add("-frame_pts");
            cmd.add("1");
            cmd.add("-f");
            cmd.add("image2");
            cmd.add("-c:v");
            cmd.add("png");
            String prefixRaw = (seg.prefix == null) ? "" : seg.prefix.trim();
            String safeBase = prefixRaw.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeBase.length() == 0) {
                String ss = seg.isFull() ? "full" : String.valueOf(seg.start);
                String ee = seg.isFull() ? "full" : String.valueOf(seg.end);
                safeBase = "seg_" + ss + "_" + ee;
            }
            File perPrefixDir = new File(outDir, safeBase);
            try {
                Files.createDirectories(perPrefixDir.toPath());
            } catch (Exception mkex) {
                System.err.println("WARN: could not create subfolder: " + perPrefixDir + " : " + mkex);
                perPrefixDir = outDir;
            }
            final String namePrefix = safeBase + "_";
            String[] existing = perPrefixDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File d, String name) {
                    String n = name.toLowerCase();
                    return n.startsWith(namePrefix.toLowerCase()) && n.endsWith(".png");
                }
            });
            final int beforeCount = (existing == null) ? 0 : existing.length;
            int startNumber = beforeCount + 1;
            cmd.add("-start_number");
            cmd.add(String.valueOf(startNumber));
            String pattern = new File(perPrefixDir, namePrefix + "%06d.png").getAbsolutePath();
            cmd.add(pattern);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            currentProcess = pb.start();
            Process p = currentProcess;

            final boolean mp = usingMpdecimate;
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            while ((line = err.readLine()) != null) {
                if (mp) {
                    int inc = parseMpdecimateDropIncrement(line);
                    if (inc > 0) {
                        droppedTotal += inc;
                        publish("Dropped +" + inc + " (total " + droppedTotal + ")");
                    }
                }
            }
            int exit = p.waitFor();
            if (exit != 0) {
                publish("FFmpeg exited with code " + exit);
            }

            currentProcess = null;
            int afterCount = 0;
            String[] afterFiles = perPrefixDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File d, String name) {
                    String n = name.toLowerCase();
                    return n.endsWith(".png");
                }
            });
            if (afterFiles != null) afterCount = afterFiles.length;

            int written = Math.max(0, afterCount - beforeCount);
            totalFramesExtracted += written;
            long segmentEndTime = System.currentTimeMillis();
            double segmentTime = (segmentEndTime - segmentStartTime) / 1000.0;
            String segmentKey = seg.isFull() ? "full_video" : (seg.start + "_" + seg.end);
            segmentProcessingTimes.put(segmentKey, segmentTime);
            segmentFrameCounts.put(segmentKey, written);

            String segLabel = seg.isFull() ? "full" : (seg.start + "–" + seg.end + "s");
            segReports.add(String.format("[%s] prefix=\"%s\" → wrote %d PNG (out: %s)",
                    segLabel, prefixRaw, written, perPrefixDir.getAbsolutePath()));
            int percent = (int) Math.round((segIdx * 100.0) / Math.max(1, segments.size()));
            final int pct = Math.min(100, Math.max(0, percent));
            publish("PROGRESS " + pct);
        }

        extractionEndTime = System.currentTimeMillis();
        long totalExtractedFramesSize = calculateTotalExtractedSize(outDir);
        additionalMetrics.put("totalExtractedFramesSizeBytes", totalExtractedFramesSize);
        if (originalVideoSize > 0) {
            double compressionRatio = (double) totalExtractedFramesSize / originalVideoSize;
            additionalMetrics.put("compressionRatio", compressionRatio);
        } else {
            additionalMetrics.put("compressionRatio", JSONObject.NULL);
        }
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        additionalMetrics.put("finalMemoryUsageBytes", finalMemory);
        additionalMetrics.put("memoryUsageDeltaBytes", finalMemory - initialMemory);
        additionalMetrics.put("filterString", filterString);
        additionalMetrics.put("segmentProcessingTimes", segmentProcessingTimes);
        additionalMetrics.put("segmentFrameCounts", segmentFrameCounts);
        String segmentsMetadataPath = exportManager.exportSegmentsMetadata(
                segments,
                input.getAbsolutePath(),
                outDir.getAbsolutePath(),
                projectSetting,
                userSetting,
                extractionOptions,
                filterString
        );
        publish("Segments metadata saved to: " + segmentsMetadataPath);
        String metricsPath = exportManager.exportMetricsToJson(
                input, outDir, totalFramesExtracted, videoDuration,
                extractionStartTime, extractionEndTime, droppedTotal, additionalMetrics);
        publish("Metrics saved to: " + metricsPath);

        return null;
    }

    @Override
    protected void process(List<String> chunks) {
        for (String s : chunks) {
            if (s.startsWith("PROGRESS ")) {
                int pct = Integer.parseInt(s.substring(9).trim());
                if (progressBar != null) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(pct);
                }
                if (lblPercent != null) {
                    lblPercent.setText(pct + "%");
                }
            } else if (s.startsWith("Dropped +")) {
                if (lblDropped != null) {
                    lblDropped.setText("Dropped: " + droppedTotal);
                }
            }
        }
    }

    private int parseMpdecimateDropIncrement(String line) {
        int idx = line.indexOf("drop_count");
        if (idx < 0) return 0;
        int colon = line.indexOf(':', idx);
        if (colon < 0 || colon + 1 >= line.length()) return 0;
        int i = colon + 1;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        int j = i;
        while (j < line.length() && Character.isDigit(line.charAt(j))) j++;
        if (j == i) return 0;
        try {
            return Integer.parseInt(line.substring(i, j));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double getVideoDuration(File videoFile) throws IOException, InterruptedException {
        List<String> cmd = Arrays.asList(
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", videoFile.getAbsolutePath()
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String durationStr = reader.readLine();
        int exitCode = process.waitFor();

        if (exitCode == 0 && durationStr != null) {
            return Double.parseDouble(durationStr.trim());
        }
        throw new IOException("Failed to get video duration. Exit code: " + exitCode);
    }

    private String getFFmpegVersion() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String firstLine = reader.readLine();
        process.waitFor();

        if (firstLine != null) {
            return firstLine.split(" ")[2];
        }
        throw new IOException("Failed to get FFmpeg version");
    }

    private String getSystemInfo() {
        return String.format("Java: %s, OS: %s",
                System.getProperty("java.version"),
                System.getProperty("os.name"));
    }

    private long calculateTotalExtractedSize(File outputDir) {
        long totalSize = 0;
        File[] files = outputDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                    totalSize += file.length();
                } else if (file.isDirectory()) {
                    totalSize += calculateDirectorySize(file);
                }
            }
        }

        return totalSize;
    }

    private long calculateDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public List<String> getSegReports() {
        return segReports;
    }

    public int getTotalFramesExtracted() {
        return totalFramesExtracted;
    }

    public int getDroppedTotal() {
        return droppedTotal;
    }

    public double getVideoDuration() {
        return videoDuration;
    }

    public long getExtractionStartTime() {
        return extractionStartTime;
    }

    public long getExtractionEndTime() {
        return extractionEndTime;
    }
}

