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
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class BatchProcessor {
    private final VideoFrameExtractor parentFrame;
    private JDialog progressDialog;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private FrameExtractionWorker worker;

    public BatchProcessor(VideoFrameExtractor parentFrame) {
        this.parentFrame = parentFrame;
    }

    public void showMetadataFileChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select Segments Metadata File");
        fc.setFileFilter(new FileNameExtensionFilter(
                "JSON files", "json"));
        int res = fc.showOpenDialog(parentFrame);
        if (res == JFileChooser.APPROVE_OPTION) {
            File metadataFile = fc.getSelectedFile();
            processMetadataFile(metadataFile);
        }
    }

    private void processMetadataFile(File metadataFile) {
        try {
            String content = new String(Files.readAllBytes(metadataFile.toPath()));
            JSONObject json = new JSONObject(content);
            BatchMetadata metadata = parseMetadata(json);
            File videoFile = new File(metadata.getVideoPath());
            if (!videoFile.exists()) {
                JOptionPane.showMessageDialog(parentFrame,
                        "Video file not found: " + metadata.getVideoPath(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            File outputDir = new File(metadata.getSinkDirectory());
            if (!outputDir.exists()) {
                int option = JOptionPane.showConfirmDialog(parentFrame,
                        "Output directory does not exist. Create it?\n" + metadata.getSinkDirectory(),
                        "Directory Not Found", JOptionPane.YES_NO_OPTION);
                if (option == JOptionPane.YES_OPTION) {
                    if (!outputDir.mkdirs()) {
                        JOptionPane.showMessageDialog(parentFrame,
                                "Failed to create output directory: " + metadata.getSinkDirectory(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    return;
                }
            }
            int confirm = JOptionPane.showConfirmDialog(parentFrame,
                    "Reproduce extraction from metadata file?\n\n" +
                            "Video: " + metadata.getVideoPath() + "\n" +
                            "Output: " + metadata.getSinkDirectory() + "\n" +
                            "Segments: " + metadata.getSegments().size(),
                    "Confirm Batch Processing", JOptionPane.YES_NO_OPTION);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            showProgressDialog();
            worker = new FrameExtractionWorker(
                    videoFile,
                    outputDir,
                    metadata.getSegments(),
                    metadata.getProject(),
                    metadata.getUser(),
                    metadata.getExtractionOptions(),
                    metadata.getFilterString(),
                    progressBar,
                    null,
                    null
            );

            worker.addPropertyChangeListener(evt -> {
                if ("progress".equals(evt.getPropertyName())) {
                    progressBar.setValue((Integer) evt.getNewValue());
                } else if ("state".equals(evt.getPropertyName())) {
                    if (SwingWorker.StateValue.DONE == evt.getNewValue()) {
                        closeProgressDialog();
                        try {
                            worker.get();
                            JOptionPane.showMessageDialog(parentFrame,
                                    "Batch processing completed successfully.",
                                    "Success", JOptionPane.INFORMATION_MESSAGE);
                        } catch (InterruptedException | ExecutionException e) {
                            JOptionPane.showMessageDialog(parentFrame,
                                    "Batch processing failed: " + e.getCause().getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            });

            worker.execute();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Error reading metadata file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Error processing metadata: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BatchMetadata parseMetadata(JSONObject json) {
        BatchMetadata metadata = new BatchMetadata();
        metadata.setVideoPath(json.getString("videoPath"));
        metadata.setSinkDirectory(json.getString("sinkDirectory"));
        metadata.setProject(json.optString("project", "Generic annotation"));
        metadata.setUser(json.optString("user", "Generic user"));
        if (json.has("extractionOptions")) {
            JSONObject optionsJson = json.getJSONObject("extractionOptions");
            ExtractionOptions options = new ExtractionOptions();
            options.setDropDuplicates(optionsJson.optBoolean("dropDuplicates", false));
            options.setAdvancedDropDuplicates(optionsJson.optBoolean("advancedDropDuplicates", false));
            options.setUseCustomMpdecimateParams(optionsJson.optBoolean("useCustomMpdecimateParams", false));

            if (options.isUseCustomMpdecimateParams()) {
                options.setMpdecimateHi(optionsJson.optInt("mpdecimateHi", 512));
                options.setMpdecimateLo(optionsJson.optInt("mpdecimateLo", 192));
                options.setMpdecimateFrac(optionsJson.optDouble("mpdecimateFrac", 0.30));
            }

            options.setUseUniformSampling(optionsJson.optBoolean("useUniformSampling", false));
            if (options.isUseUniformSampling()) {
                options.setUniformSamplingFps(optionsJson.optDouble("uniformSamplingFps", 1.0));
            }

            options.setUseSceneChanges(optionsJson.optBoolean("useSceneChanges", false));
            if (options.isUseSceneChanges()) {
                options.setSceneThreshold(optionsJson.optDouble("sceneThreshold", 0.02));
            }

            options.setSkipDarkFrames(optionsJson.optBoolean("skipDarkFrames", false));
            if (options.isSkipDarkFrames()) {
                options.setMinLumaYavg(optionsJson.optDouble("minLumaYavg", 20.0));
            }

            metadata.setExtractionOptions(options);
        }
        metadata.setFilterString(json.optString("filterString", ""));
        if (json.has("segments")) {
            JSONObject segmentsJson = json.getJSONObject("segments");
            List<VideoFrameExtractor.Segment> segments = new ArrayList<>();
            for (String key : segmentsJson.keySet()) {
                JSONObject segmentJson = segmentsJson.getJSONObject(key);
                int start = segmentJson.optInt("start", -1);
                int end = segmentJson.optInt("end", -1);
                String prefix = segmentJson.optString("prefix", "Frame_");
                boolean isFull = segmentJson.optBoolean("isFull", false);
                segments.add(new VideoFrameExtractor.Segment(start, end, prefix));
            }
            metadata.setSegments(segments);
        }

        return metadata;
    }

    private void showProgressDialog() {
        progressDialog = new JDialog(parentFrame, "Batch Processing", false);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(parentFrame);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        statusLabel = new JLabel("Processing metadata file...");
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);

        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        progressDialog.add(panel);
        progressDialog.setVisible(true);
    }

    public void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dispose();
            progressDialog = null;
        }
    }
}