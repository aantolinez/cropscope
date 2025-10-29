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

package com.cropscope.dicommanager.cli;

import com.cropscope.dicommanager.dicomfilemanagement.DicomFileManager;
import org.apache.commons.cli.*;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DicomToPngCli {
    private static final String DICOM_HEADER = "DICM";
    private static DicomFileManager dicomManager;
    private static boolean verbose = false;

    private static long imagesFound = 0;
    private static long imagesProcessedSuccessfully = 0;
    private static long imagesSkipped = 0;

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        Options options = createOptions();
        CommandLine cmd = parseArguments(options, args);
        if (cmd == null) {
            return;
        }
        dicomManager = new DicomFileManager();
        dicomManager.testDicomPlugins();
        String sourceDirPath = cmd.getOptionValue("source");
        String outputDirPath = cmd.getOptionValue("output");
        boolean recursive = cmd.hasOption("recursive");
        boolean preserveStructure = cmd.hasOption("preserve");
        boolean skipValidation = cmd.hasOption("skip-validation");
        File sourceDir = validateSourceDirectory(sourceDirPath);
        File outputDir = validateOutputDirectory(outputDirPath);
        if (sourceDir == null || outputDir == null) {
            return;
        }

        if (verbose) {
            System.out.println("Source directory: " + sourceDir.getAbsolutePath());
            System.out.println("Output directory: " + outputDir.getAbsolutePath());
            System.out.println("Recursive: " + recursive);
            System.out.println("Preserve structure: " + preserveStructure);
            System.out.println("Skip validation: " + skipValidation);
        }
        try {
            processDirectory(sourceDir, outputDir, recursive, preserveStructure, skipValidation);
            System.out.println("\nExport completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            long endTime = System.currentTimeMillis();
            saveMetricsAsJson(sourceDir, outputDir, startTime, endTime, cmd);
        }
    }

    private static void saveMetricsAsJson(File sourceDir, File outputDir, long startTime, long endTime, CommandLine cmd) {
        try {
            double totalTimeSeconds = (endTime - startTime) / 1000.0;
            double totalTimeMinutes = totalTimeSeconds / 60.0;
            imagesSkipped = imagesFound - imagesProcessedSuccessfully;
            double imagesPerSecond = (totalTimeSeconds > 0) ? (imagesProcessedSuccessfully / totalTimeSeconds) : 0;
            double imagesPerMinute = (totalTimeMinutes > 0) ? (imagesProcessedSuccessfully / totalTimeMinutes) : 0;
            JSONObject metadata = new JSONObject();
            metadata.put("annotation_project", "Generic_annotation");
            metadata.put("user", "Alfonso Antolínez García");
            JSONObject provenance = new JSONObject();
            provenance.put("processing_tool_name", "DicomDataManager-CLI");
            provenance.put("processing_tool_version", "1.0-SNAPSHOT");
            provenance.put("source_directory", sourceDir.getAbsolutePath());
            provenance.put("output_directory", outputDir.getAbsolutePath());

            JSONObject parameters = new JSONObject();
            parameters.put("recursive", cmd.hasOption("recursive"));
            parameters.put("preserve_structure", cmd.hasOption("preserve"));
            parameters.put("skip_validation", cmd.hasOption("skip-validation"));
            parameters.put("verbose", cmd.hasOption("verbose"));
            provenance.put("processing_parameters", parameters);

            metadata.put("provenance", provenance);
            JSONObject tracking = new JSONObject();
            tracking.put("start_timestamp_iso8601", Instant.ofEpochMilli(startTime).toString());
            tracking.put("end_timestamp_iso8601", Instant.ofEpochMilli(endTime).toString());
            tracking.put("total_processing_time_seconds", totalTimeSeconds);
            tracking.put("total_images_found", imagesFound);
            tracking.put("total_images_processed_successfully", imagesProcessedSuccessfully);
            tracking.put("total_images_skipped", imagesSkipped);
            tracking.put("processing_rate_images_per_second", imagesPerSecond);
            tracking.put("processing_rate_images_per_minute", imagesPerMinute);

            metadata.put("data_tracking", tracking);
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            String metadataFileName = "dicom_processing_" + timestamp.replace(":", "-") + ".json";
            Path metadataFilePath = Paths.get(outputDir.getAbsolutePath(), metadataFileName);
            Files.write(metadataFilePath, metadata.toString(2).getBytes());
            System.out.println("Processing metadata saved to: " + metadataFilePath.toString());

        } catch (IOException e) {
            System.err.println("Error saving metadata file: " + e.getMessage());
        }
    }

    private static Options createOptions() {
        Options options = new Options();
        options.addOption(Option.builder("s")
                .longOpt("source")
                .hasArg()
                .argName("DIRECTORY")
                .desc("Source directory containing DICOM files")
                .required()
                .build());
        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("DIRECTORY")
                .desc("Output directory for PNG files")
                .required()
                .build());
        options.addOption(Option.builder("r")
                .longOpt("recursive")
                .desc("Process directories recursively")
                .build());
        options.addOption(Option.builder("p")
                .longOpt("preserve")
                .desc("Preserve original directory structure")
                .build());
        options.addOption(Option.builder("v")
                .longOpt("verbose")
                .desc("Enable verbose output")
                .build());
        options.addOption(Option.builder("n")
                .longOpt("no-validation")
                .desc("Skip DICOM validation (process all .dcm files)")
                .build());
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show help message")
                .build());
        return options;
    }

    private static CommandLine parseArguments(Options options, String[] args) {
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("help")) {
                printHelp(formatter, options);
                return null;
            }
            verbose = cmd.hasOption("verbose");
            return cmd;
        } catch (ParseException e) {
            System.err.println("Error parsing arguments: " + e.getMessage());
            printHelp(formatter, options);
            return null;
        }
    }

    private static void printHelp(HelpFormatter formatter, Options options) {
        formatter.printHelp("DicomToPngCli", options);
        System.out.println("\nExamples:");
        System.out.println("  # Basic recursive export with structure preservation");
        System.out.println("  java -jar DicomDataManager-CLI.jar -s /path/to/dicom -o /path/to/output");
        System.out.println("\n  # Export without preserving directory structure");
        System.out.println("  java -jar DicomDataManager-CLI.jar -s /path/to/dicom -o /path/to/output -p");
        System.out.println("\n  # Skip DICOM validation (process all .dcm files)");
        System.out.println("  java -jar DicomDataManager-CLI.jar -s /path/to/dicom -o /path/to/output -n");
    }

    private static File validateSourceDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            System.err.println("Error: Source directory does not exist: " + path);
            return null;
        }
        if (!dir.isDirectory()) {
            System.err.println("Error: Source path is not a directory: " + path);
            return null;
        }
        return dir;
    }

    private static File validateOutputDirectory(String path) {
        File dir = new File(path);
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                System.err.println("Error: Output path exists but is not a directory: " + path);
                return null;
            }
        } else {
            try {
                if (!dir.mkdirs()) {
                    System.err.println("Error: Could not create output directory: " + path);
                    return null;
                }
                System.out.println("Created output directory: " + path);
            } catch (SecurityException e) {
                System.err.println("Error: No permission to create output directory: " + e.getMessage());
                return null;
            }
        }
        return dir;
    }

    private static void processDirectory(File sourceDir, File outputDir,
                                         boolean recursive, boolean preserveStructure,
                                         boolean skipValidation) throws IOException {
        if (verbose) {
            System.out.println("\nProcessing directory: " + sourceDir.getAbsolutePath());
        }
        if (recursive) {
            processRecursively(sourceDir, outputDir, preserveStructure, skipValidation);
        } else {
            processCurrentDirectory(sourceDir, outputDir, skipValidation);
        }
    }

    private static void processRecursively(File currentDir, File outputBaseDir,
                                           boolean preserveStructure, boolean skipValidation) throws IOException {
        if (verbose) {
            System.out.println("  Processing directory: " + currentDir.getName());
        }
        File currentOutputDir = outputBaseDir;
        if (preserveStructure) {
            Path relativePath = getRelativePath(currentDir, currentDir.getParentFile());
            currentOutputDir = new File(outputBaseDir, relativePath.toString());
            if (!currentOutputDir.exists() && !currentOutputDir.mkdirs()) {
                System.err.println("Warning: Could not create output directory: " + currentOutputDir);
                return;
            }
        }
        File[] files = currentDir.listFiles();
        if (files == null) {
            System.err.println("Warning: Could not read directory: " + currentDir);
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                processRecursively(file, outputBaseDir, preserveStructure, skipValidation);
            } else if (shouldProcessFile(file, skipValidation)) {
                exportToPng(file, currentOutputDir);
            }
        }
    }

    private static void processCurrentDirectory(File sourceDir, File outputDir, boolean skipValidation) {
        File[] files = sourceDir.listFiles();
        if (files == null) {
            System.err.println("Warning: Could not read directory: " + sourceDir);
            return;
        }

        for (File file : files) {
            if (!file.isDirectory() && shouldProcessFile(file, skipValidation)) {
                exportToPng(file, outputDir);
            }
        }
    }

    private static boolean shouldProcessFile(File file, boolean skipValidation) {
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".dcm")) {
            return false;
        }
        imagesFound++;

        if (skipValidation) {
            return true;
        }
        if (file.length() < 132) {
            if (verbose) {
                System.out.println("  Skipping: " + file.getName() + " (file too small)");
            }
            return false;
        }

        try {
            byte[] header = new byte[4];
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(128);
                raf.readFully(header);
            }

            if (!new String(header).equals(DICOM_HEADER)) {
                if (verbose) {
                    System.out.println("  Skipping: " + file.getName() + " (not a valid DICOM file)");
                }
                return false;
            }
            if (!dicomManager.hasPixelData(file)) {
                if (verbose) {
                    System.out.println("  Skipping: " + file.getName() + " (no pixel data)");
                }
                return false;
            }

            return true;
        } catch (IOException e) {
            if (verbose) {
                System.out.println("  Skipping: " + file.getName() + " (IO error: " + e.getMessage() + ")");
            }
            return false;
        }
    }

    private static void exportToPng(File dicomFile, File outputDir) {
        String fileName = dicomFile.getName();
        String baseName = fileName.substring(0, fileName.length() - 4);
        File outputFile = new File(outputDir, baseName + ".png");

        try {
            if (verbose) {
                System.out.println("  Exporting: " + fileName + " -> " + outputFile.getName());
            }
            BufferedImage image = dicomManager.loadImage(dicomFile);
            if (image == null) {
                System.err.println("  Error: Could not load image from " + fileName);
                return;
            }
            dicomManager.exportImage(image, "png", outputFile);
            imagesProcessedSuccessfully++;

            if (verbose) {
                System.out.println("    Success: " + image.getWidth() + "x" + image.getHeight());
            }
        } catch (Exception e) {
            System.err.println("  Error exporting " + fileName + ": " + e.getMessage());
        }
    }

    private static Path getRelativePath(File file, File baseDir) {
        try {
            Path basePath = baseDir.toPath();
            Path filePath = file.toPath();
            return basePath.relativize(filePath);
        } catch (Exception e) {
            return Paths.get(file.getName());
        }
    }
}