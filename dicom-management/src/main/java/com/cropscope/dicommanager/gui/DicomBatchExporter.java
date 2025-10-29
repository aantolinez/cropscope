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

package com.cropscope.dicommanager.gui;

import com.cropscope.dicommanager.dicomfilemanagement.DicomFileManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DicomBatchExporter extends JDialog {
    private DicomFileManager dicomFileManager;
    private List<File> selectedFiles = new ArrayList<>();
    private JList<File> fileList;
    private DefaultListModel<File> fileListModel;
    private JComboBox<String> formatComboBox;
    private JTextField prefixTextField;
    private JTextField outputDirTextField;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton startExportButton;
    private JButton cancelButton;
    private JButton browseOutputButton;
    private JButton addFilesButton;
    private JButton clearListButton;

    public DicomBatchExporter(JFrame parent, DicomFileManager dicomFileManager, List<File> initialFiles) {
        super(parent, "DICOM Batch Exporter", true);
        this.dicomFileManager = dicomFileManager;
        if (initialFiles != null && !initialFiles.isEmpty()) {
            selectedFiles.addAll(initialFiles);
        }

        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        refreshFileList();

        setSize(600, 450);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    public static void showExporter(JFrame parent, DicomFileManager dicomFileManager, List<File> initialFiles) {
        DicomBatchExporter exporter = new DicomBatchExporter(parent, dicomFileManager, initialFiles);
        exporter.setVisible(true);
    }

    public void addFilesToExport(List<File> files) {
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                if (!selectedFiles.contains(file)) {
                    selectedFiles.add(file);
                }
            }
            refreshFileList();
            statusLabel.setText("Added " + files.size() + " files to export list");
        }
    }

    private void refreshFileList() {
        fileListModel.clear();
        for (File file : selectedFiles) {
            fileListModel.addElement(file);
        }
    }

    private void initializeComponents() {
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.setVisibleRowCount(8);
        JScrollPane fileListScrollPane = new JScrollPane(fileList);
        String[] formats = {"PNG", "JPEG", "JPG", "BMP"};
        formatComboBox = new JComboBox<>(formats);
        formatComboBox.setSelectedItem("PNG");
        prefixTextField = new JTextField("Enter prefix (optional)");
        prefixTextField.setForeground(Color.GRAY);
        prefixTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (prefixTextField.getText().equals("Enter prefix (optional)")) {
                    prefixTextField.setText("");
                    prefixTextField.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (prefixTextField.getText().isEmpty()) {
                    prefixTextField.setText("Enter prefix (optional)");
                    prefixTextField.setForeground(Color.GRAY);
                }
            }
        });
        outputDirTextField = new JTextField(20);
        outputDirTextField.setEditable(false);
        addFilesButton = new JButton("Add Files");
        clearListButton = new JButton("Clear List");
        startExportButton = new JButton("Start Export");
        cancelButton = new JButton("Cancel");
        browseOutputButton = new JButton("Browse");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        statusLabel = new JLabel("Ready");
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel fileSelectionPanel = new JPanel(new BorderLayout(5, 5));
        JPanel fileButtonPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        fileButtonPanel.add(addFilesButton);
        fileButtonPanel.add(clearListButton);
        fileButtonPanel.add(startExportButton);

        JLabel fileSelectionLabel = new JLabel("Files to Export:");
        fileSelectionPanel.add(fileSelectionLabel, BorderLayout.NORTH);
        fileSelectionPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);
        fileSelectionPanel.add(fileButtonPanel, BorderLayout.SOUTH);
        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Export Options"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        optionsPanel.add(new JLabel("Output Format:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        optionsPanel.add(formatComboBox, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        optionsPanel.add(new JLabel("Filename Prefix:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        optionsPanel.add(prefixTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        optionsPanel.add(new JLabel("Output Directory:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        JPanel outputDirPanel = new JPanel(new BorderLayout(5, 0));
        outputDirPanel.add(outputDirTextField, BorderLayout.CENTER);
        outputDirPanel.add(browseOutputButton, BorderLayout.EAST);
        optionsPanel.add(outputDirPanel, gbc);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(progressBar, BorderLayout.SOUTH);
        mainPanel.add(optionsPanel, BorderLayout.NORTH);
        mainPanel.add(fileSelectionPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(statusPanel, BorderLayout.PAGE_END);

        add(mainPanel);
    }

    private void setupEventHandlers() {
        addFilesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Select DICOM Files");
                fileChooser.setMultiSelectionEnabled(true);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fileChooser.setAcceptAllFileFilterUsed(true);

                int result = fileChooser.showOpenDialog(DicomBatchExporter.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File[] files = fileChooser.getSelectedFiles();
                    int addedCount = 0;

                    for (File file : files) {
                        if (!selectedFiles.contains(file)) {
                            if (file.isFile()) {
                                selectedFiles.add(file);
                                addedCount++;
                            } else if (file.isDirectory()) {
                                List<File> dicomFiles = dicomFileManager.findAllFilesRecursively(file);
                                for (File dicomFile : dicomFiles) {
                                    if (dicomFileManager.isDicomFile(dicomFile) &&
                                            dicomFileManager.hasPixelData(dicomFile) &&
                                            !selectedFiles.contains(dicomFile)) {
                                        selectedFiles.add(dicomFile);
                                        addedCount++;
                                    }
                                }
                            }
                        }
                    }

                    refreshFileList();
                    statusLabel.setText("Added " + addedCount + " files to export list");
                }
            }
        });
        clearListButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedFiles.clear();
                refreshFileList();
                statusLabel.setText("Export list cleared");
            }
        });
        browseOutputButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser dirChooser = new JFileChooser();
                dirChooser.setDialogTitle("Select Output Directory");
                dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                dirChooser.setAcceptAllFileFilterUsed(true);

                int result = dirChooser.showOpenDialog(DicomBatchExporter.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File directory = dirChooser.getSelectedFile();
                    outputDirTextField.setText(directory.getAbsolutePath());
                    statusLabel.setText("Output directory set to: " + directory.getAbsolutePath());
                }
            }
        });
        startExportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedFiles.isEmpty()) {
                    JOptionPane.showMessageDialog(DicomBatchExporter.this,
                            "No files selected for export",
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (outputDirTextField.getText().isEmpty()) {
                    JOptionPane.showMessageDialog(DicomBatchExporter.this,
                            "No output directory selected",
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String format = formatComboBox.getSelectedItem().toString().toLowerCase();
                String prefix = prefixTextField.getText();
                if (prefix.equals("Enter prefix (optional)")) {
                    prefix = "";
                }

                File outputDir = new File(outputDirTextField.getText());
                startExport(format, prefix, outputDir);
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void startExport(String format, String prefix, File outputDir) {
        startExportButton.setEnabled(false);
        cancelButton.setEnabled(false);
        addFilesButton.setEnabled(false);
        clearListButton.setEnabled(false);
        browseOutputButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setMaximum(selectedFiles.size());
        progressBar.setValue(0);

        SwingWorker<Void, String> exportWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                int successCount = 0;
                int failureCount = 0;

                for (int i = 0; i < selectedFiles.size(); i++) {
                    File file = selectedFiles.get(i);
                    publish("Exporting: " + file.getName());

                    try {
                        BufferedImage image = dicomFileManager.loadImage(file);
                        if (image == null) {
                            throw new Exception("Failed to load image");
                        }
                        String originalName = file.getName();
                        String baseName;
                        int dotIndex = originalName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            baseName = originalName.substring(0, dotIndex);
                        } else {
                            baseName = originalName;
                        }
                        String outputName;
                        if (prefix != null && !prefix.trim().isEmpty()) {
                            outputName = prefix.trim() + baseName + "." + format;
                        } else {
                            outputName = baseName + "." + format;
                        }

                        File outputFile = new File(outputDir, outputName);
                        dicomFileManager.exportImage(image, format, outputFile);

                        successCount++;
                    } catch (Exception e) {
                        failureCount++;
                        publish("Error exporting " + file.getName() + ": " + e.getMessage());
                    }
                    progressBar.setValue(i + 1);
                }

                publish("Export completed: " + successCount + " successful, " + failureCount + " failed");
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                statusLabel.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                startExportButton.setEnabled(true);
                cancelButton.setEnabled(true);
                addFilesButton.setEnabled(true);
                clearListButton.setEnabled(true);
                browseOutputButton.setEnabled(true);
                progressBar.setVisible(false);

                try {
                    get();
                    JOptionPane.showMessageDialog(DicomBatchExporter.this,
                            "Export completed successfully",
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(DicomBatchExporter.this,
                            "Export failed: " + e.getCause().getMessage(),
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        exportWorker.execute();
    }
}