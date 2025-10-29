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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ExportManager {

    private final File sinkDirectory;

    public ExportManager(File sinkDirectory) {
        this.sinkDirectory = sinkDirectory;
    }

    public String exportSegmentsMetadata(List<VideoFrameExtractor.Segment> segments,
                                         String videoPath,
                                         String sinkDirectoryPath,
                                         String projectSetting,
                                         String userSetting,
                                         ExtractionOptions options,
                                         String filterString) {
        try {
            JSONObject json = new JSONObject();
            json.put("videoPath", videoPath);
            json.put("sinkDirectory", sinkDirectoryPath);
            json.put("project", projectSetting);
            json.put("user", userSetting);
            String isoDateTime = Instant.now()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            json.put("extractionDateTime", isoDateTime);
            JSONObject optionsJson = new JSONObject();
            optionsJson.put("dropDuplicates", options.isDropDuplicates());
            optionsJson.put("advancedDropDuplicates", options.isAdvancedDropDuplicates());
            optionsJson.put("useCustomMpdecimateParams", options.isUseCustomMpdecimateParams());

            if (options.isUseCustomMpdecimateParams()) {
                optionsJson.put("mpdecimateHi", options.getMpdecimateHi());
                optionsJson.put("mpdecimateLo", options.getMpdecimateLo());
                optionsJson.put("mpdecimateFrac", options.getMpdecimateFrac());
            }

            optionsJson.put("useUniformSampling", options.isUseUniformSampling());
            if (options.isUseUniformSampling()) {
                optionsJson.put("uniformSamplingFps", options.getUniformSamplingFps());
            }

            optionsJson.put("useSceneChanges", options.isUseSceneChanges());
            if (options.isUseSceneChanges()) {
                optionsJson.put("sceneThreshold", options.getSceneThreshold());
            }

            optionsJson.put("skipDarkFrames", options.isSkipDarkFrames());
            if (options.isSkipDarkFrames()) {
                optionsJson.put("minLumaYavg", options.getMinLumaYavg());
            }

            json.put("extractionOptions", optionsJson);
            json.put("filterString", filterString);
            JSONObject segmentsJson = new JSONObject();
            for (int i = 0; i < segments.size(); i++) {
                VideoFrameExtractor.Segment segment = segments.get(i);
                JSONObject segmentJson = new JSONObject();
                segmentJson.put("start", segment.start);
                segmentJson.put("end", segment.end);
                segmentJson.put("prefix", segment.prefix);
                segmentJson.put("isFull", segment.isFull());
                segmentsJson.put("segment_" + (i + 1), segmentJson);
            }
            json.put("segments", segmentsJson);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String fileName = "video_frame_extraction_segments_" + timestamp + ".json";
            File outputFile = new File(sinkDirectory, fileName);
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(json.toString(2));
            }

            return outputFile.getAbsolutePath();
        } catch (IOException e) {
            return "Error saving segments metadata: " + e.getMessage();
        }
    }

    public String exportMetricsToJson(File input, File outDir, int totalFramesExtracted,
                                      double videoDuration, long extractionStartTime,
                                      long extractionEndTime, int droppedTotal,
                                      Map<String, Object> additionalMetrics) {
        try {
            JSONObject json = new JSONObject();
            json.put("videoPath", input.getAbsolutePath());
            json.put("sinkDirectory", outDir.getAbsolutePath());
            json.put("totalFramesExtracted", totalFramesExtracted);
            json.put("videoDurationSeconds", videoDuration);
            json.put("extractionStartTime", extractionStartTime);
            json.put("extractionEndTime", extractionEndTime);
            double extractionTimeSeconds = (extractionEndTime - extractionStartTime) / 1000.0;
            double extractionTimeMinutes = extractionTimeSeconds / 60.0;
            double fps = (videoDuration > 0) ? totalFramesExtracted / videoDuration : 0;
            double fpm = fps * 60;
            double processingRate = (totalFramesExtracted * 1000.0) / (extractionEndTime - extractionStartTime);
            double duplicateRate = (totalFramesExtracted + droppedTotal > 0) ?
                    (droppedTotal * 100.0) / (totalFramesExtracted + droppedTotal) : 0;
            double realTimeFactor = (videoDuration > 0) ? extractionTimeSeconds / videoDuration : 0;

            json.put("extractionTimeSeconds", extractionTimeSeconds);
            json.put("extractionTimeMinutes", extractionTimeMinutes);
            json.put("framesPerSecond", fps);
            json.put("framesPerMinute", fpm);
            json.put("processingRate", processingRate);
            json.put("duplicateFrameRate", duplicateRate);
            json.put("realTimeFactor", realTimeFactor);
            json.put("droppedFrames", droppedTotal);
            Long originalVideoSize = (Long) additionalMetrics.get("originalVideoSizeBytes");
            Long totalExtractedFramesSize = (Long) additionalMetrics.get("totalExtractedFramesSizeBytes");
            Double compressionRatio = (Double) additionalMetrics.get("compressionRatio");

            json.put("originalVideoSizeBytes", originalVideoSize);
            json.put("totalExtractedFramesSizeBytes", totalExtractedFramesSize);
            json.put("compressionRatio", compressionRatio);
            JSONObject additional = new JSONObject(additionalMetrics);
            json.put("additionalMetrics", additional);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String fileName = "video_frame_extraction_metrics_" + timestamp + ".json";
            File outputFile = new File(outDir, fileName);
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(json.toString(2));
            }

            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            return "Error saving metrics: " + e.getMessage();
        }
    }
}