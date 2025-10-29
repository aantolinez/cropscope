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

package com.cropscope.dicommanager.dicomfilemanagement;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class DicomFileManager {
    private List<File> dicomFiles = new ArrayList<>();
    private List<File> imageFiles = new ArrayList<>();
    private Map<Integer, BufferedImage> imageCache = new LinkedHashMap<>();
    private Map<Integer, String> dicomInfoCache = new LinkedHashMap<>();
    private Map<Integer, Attributes> dicomMetadataCache = new LinkedHashMap<>();
    private boolean debugMode = true;

    private DefaultTreeModel treeModel;
    private File rootFolder;
    private Map<File, Boolean> fileHasImageMap = new HashMap<>();

    static {
        try {
            ImageIO.scanForPlugins();
        } catch (Exception e) {
            System.err.println("Warning: Failed to initialize DICOM plugins: " + e.getMessage());
        }
    }

    public void testDicomPlugins() {
        if (debugMode) {
            System.out.println("=== DICOM Plugin Test ===");
            System.out.println("Available ImageIO formats:");
            String[] formatNames = ImageIO.getReaderFormatNames();
            for (String formatName : formatNames) {
                System.out.println("- " + formatName);
            }

            System.out.println("\nDICOM readers:");
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (readers.hasNext()) {
                System.out.println("DICOM reader found: " + readers.next().getClass().getName());
            } else {
                System.out.println("No DICOM reader found!");
            }
            System.out.println("========================");
        }
    }

    public void clearFiles() {
        dicomFiles.clear();
        imageFiles.clear();
        imageCache.clear();
        dicomInfoCache.clear();
        dicomMetadataCache.clear();
        fileHasImageMap.clear();
        rootFolder = null;
    }

    public void addFiles(List<File> files) {
        dicomFiles.addAll(files);
        imageFiles.addAll(files);
    }

    public List<File> getImageFiles() {
        return imageFiles;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public DefaultTreeModel getTreeModel() {
        return treeModel;
    }

    public File getRootFolder() {
        return rootFolder;
    }

    public Map<File, Boolean> getFileHasImageMap() {
        return fileHasImageMap;
    }

    public void initializeTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("DICOM Files");
        treeModel = new DefaultTreeModel(root);
    }

    public void buildFileTree(File folder, List<File> imageFiles) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new FileWrapper(folder, folder.getName()));
        rootFolder = folder;
        buildTreeNode(rootNode, folder, imageFiles);
        treeModel.setRoot(rootNode);
        treeModel.reload();
    }

    private void buildTreeNode(DefaultMutableTreeNode parentNode, File folder, List<File> imageFiles) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(new FileWrapper(file, file.getName()));
                    parentNode.add(folderNode);
                    buildTreeNode(folderNode, file, imageFiles);
                }
            }
            for (File file : files) {
                if (!file.isDirectory() && fileHasImageMap.getOrDefault(file, false)) {
                    DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new FileWrapper(file, file.getName()));
                    parentNode.add(fileNode);
                }
            }
        }
    }

    public void buildFlatFileTree(List<File> imageFiles) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Selected Files");
        treeModel.setRoot(rootNode);
        Map<File, DefaultMutableTreeNode> directoryNodes = new HashMap<>();

        for (File file : imageFiles) {
            File parent = file.getParentFile();
            DefaultMutableTreeNode parentNode;

            if (directoryNodes.containsKey(parent)) {
                parentNode = directoryNodes.get(parent);
            } else {
                parentNode = new DefaultMutableTreeNode(new FileWrapper(parent, parent.getName()));
                rootNode.add(parentNode);
                directoryNodes.put(parent, parentNode);
            }

            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new FileWrapper(file, file.getName()));
            parentNode.add(fileNode);
        }

        treeModel.reload();
    }

    public List<File> findImageFilesInDirectory(File directory) {
        List<File> imageFiles = new ArrayList<>();

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        if (isDicomFile(file) && hasPixelData(file)) {
                            imageFiles.add(file);
                        }
                    } else if (file.isDirectory()) {
                        imageFiles.addAll(findImageFilesInDirectory(file));
                    }
                }
            }
        }

        return imageFiles;
    }

    public List<File> findAllFilesRecursively(File folder) {
        List<File> allFiles = new ArrayList<>();

        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        allFiles.addAll(findAllFilesRecursively(file));
                    } else {
                        allFiles.add(file);
                    }
                }
            }
        }

        return allFiles;
    }

    public boolean isDicomFile(File file) {
        try {
            if (file.length() < 132) {
                if (debugMode) {
                    System.out.println("File too small");
                }
                return false;
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.skip(128);
                byte[] dicm = new byte[4];
                int read = fis.read(dicm);
                if (read != 4) {
                    return false;
                }
                return new String(dicm).equals("DICM");
            }
        } catch (Exception e) {
            if (debugMode) {
                System.out.println("Error checking DICOM file: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean hasPixelData(File file) {
        try {
            Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
            if (!iter.hasNext()) {
                return false;
            }
            ImageReader reader = iter.next();
            try (FileImageInputStream iis = new FileImageInputStream(file)) {
                reader.setInput(iis);
                DicomMetaData metadata = (DicomMetaData) reader.getStreamMetadata();
                if (metadata != null) {
                    Attributes attrs = metadata.getAttributes();
                    return attrs.containsValue(Tag.PixelData);
                }
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            if (debugMode) {
                System.out.println("Error checking pixel data: " + e.getMessage());
            }
            return false;
        }
        return false;
    }

    public BufferedImage loadImage(File file) {
        try {
            Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
            if (!iter.hasNext()) {
                throw new Exception("No DICOM reader available");
            }

            ImageReader reader = iter.next();
            try (FileImageInputStream iis = new FileImageInputStream(file)) {
                reader.setInput(iis);
                DicomMetaData metadata = (DicomMetaData) reader.getStreamMetadata();
                if (metadata != null) {
                    Attributes attrs = metadata.getAttributes();
                    StringBuilder sb = new StringBuilder();
                    sb.append("File: ").append(file.getName()).append("\n\n");
                    sb.append("Patient ID: ").append(attrs.getString(Tag.PatientID, "N/A")).append("\n");
                    sb.append("Patient Name: ").append(attrs.getString(Tag.PatientName, "N/A")).append("\n");
                    sb.append("Study Date: ").append(attrs.getString(Tag.StudyDate, "N/A")).append("\n");
                    sb.append("Modality: ").append(attrs.getString(Tag.Modality, "")).append("\n");
                    sb.append("Study Description: ").append(attrs.getString(Tag.StudyDescription, "N/A")).append("\n");
                    sb.append("Series Description: ").append(attrs.getString(Tag.SeriesDescription, "N/A")).append("\n");
                    sb.append("Image Comments: ").append(attrs.getString(Tag.ImageComments, "N/A")).append("\n");

                    String dicomInfo = sb.toString();
                    dicomInfoCache.put(imageFiles.indexOf(file), dicomInfo);
                    dicomMetadataCache.put(imageFiles.indexOf(file), attrs);
                }
                int numImages = reader.getNumImages(true);
                if (numImages > 0) {
                    BufferedImage image = reader.read(0);
                    imageCache.put(imageFiles.indexOf(file), image);
                    return image;
                } else {
                    throw new Exception("No images found in DICOM file");
                }
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            if (debugMode) {
                System.out.println("Error loading image: " + e.getMessage());
            }
            return null;
        }
    }

    public BufferedImage getCurrentImage(int index) {
        if (index < 0 || index >= imageFiles.size()) {
            return null;
        }
        if (imageCache.containsKey(index)) {
            return imageCache.get(index);
        }
        return loadImage(imageFiles.get(index));
    }

    public String getDicomInfo(int index) {
        if (index < 0 || index >= imageFiles.size()) {
            return null;
        }
        if (dicomInfoCache.containsKey(index)) {
            return dicomInfoCache.get(index);
        }
        loadImage(imageFiles.get(index));
        return dicomInfoCache.get(index);
    }

    public Attributes getDicomMetadata(int index) {
        if (index < 0 || index >= imageFiles.size()) {
            return null;
        }
        if (dicomMetadataCache.containsKey(index)) {
            return dicomMetadataCache.get(index);
        }
        loadImage(imageFiles.get(index));
        return dicomMetadataCache.get(index);
    }

    public void exportImage(BufferedImage image, String format, File outputFile) throws IOException {
        if (image == null || format == null || outputFile == null) {
            throw new IllegalArgumentException("Invalid parameters for image export");
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new IOException("No writer available for format: " + format);
        }

        ImageWriter writer = writers.next();
        try (FileImageOutputStream ios = new FileImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            if (format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg")) {
                ImageWriteParam writeParam = writer.getDefaultWriteParam();
                if (writeParam.canWriteCompressed()) {
                    writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    writeParam.setCompressionQuality(0.9f);
                }
            }
            writer.write(image);
        } finally {
            writer.dispose();
        }
    }

    public List<File> findImagesInSameSeries(int currentIndex) {
        List<File> seriesImages = new ArrayList<>();

        if (currentIndex < 0 || currentIndex >= imageFiles.size()) {
            return seriesImages;
        }

        Attributes currentMetadata = getDicomMetadata(currentIndex);
        if (currentMetadata == null) {
            return seriesImages;
        }
        String currentSeriesUID = currentMetadata.getString(Tag.SeriesInstanceUID, "");

        if (currentSeriesUID.isEmpty()) {
            File currentFile = imageFiles.get(currentIndex);
            File parentFolder = currentFile.getParentFile();

            for (File file : imageFiles) {
                if (file.getParentFile().equals(parentFolder)) {
                    seriesImages.add(file);
                }
            }
        } else {
            for (int i = 0; i < imageFiles.size(); i++) {
                try {
                    Attributes attrs = getDicomMetadata(i);
                    if (attrs != null) {
                        String seriesUID = attrs.getString(Tag.SeriesInstanceUID, "");
                        if (seriesUID.equals(currentSeriesUID)) {
                            seriesImages.add(imageFiles.get(i));
                        }
                    }
                } catch (Exception e) {
                    if (debugMode) {
                        System.out.println("Error reading DICOM metadata: " + e.getMessage());
                    }
                }
            }
        }

        return seriesImages;
    }

    public double getSlicePosition(File file) throws Exception {
        Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
        if (iter.hasNext()) {
            ImageReader reader = iter.next();
            try (FileImageInputStream iis = new FileImageInputStream(file)) {
                reader.setInput(iis);
                DicomMetaData metadata = (DicomMetaData) reader.getStreamMetadata();
                if (metadata != null) {
                    Attributes attrs = metadata.getAttributes();
                    Double sliceLocation = attrs.getDouble(Tag.SliceLocation, Double.NaN);
                    if (!Double.isNaN(sliceLocation)) {
                        return sliceLocation;
                    }
                    double[] imagePosition = attrs.getDoubles(Tag.ImagePositionPatient);
                    if (imagePosition != null && imagePosition.length >= 3) {
                        return imagePosition[2];
                    }
                }
            } finally {
                reader.dispose();
            }
        }
        return 0.0;
    }

    public static class FileWrapper {
        private final File file;
        private final String displayName;

        public FileWrapper(File file, String displayName) {
            this.file = file;
            this.displayName = displayName;
        }

        public File getFile() {
            return file;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}