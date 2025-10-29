/* ------------------------------------------------------
* This is file is part of the CropScope(R) suite.
* Authors:
* - Alfonso Antolínez García
* - Marina Antolínez Cabrero
--------------------------------------------------------*/

package com.cropscope.niftidatamanager.batch;


import com.cropscope.niftidatamanager.NiftiImage;
import com.cropscope.niftidatamanager.NiftiHeader;
import com.cropscope.niftidatamanager.DataType;
import com.cropscope.niftidatamanager.exceptions.NiftiException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;
public class BatchNiftiProcessor {
    private static final int MODE_SINGLE_VIEW = 0;
    private static final int MODE_MULTI_VIEW = 4;

    private static final int MIN_DIMENSION = 64;
    private static final double MAX_ASPECT_RATIO = 4.0;
    private static final double MIN_STANDARD_DEVIATION = 5.0;
    private static final double MAX_BACKGROUND_RATIO = 0.95;

    public static void processDirectoryTree(String sourceDirectory, String sinkDirectory, int mode)
            throws IOException {
        Path sourcePath = Paths.get(sourceDirectory);
        Path sinkPath = Paths.get(sinkDirectory);
        Files.createDirectories(sinkPath);
        System.out.println("Scanning directory: " + sourcePath.toAbsolutePath());
        System.out.println("Output directory: " + sinkPath.toAbsolutePath());
        System.out.println("Processing mode: " + (mode == MODE_MULTI_VIEW ? "Multi-view (all anatomical perspectives)" : "Single-view (dimension " + mode + ")"));
        List<Path> niftiFiles = findNiftiFiles(sourcePath);
        System.out.println("Found " + niftiFiles.size() + " NIfTI files");
        List<JSONObject> exportInfos = new ArrayList<>();
        List<String> skippedFiles = new ArrayList<>();
        Instant startTime = Instant.now();
        int totalFilesProcessed = 0;
        int totalSlicesExported = 0;
        for (Path niftiFile : niftiFiles) {
            try {
                NiftiImage image = NiftiImage.read(niftiFile.toString());
                NiftiHeader header = image.getHeader();
                short[] dims = header.getDimensions();
                int ndim = dims[0];
                if (mode == MODE_MULTI_VIEW) {
                    if (ndim >= 3) {
                        List<ExportResult> results = exportAllAnatomicalViews(niftiFile, sinkPath, header, image);
                        for (ExportResult result : results) {
                            exportInfos.add(result.exportInfo);
                            totalSlicesExported += result.slicesExported;
                        }
                        totalFilesProcessed++;
                        System.out.println("✓ Exported " + results.size() + " anatomical views");
                    } else if (ndim == 2) {
                        Path fileOutputDir = sinkPath.resolve(getBaseFilename(niftiFile.getFileName().toString()));
                        Files.createDirectories(fileOutputDir);
                        Path axialDir = fileOutputDir.resolve("axial_slices");
                        ExportResult result = exportSlicesForView(image, header, niftiFile,
                                axialDir, getBaseFilename(niftiFile.getFileName().toString()), 1, "axial");
                        exportInfos.add(result.exportInfo);
                        totalSlicesExported += result.slicesExported;
                        totalFilesProcessed++;
                        System.out.println("✓ Exported 2D file as axial view");
                    } else {
                        String skipMessage = String.format(
                                "Skipping %s: Unsupported dimension %d", niftiFile.getFileName(), ndim);
                        System.out.println("  " + skipMessage);
                        skippedFiles.add(skipMessage);
                    }
                } else {
                    if (mode < 0 || mode >= ndim) {
                        String skipMessage = String.format(
                                "Skipping %s: Requested dimension %d is invalid for %dD image (valid dimensions: 0-%d)",
                                niftiFile.getFileName(), mode, ndim, ndim - 1
                        );
                        System.out.println("  " + skipMessage);
                        skippedFiles.add(skipMessage);
                        continue;
                    }
                    System.out.println("\nProcessing: " + niftiFile.getFileName());
                    ExportResult result = exportAllSlicesWithMetadata(niftiFile, sinkPath, mode, header, image);
                    exportInfos.add(result.exportInfo);
                    totalSlicesExported += result.slicesExported;
                    totalFilesProcessed++;
                    System.out.println("✓ Exported " + result.slicesExported + " slices");
                }
            } catch (Exception e) {
                String errorMessage = "Failed to process " + niftiFile.getFileName() + ": " + e.getMessage();
                System.err.println("✗ " + errorMessage);
                skippedFiles.add(errorMessage);
                e.printStackTrace();
            }
        }
        Instant endTime = Instant.now();
        long processingTimeMs = Duration.between(startTime, endTime).toMillis();
        System.out.println("\n=== PROCESSING COMPLETE ===");
        System.out.println("Files processed: " + totalFilesProcessed);
        System.out.println("Total slices exported: " + totalSlicesExported);
        System.out.println("Processing time: " + processingTimeMs + " ms");
        System.out.println("Output location: " + sinkPath.toAbsolutePath());
        if (!skippedFiles.isEmpty()) {
            System.out.println("\n=== SKIPPED/FAILED FILES (" + skippedFiles.size() + ") ===");
            for (String skipped : skippedFiles) {
                System.out.println("  - " + skipped);
            }
        }
        createMetadataFile(sourceDirectory, sinkDirectory, totalFilesProcessed,
                totalSlicesExported, processingTimeMs, exportInfos, sinkPath.toString());
    }

    private static class ExportResult {
        int slicesExported;
        JSONObject exportInfo;

        ExportResult(int slicesExported, JSONObject exportInfo) {
            this.slicesExported = slicesExported;
            this.exportInfo = exportInfo;
        }
    }

    private static List<ExportResult> exportAllAnatomicalViews(Path niftiFile, Path sinkDirectory,
                                                               NiftiHeader header, NiftiImage image) throws IOException {
        short[] dims = header.getDimensions();
        String baseFilename = getBaseFilename(niftiFile.getFileName().toString());
        Path fileOutputDir = sinkDirectory.resolve(baseFilename);
        Files.createDirectories(fileOutputDir);
        List<ExportResult> results = new ArrayList<>();
        Path axialDir = fileOutputDir.resolve("axial_slices");
        ExportResult axialResult = exportSlicesForView(image, header, niftiFile,
                axialDir, baseFilename, 2, "axial");
        results.add(axialResult);
        Path coronalDir = fileOutputDir.resolve("coronal_slices");
        ExportResult coronalResult = exportSlicesForView(image, header, niftiFile,
                coronalDir, baseFilename, 1, "coronal");
        results.add(coronalResult);
        Path sagittalDir = fileOutputDir.resolve("sagittal_slices");
        ExportResult sagittalResult = exportSlicesForView(image, header, niftiFile,
                sagittalDir, baseFilename, 0, "sagittal");
        results.add(sagittalResult);
        return results;
    }

    private static ExportResult exportSlicesForView(NiftiImage image, NiftiHeader header,
                                                    Path niftiFile, Path viewOutputDir,
                                                    String baseFilename, int dimension,
                                                    String viewName) throws IOException {
        short[] dims = header.getDimensions();
        int numSlices = dims[dimension + 1];
        int[] outputDims = getOutputDimensions(dims, dimension);
        int width = Math.max(1, outputDims[0]);
        int height = Math.max(1, outputDims[1]);
        if (!isSliceViewValuable(width, height)) {
            String skipMessage = String.format(
                    "Skipping %s view for %s: dimensions %dx%d not suitable for computer vision",
                    viewName, niftiFile.getFileName(), width, height
            );
            System.out.println("  " + skipMessage);
            JSONObject skipExportInfo = createExportInfo(niftiFile, viewOutputDir.toString(),
                    baseFilename, header, dimension, 0, viewName);
            skipExportInfo.put("skipReason", skipMessage);
            skipExportInfo.put("lowQualitySlicesSkipped", 0);

            return new ExportResult(0, skipExportInfo);
        }
        Files.createDirectories(viewOutputDir);
        int exportedSlices = 0;
        int lowQualitySlices = 0;

        for (int sliceIndex = 0; sliceIndex < numSlices; sliceIndex++) {
            try {
                BufferedImage sliceImage = convertSliceToBufferedImage(image, dimension, sliceIndex);
                if (isSliceContentValuable(sliceImage)) {
                    String pngFilename = String.format("%s_slice_%04d.png", baseFilename, sliceIndex);
                    Path outputPath = viewOutputDir.resolve(pngFilename);
                    ImageIO.write(sliceImage, "PNG", outputPath.toFile());
                    exportedSlices++;
                } else {
                    lowQualitySlices++;
                }

                if ((sliceIndex + 1) % 50 == 0 || sliceIndex == numSlices - 1) {
                    System.out.println(String.format(
                            "  Processed %s slice %d/%d (exported: %d, low-quality: %d)",
                            viewName, sliceIndex + 1, numSlices, exportedSlices, lowQualitySlices
                    ));
                }
            } catch (Exception e) {
                System.err.println("    Warning: Failed to export " + viewName + " slice " + sliceIndex + ": " + e.getMessage());
            }
        }
        JSONObject exportInfo = createExportInfo(niftiFile, viewOutputDir.toString(),
                baseFilename, header, dimension, exportedSlices, viewName);
        exportInfo.put("lowQualitySlicesSkipped", lowQualitySlices);

        return new ExportResult(exportedSlices, exportInfo);
    }

    private static boolean isSliceViewValuable(int width, int height) {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            return false;
        }
        double aspectRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (aspectRatio > MAX_ASPECT_RATIO) {
            return false;
        }

        return true;
    }

    private static boolean isSliceContentValuable(BufferedImage sliceImage) {
        byte[] pixelData = ((DataBufferByte) sliceImage.getRaster().getDataBuffer()).getData();
        double sum = 0, sumSq = 0;
        int backgroundPixels = 0;
        int totalPixels = pixelData.length;

        for (byte pixel : pixelData) {
            int value = pixel & 0xFF;
            sum += value;
            sumSq += value * value;
            if (value < 10) {
                backgroundPixels++;
            }
        }

        double mean = sum / totalPixels;
        double variance = (sumSq / totalPixels) - (mean * mean);
        double stdDev = Math.sqrt(Math.max(0, variance));
        double backgroundRatio = (double) backgroundPixels / totalPixels;
        return stdDev >= MIN_STANDARD_DEVIATION && backgroundRatio <= MAX_BACKGROUND_RATIO;
    }

    private static JSONObject createExportInfo(Path niftiFile, String savedAs, String baseFilename,
                                               NiftiHeader header, int exportDimension,
                                               int numSlicesExported, String viewName) {
        short[] dims = header.getDimensions();
        int ndim = dims[0];
        JSONObject exportInfo = new JSONObject();
        exportInfo.put("originalImagePath", niftiFile.toString());
        exportInfo.put("originalFilename", niftiFile.getFileName().toString());
        exportInfo.put("sinkDir", savedAs.substring(0, savedAs.lastIndexOf("/" + viewName + "_slices")));
        exportInfo.put("savedAs", savedAs);
        exportInfo.put("annotation", "NIfTI_" + baseFilename + "_" + viewName);
        exportInfo.put("originalWidth", dims[1]);
        exportInfo.put("originalHeight", ndim >= 2 ? dims[2] : 1);
        exportInfo.put("originalDepth", ndim >= 3 ? dims[3] : 1);
        exportInfo.put("numDimensions", ndim);
        exportInfo.put("exportDimension", exportDimension);
        exportInfo.put("numSlicesExported", numSlicesExported);
        exportInfo.put("dataType", header.getDataType().getName());
        exportInfo.put("fileSizeBytes", niftiFile.toFile().length());
        exportInfo.put("project", "NIfTI Batch Processing");
        exportInfo.put("user", System.getProperty("user.name", "batch-user"));
        exportInfo.put("viewName", viewName);
        if (ndim >= 3) {
            JSONArray cropOperations = new JSONArray();
            JSONObject level0 = new JSONObject();
            level0.put("level", 0);
            level0.put("view_type", viewName);
            level0.put("dimensions", getViewDimensions(dims, exportDimension));
            level0.put("slice_count", numSlicesExported);
            cropOperations.put(level0);
            exportInfo.put("crop_operations", cropOperations);
        }
        return exportInfo;
    }
    private static String getViewDimensions(short[] dims, int dimension) {
        switch (dimension) {
            case 0:
                return dims[2] + "x" + dims[3];
            case 1:
                return dims[1] + "x" + dims[3];
            case 2:
                return dims[1] + "x" + dims[2];
            default: return "1x1";
        }
    }

    private static ExportResult exportAllSlicesWithMetadata(Path niftiFile, Path sinkDirectory,
                                                            int requestedDimension, NiftiHeader header,
                                                            NiftiImage image) throws IOException {
        short[] dims = header.getDimensions();
        int ndim = dims[0];
        int actualDimension = requestedDimension;
        int numSlices = dims[actualDimension + 1];
        String baseFilename = getBaseFilename(niftiFile.getFileName().toString());
        Path fileOutputDir = sinkDirectory.resolve(baseFilename);
        Files.createDirectories(fileOutputDir);
        JSONObject exportInfo = new JSONObject();
        exportInfo.put("originalImagePath", niftiFile.toString());
        exportInfo.put("originalFilename", niftiFile.getFileName().toString());
        exportInfo.put("sinkDir", sinkDirectory.toString());
        exportInfo.put("savedAs", fileOutputDir.toString());
        exportInfo.put("annotation", "NIfTI_" + baseFilename);
        exportInfo.put("originalWidth", dims[1]);
        exportInfo.put("originalHeight", ndim >= 2 ? dims[2] : 1);
        exportInfo.put("originalDepth", ndim >= 3 ? dims[3] : 1);
        exportInfo.put("numDimensions", ndim);
        exportInfo.put("exportDimension", actualDimension);
        exportInfo.put("numSlicesExported", numSlices);
        exportInfo.put("dataType", header.getDataType().getName());
        try {
            exportInfo.put("fileSizeBytes", Files.size(niftiFile));
        } catch (IOException e) {
            exportInfo.put("fileSizeBytes", -1);
            System.err.println("Warning: Could not get file size for " + niftiFile.getFileName());
        }
        exportInfo.put("project", "NIfTI Batch Processing");
        exportInfo.put("user", System.getProperty("user.name", "batch-user"));
        int exportedSlices = 0;
        for (int sliceIndex = 0; sliceIndex < numSlices; sliceIndex++) {
            try {
                BufferedImage sliceImage = convertSliceToBufferedImage(image, actualDimension, sliceIndex);
                String pngFilename = String.format("%s_slice_%04d_dim%d.png", baseFilename, sliceIndex, actualDimension);
                Path outputPath = fileOutputDir.resolve(pngFilename);
                ImageIO.write(sliceImage, "PNG", outputPath.toFile());
                exportedSlices++;
                if (sliceIndex % 50 == 0) {
                    System.out.println("  Exported slice " + sliceIndex + "/" + numSlices + " (dim=" + actualDimension + ")");
                }
            } catch (Exception e) {
                System.err.println("    Warning: Failed to export slice " + sliceIndex + ": " + e.getMessage());
            }
        }
        return new ExportResult(exportedSlices, exportInfo);
    }

    private static BufferedImage convertSliceToBufferedImage(NiftiImage image, int dimension, int sliceIndex)
            throws IOException {
        Object data = image.getData();
        NiftiHeader header = image.getHeader();
        short[] dims = header.getDimensions();
        DataType dataType = header.getDataType();
        int[] outputDims = getOutputDimensions(dims, dimension);
        int width = Math.max(1, outputDims[0]);
        int height = Math.max(1, outputDims[1]);
        float[] sliceData = extractSliceAsFloatArray(data, header, dimension, sliceIndex);
        float[] normalizedData = normalizeFloatArray(sliceData);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] pixelData = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < normalizedData.length && i < pixelData.length; i++) {
            if (Float.isFinite(normalizedData[i])) {
                pixelData[i] = (byte) Math.max(0, Math.min(255, (int) (normalizedData[i] * 255)));
            } else {
                pixelData[i] = (byte) 128;
            }
        }
        return img;
    }
    private static float[] extractSliceAsFloatArray(Object data, NiftiHeader header,
                                                    int dimension, int sliceIndex) {
        short[] dims = header.getDimensions();
        DataType dataType = header.getDataType();
        int ndim = dims[0];
        if (ndim == 3) {
            int width = dims[1];
            int height = dims[2];
            int depth = dims[3];
            float[] result;
            if (dimension == 2) {
                result = new float[width * height];
                int srcIndex = sliceIndex * width * height;
                copySliceData(data, dataType, srcIndex, result);
            } else if (dimension == 1) {
                result = new float[width * depth];
                for (int z = 0; z < depth; z++) {
                    for (int x = 0; x < width; x++) {
                        int srcIndex = z * width * height + sliceIndex * width + x;
                        int dstIndex = z * width + x;
                        float value = extractValueAt(data, dataType, srcIndex);
                        result[dstIndex] = value;
                    }
                }
            } else if (dimension == 0) {
                result = new float[height * depth];
                for (int z = 0; z < depth; z++) {
                    for (int y = 0; y < height; y++) {
                        int srcIndex = z * width * height + y * width + sliceIndex;
                        int dstIndex = z * height + y;
                        float value = extractValueAt(data, dataType, srcIndex);
                        result[dstIndex] = value;
                    }
                }
            } else {
                throw new IllegalArgumentException("Invalid dimension: " + dimension);
            }
            return result;
        } else {
            int sliceSize = 1;
            for (int i = 1; i <= ndim; i++) {
                if (i - 1 != dimension) {
                    sliceSize *= dims[i];
                }
            }
            float[] result = new float[sliceSize];
            int dataIndex = sliceIndex * sliceSize;
            copySliceData(data, dataType, dataIndex, result);
            return result;
        }
    }
    private static void copySliceData(Object data, DataType dataType, int srcIndex, float[] result) {
        switch (dataType) {
            case DT_UNSIGNED_CHAR:
                byte[] byteData = (byte[]) data;
                for (int i = 0; i < result.length && srcIndex + i < byteData.length; i++) {
                    result[i] = byteData[srcIndex + i] & 0xFF;
                }
                break;
            case DT_SIGNED_SHORT:
                short[] shortData = (short[]) data;
                for (int i = 0; i < result.length && srcIndex + i < shortData.length; i++) {
                    result[i] = shortData[srcIndex + i];
                }
                break;
            case DT_UNSIGNED_SHORT:
                short[] ushortData = (short[]) data;
                for (int i = 0; i < result.length && srcIndex + i < ushortData.length; i++) {
                    result[i] = ushortData[srcIndex + i] & 0xFFFF;
                }
                break;
            case DT_FLOAT:
                float[] floatData = (float[]) data;
                System.arraycopy(floatData, srcIndex, result, 0,
                        Math.min(result.length, floatData.length - srcIndex));
                break;
            case DT_DOUBLE:
                double[] doubleData = (double[]) data;
                for (int i = 0; i < result.length && srcIndex + i < doubleData.length; i++) {
                    result[i] = (float) doubleData[srcIndex + i];
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported data type: " + dataType);
        }
    }
    private static float extractValueAt(Object data, DataType dataType, int index) {
        switch (dataType) {
            case DT_UNSIGNED_CHAR:
                return ((byte[]) data)[index] & 0xFF;
            case DT_SIGNED_SHORT:
                return ((short[]) data)[index];
            case DT_UNSIGNED_SHORT:
                return ((short[]) data)[index] & 0xFFFF;
            case DT_FLOAT:
                return ((float[]) data)[index];
            case DT_DOUBLE:
                return (float) ((double[]) data)[index];
            default:
                throw new UnsupportedOperationException("Unsupported data type: " + dataType);
        }
    }
    private static int[] getOutputDimensions(short[] dims, int slicedDimension) {
        int ndim = dims[0];
        if (ndim == 2) {
            if (slicedDimension == 0) return new int[]{1, dims[2]};
            else return new int[]{dims[1], 1};
        } else if (ndim == 3) {
            int width = dims[1], height = dims[2], depth = dims[3];
            switch (slicedDimension) {
                case 0: return new int[]{height, depth};
                case 1: return new int[]{width, depth};
                case 2: return new int[]{width, height};
                default: return new int[]{width, height};
            }
        } else {
            int outputSize = 1;
            for (int i = 1; i <= ndim; i++) {
                if (i - 1 != slicedDimension) {
                    outputSize *= dims[i];
                }
            }
            return new int[]{Math.max(1, outputSize), 1};
        }
    }
    private static float[] normalizeFloatArray(float[] data) {
        if (data.length == 0) return data;
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        for (float value : data) {
            if (Float.isFinite(value)) {
                if (value < min) min = value;
                if (value > max) max = value;
            }
        }
        if (min == Float.MAX_VALUE) {
            java.util.Arrays.fill(data, 0.5f);
            return data;
        }
        float range = max - min;
        if (range == 0) {
            java.util.Arrays.fill(data, 0.5f);
            return data;
        }
        for (int i = 0; i < data.length; i++) {
            if (Float.isFinite(data[i])) {
                data[i] = (data[i] - min) / range;
            } else {
                data[i] = 0.5f;
            }
        }
        return data;
    }
    private static String getBaseFilename(String filename) {
        if (filename.toLowerCase().endsWith(".nii.gz")) {
            return filename.substring(0, filename.length() - 7);
        } else if (filename.toLowerCase().endsWith(".nii")) {
            return filename.substring(0, filename.length() - 4);
        } else {
            return filename;
        }
    }

    private static List<Path> findNiftiFiles(Path rootDirectory) throws IOException {
        List<Path> niftiFiles = new ArrayList<>();
        Files.walk(rootDirectory)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String filename = path.getFileName().toString().toLowerCase();
                    return filename.endsWith(".nii") || filename.endsWith(".nii.gz");
                })
                .forEach(niftiFiles::add);
        return niftiFiles;
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java BatchNiftiProcessor <source_directory> <sink_directory> <mode>");
            System.out.println("  source_directory: Root directory containing NIfTI files");
            System.out.println("  sink_directory: Output directory for PNG files");
            System.out.println("  mode: Processing mode");
            System.out.println("        0 = X/sagittal slices");
            System.out.println("        1 = Y/coronal slices");
            System.out.println("        2 = Z/axial slices");
            System.out.println("        4 = All anatomical views (multi-view mode)");
            System.out.println("");
            System.out.println("Example (single view): java BatchNiftiProcessor /data/nifti /output/png 2");
            System.out.println("Example (multi-view): java BatchNiftiProcessor /data/nifti /output/png 4");
            return;
        }
        try {
            String sourceDir = args[0];
            String sinkDir = args[1];
            int mode = Integer.parseInt(args[2]);
            if (mode != 0 && mode != 1 && mode != 2 && mode != 4) {
                System.err.println("Error: Mode must be 0, 1, 2, or 4");
                System.exit(1);
            }
            processDirectoryTree(sourceDir, sinkDir, mode);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    private static void createMetadataFile(String sourceDir, String sinkDir, int totalFilesProcessed,
                                           int totalSlicesExported, long processingTimeMs,
                                           List<JSONObject> exportInfos, String sinkPath) throws IOException {
        JSONObject metadata = new JSONObject();
        metadata.put("sourceDir", sourceDir);
        metadata.put("sinkDir", sinkDir);
        metadata.put("totalFilesProcessed", totalFilesProcessed);
        metadata.put("totalSlicesExported", totalSlicesExported);
        metadata.put("processingTimeMs", processingTimeMs);
        metadata.put("project", "NIfTI Batch Processing");
        metadata.put("sessionId", UUID.randomUUID().toString());
        metadata.put("exportSequence", 0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC);
        String timestamp = formatter.format(Instant.now());
        metadata.put("exportedAtUtc", timestamp);
        metadata.put("sessionFolderUtc", timestamp);
        metadata.put("user", System.getProperty("user.name", "batch-user"));
        JSONArray exportsArray = new JSONArray();
        for (JSONObject exportInfo : exportInfos) {
            exportsArray.put(exportInfo);
        }
        metadata.put("exports", exportsArray);
        double filesPerSec = processingTimeMs > 0 ? (double) totalFilesProcessed * 1000 / processingTimeMs : 0.0;
        double slicesPerSec = processingTimeMs > 0 ? (double) totalSlicesExported * 1000 / processingTimeMs : 0.0;
        double avgSlicesPerFile = totalFilesProcessed > 0 ? (double) totalSlicesExported / totalFilesProcessed : 0.0;
        metadata.put("filesProcessedPerSecond", filesPerSec);
        metadata.put("slicesExportedPerSecond", slicesPerSec);
        metadata.put("averageSlicesPerFile", avgSlicesPerFile);
        JSONObject defaultExportSize = new JSONObject();
        defaultExportSize.put("w", "varies by slice");
        defaultExportSize.put("h", "varies by slice");
        metadata.put("defaultExportSize", defaultExportSize);
        String metadataFilename = "nifti_batch_metadata_" + timestamp + ".json";
        String metadataPath = Paths.get(sinkPath, metadataFilename).toString();
        try (FileWriter writer = new FileWriter(metadataPath)) {
            writer.write(metadata.toString(2));
        }
        System.out.println("Metadata saved to: " + metadataPath);
    }
}