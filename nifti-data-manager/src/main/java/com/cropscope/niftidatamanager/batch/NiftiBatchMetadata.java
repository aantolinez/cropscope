/* ------------------------------------------------------
* This is file is part of the CropScope(R) suite.
* Authors:
* - Alfonso Antolínez García
* - Marina Antolínez Cabrero
--------------------------------------------------------*/
package com.cropscope.niftidatamanager.batch;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
public class NiftiBatchMetadata {
    private String sourceDir;
    private String sinkDir;
    private int totalFilesProcessed;
    private int totalSlicesExported;
    private long processingTimeMs;
    private String project;
    private String sessionId;
    private int exportSequence;
    private List<NiftiExportInfo> exports;
    private Instant exportedAtUtc;
    private Instant sessionFolderUtc;
    private String user;
    private ProcessingMetrics metrics;
    private int totalLowQualitySlicesSkipped;
    private int totalFilesSkipped;

    public NiftiBatchMetadata() {
        this.exports = new ArrayList<>();
        this.sessionId = java.util.UUID.randomUUID().toString();
        this.exportedAtUtc = Instant.now();
        this.sessionFolderUtc = Instant.now();
        this.user = System.getProperty("user.name", "batch-user");
        this.project = "NIfTI Batch Processing";
        this.exportSequence = 0;
        this.totalLowQualitySlicesSkipped = 0;
        this.totalFilesSkipped = 0;
    }

    public String getSourceDir() {
        return sourceDir;
    }
    public void setSourceDir(String sourceDir) { this.sourceDir = sourceDir; }
    public String getSinkDir() { return sinkDir; }
    public void setSinkDir(String sinkDir) { this.sinkDir = sinkDir; }
    public int getTotalFilesProcessed() { return totalFilesProcessed; }
    public void setTotalFilesProcessed(int totalFilesProcessed) { this.totalFilesProcessed = totalFilesProcessed; }
    public int getTotalSlicesExported() { return totalSlicesExported; }
    public void setTotalSlicesExported(int totalSlicesExported) { this.totalSlicesExported = totalSlicesExported; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public int getExportSequence() { return exportSequence; }
    public void setExportSequence(int exportSequence) { this.exportSequence = exportSequence; }
    public List<NiftiExportInfo> getExports() { return exports; }
    public void setExports(List<NiftiExportInfo> exports) { this.exports = exports; }
    public Instant getExportedAtUtc() { return exportedAtUtc; }
    public void setExportedAtUtc(Instant exportedAtUtc) { this.exportedAtUtc = exportedAtUtc; }
    public Instant getSessionFolderUtc() { return sessionFolderUtc; }
    public void setSessionFolderUtc(Instant sessionFolderUtc) { this.sessionFolderUtc = sessionFolderUtc; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public ProcessingMetrics getMetrics() { return metrics; }
    public void setMetrics(ProcessingMetrics metrics) { this.metrics = metrics; }
    public int getTotalLowQualitySlicesSkipped() { return totalLowQualitySlicesSkipped; }
    public void setTotalLowQualitySlicesSkipped(int totalLowQualitySlicesSkipped) { this.totalLowQualitySlicesSkipped = totalLowQualitySlicesSkipped; }
    public int getTotalFilesSkipped() { return totalFilesSkipped; }
    public void setTotalFilesSkipped(int totalFilesSkipped) { this.totalFilesSkipped = totalFilesSkipped; }

    public static class NiftiExportInfo {
        private String originalImagePath;
        private String originalFilename;
        private String sinkDir;
        private String savedAs;
        private String annotation;
        private int originalWidth;
        private int originalHeight;
        private int originalDepth;
        private int numDimensions;
        private int exportDimension;
        private int numSlicesExported;
        private String dataType;
        private long fileSizeBytes;
        private String project;
        private String user;
        private String viewName;
        private JSONArray cropOperations;
        private int lowQualitySlicesSkipped;
        private String skipReason;

        public String getOriginalImagePath() {
            return originalImagePath;
        }
        public void setOriginalImagePath(String originalImagePath) { this.originalImagePath = originalImagePath; }
        public String getOriginalFilename() { return originalFilename; }
        public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
        public String getSinkDir() { return sinkDir; }
        public void setSinkDir(String sinkDir) { this.sinkDir = sinkDir; }
        public String getSavedAs() { return savedAs; }
        public void setSavedAs(String savedAs) { this.savedAs = savedAs; }
        public String getAnnotation() { return annotation; }
        public void setAnnotation(String annotation) { this.annotation = annotation; }
        public int getOriginalWidth() { return originalWidth; }
        public void setOriginalWidth(int originalWidth) { this.originalWidth = originalWidth; }
        public int getOriginalHeight() { return originalHeight; }
        public void setOriginalHeight(int originalHeight) { this.originalHeight = originalHeight; }
        public int getOriginalDepth() { return originalDepth; }
        public void setOriginalDepth(int originalDepth) { this.originalDepth = originalDepth; }
        public int getNumDimensions() { return numDimensions; }
        public void setNumDimensions(int numDimensions) { this.numDimensions = numDimensions; }
        public int getExportDimension() { return exportDimension; }
        public void setExportDimension(int exportDimension) { this.exportDimension = exportDimension; }
        public int getNumSlicesExported() { return numSlicesExported; }
        public void setNumSlicesExported(int numSlicesExported) { this.numSlicesExported = numSlicesExported; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
        public String getProject() { return project; }
        public void setProject(String project) { this.project = project; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getViewName() { return viewName; }
        public void setViewName(String viewName) { this.viewName = viewName; }
        public JSONArray getCropOperations() { return cropOperations; }
        public void setCropOperations(JSONArray cropOperations) { this.cropOperations = cropOperations; }
        public int getLowQualitySlicesSkipped() { return lowQualitySlicesSkipped; }
        public void setLowQualitySlicesSkipped(int lowQualitySlicesSkipped) { this.lowQualitySlicesSkipped = lowQualitySlicesSkipped; }
        public String getSkipReason() { return skipReason; }
        public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
    }
    public static class ProcessingMetrics {
        private double filesProcessedPerSecond;
        private double slicesExportedPerSecond;
        private double averageSlicesPerFile;
        private long totalProcessingTimeMs;
        public ProcessingMetrics(int totalFiles, int totalSlices, long totalTimeMs) {
            this.totalProcessingTimeMs = totalTimeMs;
            this.filesProcessedPerSecond = totalTimeMs > 0 ? (double) totalFiles * 1000 / totalTimeMs : 0.0;
            this.slicesExportedPerSecond = totalTimeMs > 0 ? (double) totalSlices * 1000 / totalTimeMs : 0.0;
            this.averageSlicesPerFile = totalFiles > 0 ? (double) totalSlices / totalFiles : 0.0;
        }
        public double getFilesProcessedPerSecond() { return filesProcessedPerSecond; }
        public double getSlicesExportedPerSecond() { return slicesExportedPerSecond; }
        public double getAverageSlicesPerFile() { return averageSlicesPerFile; }
        public long getTotalProcessingTimeMs() { return totalProcessingTimeMs; }
    }
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("sourceDir", sourceDir);
        json.put("sinkDir", sinkDir);
        json.put("totalFilesProcessed", totalFilesProcessed);
        json.put("totalSlicesExported", totalSlicesExported);
        json.put("processingTimeMs", processingTimeMs);
        json.put("project", project);
        json.put("sessionId", sessionId);
        json.put("exportSequence", exportSequence);
        json.put("totalFilesSkipped", totalFilesSkipped);
        json.put("totalLowQualitySlicesSkipped", totalLowQualitySlicesSkipped);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC);
        json.put("exportedAtUtc", formatter.format(exportedAtUtc));
        json.put("sessionFolderUtc", formatter.format(sessionFolderUtc));
        json.put("user", user);
        JSONArray exportsArray = new JSONArray();
        for (NiftiExportInfo export : exports) {
            JSONObject exportJson = new JSONObject();
            exportJson.put("originalImagePath", export.getOriginalImagePath());
            exportJson.put("originalFilename", export.getOriginalFilename());
            exportJson.put("sinkDir", export.getSinkDir());
            exportJson.put("savedAs", export.getSavedAs());
            exportJson.put("annotation", export.getAnnotation());
            exportJson.put("originalWidth", export.getOriginalWidth());
            exportJson.put("originalHeight", export.getOriginalHeight());
            exportJson.put("originalDepth", export.getOriginalDepth());
            exportJson.put("numDimensions", export.getNumDimensions());
            exportJson.put("exportDimension", export.getExportDimension());
            exportJson.put("numSlicesExported", export.getNumSlicesExported());
            exportJson.put("dataType", export.getDataType());
            exportJson.put("fileSizeBytes", export.getFileSizeBytes());
            exportJson.put("project", export.getProject());
            exportJson.put("user", export.getUser());
            exportJson.put("viewName", export.getViewName());
            exportJson.put("lowQualitySlicesSkipped", export.getLowQualitySlicesSkipped());
            if (export.getSkipReason() != null) {
                exportJson.put("skipReason", export.getSkipReason());
            }
            if (export.getCropOperations() != null) {
                exportJson.put("crop_operations", export.getCropOperations());
            }

            exportsArray.put(exportJson);
        }
        json.put("exports", exportsArray);
        json.put("filesProcessedPerSecond", metrics.getFilesProcessedPerSecond());
        json.put("slicesExportedPerSecond", metrics.getSlicesExportedPerSecond());
        json.put("averageSlicesPerFile", metrics.getAverageSlicesPerFile());
        JSONObject defaultExportSize = new JSONObject();
        defaultExportSize.put("w", "varies by slice");
        defaultExportSize.put("h", "varies by slice");
        json.put("defaultExportSize", defaultExportSize);

        return json;
    }
    public static void saveMetadata(NiftiBatchMetadata metadata, String outputPath) throws IOException {
        JSONObject json = metadata.toJson();
        try (FileWriter writer = new FileWriter(Paths.get(outputPath).toFile())) {
            writer.write(json.toString(2));
        }
    }
    public static String generateDefaultFilename() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC);
        return "nifti_batch_metadata_" + formatter.format(Instant.now()) + ".json";
    }
}