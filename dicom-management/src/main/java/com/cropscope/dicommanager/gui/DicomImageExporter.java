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
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DicomImageExporter extends JDialog {
    private DicomFileManager dicomFileManager;
    private List<File> selectedFiles = new ArrayList<>();
    private JList<File> fileList;
    private DefaultListModel<File> fileListModel;
    private JScrollPane fileListScrollPane;
    private JComboBox<String> formatComboBox;
    private JTextField prefixTextField;
    private JTextField outputDirTextField;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton exportButton;
    private JButton cancelButton;
    private JButton selectFilesButton;
    private JButton selectDirButton;
    private JButton browseOutputButton;

    public DicomImageExporter(JFrame parent, DicomFileManager dicomFileManager) {
        super(parent, "DICOM Image Exporter", true);
        this.dicomFileManager = dicomFileManager;

        initializeComponents();
        layoutComponents();
        setupEventHandlers();

        setSize(600, 400);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    public static void showExporter(JFrame parent, DicomFileManager dicomFileManager) {
        DicomImageExporter exporter = new DicomImageExporter(parent, dicomFileManager);
        exporter.setVisible(true);
    }

    private void initializeComponents() {
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.setVisibleRowCount(8);
        fileListScrollPane = new JScrollPane(fileList);
        String[] formats = {"PNG", "JPEG", "BMP"};
        formatComboBox = new JComboBox<>(formats);
        prefixTextField = new JTextField(20);
        outputDirTextField = new JTextField(20);
        outputDirTextField.setEditable(false);
        selectFilesButton = new JButton("Select Files");
        selectDirButton = new JButton("Select Directory");
        browseOutputButton = new JButton("Browse...");
        exportButton = new JButton("Export");
        cancelButton = new JButton("Cancel");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        statusLabel = new JLabel("Ready");
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel fileSelectionPanel = new JPanel(new BorderLayout(5, 5));
        JPanel fileButtonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        fileButtonPanel.add(selectFilesButton);
        fileButtonPanel.add(selectDirButton);

        JLabel fileSelectionLabel = new JLabel("Selected Files:");
        fileSelectionPanel.add(fileSelectionLabel, BorderLayout.NORTH);
        fileSelectionPanel.add(fileListScrollPane, BorderLayout.CENTER);
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
        buttonPanel.add(exportButton);
        buttonPanel.add(cancelButton);
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(progressBar, BorderLayout.SOUTH);
        mainPanel.add(fileSelectionPanel, BorderLayout.CENTER);
        mainPanel.add(optionsPanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(statusPanel, BorderLayout.PAGE_END);

        add(mainPanel);
    }

    private void setupEventHandlers() {
        selectFilesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Select DICOM Files");
                fileChooser.setMultiSelectionEnabled(true);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setAcceptAllFileFilterUsed(true);

                int result = fileChooser.showOpenDialog(DicomImageExporter.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File[] files = fileChooser.getSelectedFiles();
                    for (File file : files) {
                        if (!fileListModel.contains(file)) {
                            fileListModel.addElement(file);
                        }
                    }
                    updateStatus("Added " + files.length + " files to export list");
                }
            }
        });
        selectDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser dirChooser = new JFileChooser();
                dirChooser.setDialogTitle("Select Directory with DICOM Files");
                dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                dirChooser.setAcceptAllFileFilterUsed(true);

                int result = dirChooser.showOpenDialog(DicomImageExporter.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File directory = dirChooser.getSelectedFile();
                    List<File> dicomFiles = dicomFileManager.findAllFilesRecursively(directory);

                    int addedCount = 0;
                    for (File file : dicomFiles) {
                        if (dicomFileManager.isDicomFile(file) && dicomFileManager.hasPixelData(file)) {
                            if (!fileListModel.contains(file)) {
                                fileListModel.addElement(file);
                                addedCount++;
                            }
                        }
                    }

                    updateStatus("Added " + addedCount + " DICOM image files from directory");
                }
            }
        });
        browseOutputButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser dirChooser = new JFileChooser();
                dirChooser.setDialogTitle("Select Output Directory");
                dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                dirChooser.setAcceptAllFileFilterUsed(true);

                int result = dirChooser.showOpenDialog(DicomImageExporter.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File directory = dirChooser.getSelectedFile();
                    outputDirTextField.setText(directory.getAbsolutePath());
                    updateStatus("Output directory set to: " + directory.getAbsolutePath());
                }
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fileListModel.isEmpty()) {
                    JOptionPane.showMessageDialog(DicomImageExporter.this,
                            "No files selected for export",
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (outputDirTextField.getText().isEmpty()) {
                    JOptionPane.showMessageDialog(DicomImageExporter.this,
                            "No output directory selected",
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                selectedFiles.clear();
                for (int i = 0; i < fileListModel.size(); i++) {
                    selectedFiles.add(fileListModel.get(i));
                }
                String format = formatComboBox.getSelectedItem().toString().toLowerCase();
                String prefix = prefixTextField.getText().trim();
                File outputDir = new File(outputDirTextField.getText());
                startExport(selectedFiles, format, prefix, outputDir);
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void startExport(List<File> files, String format, String prefix, File outputDir) {
        exportButton.setEnabled(false);
        cancelButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setMaximum(files.size());
        progressBar.setValue(0);

        SwingWorker<Void, String> exportWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                int successCount = 0;
                int failureCount = 0;

                for (int i = 0; i < files.size(); i++) {
                    File file = files.get(i);
                    publish("Exporting: " + file.getName());

                    try {
                        BufferedImage image = dicomFileManager.loadImage(file);
                        if (image == null) {
                            throw new Exception("Failed to load image");
                        }
                        String originalName = file.getName();
                        String baseName = originalName.substring(0, originalName.lastIndexOf('.'));
                        String outputName = prefix + baseName + "." + format;
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
                updateStatus(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                exportButton.setEnabled(true);
                cancelButton.setEnabled(true);
                progressBar.setVisible(false);

                try {
                    get();
                    JOptionPane.showMessageDialog(DicomImageExporter.this,
                            "Export completed successfully",
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(DicomImageExporter.this,
                            "Export failed: " + e.getCause().getMessage(),
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        exportWorker.execute();
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
}