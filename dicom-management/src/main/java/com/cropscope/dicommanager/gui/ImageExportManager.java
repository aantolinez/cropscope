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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ImageExportManager extends JDialog {
    private DicomFileManager dicomFileManager;
    private JTree dicomFileTree;
    private List<File> exportList = new ArrayList<>();

    private JList<File> fileList;
    private DefaultListModel<File> fileListModel;
    private JComboBox<String> formatComboBox;
    private JTextField prefixTextField;
    private JTextField outputDirTextField;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton exportButton;
    private JButton cancelButton;
    private JButton browseOutputButton;
    private JButton clearListButton;
    private JScrollPane fileListScrollPane;
    private JPopupMenu treePopupMenu;

    public ImageExportManager(JFrame parent, DicomFileManager dicomFileManager,
                              JTree dicomFileTree) {
        super(parent, "DICOM Image Export Manager", true);
        this.dicomFileManager = dicomFileManager;
        this.dicomFileTree = dicomFileTree;

        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        setupTreePopupMenu();

        setSize(600, 400);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initializeComponents() {
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.setVisibleRowCount(8);
        fileListScrollPane = new JScrollPane(fileList);
        String[] formats = {"PNG", "JPEG", "BMP"};
        formatComboBox = new JComboBox<>(formats);
        formatComboBox.setSelectedIndex(0);
        prefixTextField = new JTextField(20);
        outputDirTextField = new JTextField(20);
        outputDirTextField.setEditable(false);
        browseOutputButton = new JButton("Browse...");
        exportButton = new JButton("Export");
        cancelButton = new JButton("Cancel");
        clearListButton = new JButton("Clear List");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        statusLabel = new JLabel("Ready");
        treePopupMenu = new JPopupMenu();
        JMenuItem addToExportItem = new JMenuItem("Add to Export List");
        addToExportItem.addActionListener(e -> addSelectedFilesToExportList());
        treePopupMenu.add(addToExportItem);
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel fileSelectionPanel = new JPanel(new BorderLayout(5, 5));
        JPanel fileButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        fileButtonPanel.add(clearListButton);

        JLabel fileSelectionLabel = new JLabel("Export List:");
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
        browseOutputButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser dirChooser = new JFileChooser();
                dirChooser.setDialogTitle("Select Output Directory");
                dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                dirChooser.setAcceptAllFileFilterUsed(true);

                int result = dirChooser.showOpenDialog(ImageExportManager.this);
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
                    JOptionPane.showMessageDialog(ImageExportManager.this,
                            "No files selected for export",
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (outputDirTextField.getText().isEmpty()) {
                    JOptionPane.showMessageDialog(ImageExportManager.this,
                            "No output directory selected",
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                exportList.clear();
                for (int i = 0; i < fileListModel.size(); i++) {
                    exportList.add(fileListModel.get(i));
                }
                String format = formatComboBox.getSelectedItem().toString().toLowerCase();
                String prefix = prefixTextField.getText().trim();
                File outputDir = new File(outputDirTextField.getText());
                startExport(exportList, format, prefix, outputDir);
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        clearListButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fileListModel.clear();
                exportList.clear();
                updateStatus("Export list cleared");
            }
        });
    }

    private void setupTreePopupMenu() {
        dicomFileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = dicomFileTree.getRowForLocation(e.getX(), e.getY());
                    if (row >= 0) {
                        dicomFileTree.setSelectionRow(row);
                        TreePath path = dicomFileTree.getPathForRow(row);
                        if (path != null) {
                            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                            Object userObject = node.getUserObject();
                            if (userObject.toString() != null) {
                                treePopupMenu.show(dicomFileTree, e.getX(), e.getY());
                            }
                        }
                    }
                }
            }
        });
    }

    private void addSelectedFilesToExportList() {
        TreePath[] selectedPaths = dicomFileTree.getSelectionPaths();

        if (selectedPaths == null) {
            return;
        }

        int addedCount = 0;

        for (TreePath path : selectedPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObject = node.getUserObject();

            if (userObject != null) {
                File file = null;
                try {
                    Method getFileMethod = userObject.getClass().getMethod("getFile");
                    file = (File) getFileMethod.invoke(userObject);
                } catch (Exception ex) {
                    if (userObject instanceof File) {
                        file = (File) userObject;
                    }
                }

                if (file != null) {
                    if (!file.isDirectory()) {
                        if (!fileListModel.contains(file)) {
                            fileListModel.addElement(file);
                            addedCount++;
                        }
                    } else {
                        List<File> imageFilesInDir = getImageFilesInDirectory(file);
                        for (File imageFile : imageFilesInDir) {
                            if (!fileListModel.contains(imageFile)) {
                                fileListModel.addElement(imageFile);
                                addedCount++;
                            }
                        }
                    }
                }
            }
        }

        updateStatus("Added " + addedCount + " files to export list");
    }

    private List<File> getImageFilesInDirectory(File directory) {
        List<File> imageFiles = new ArrayList<>();

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        if (dicomFileManager.isDicomFile(file) && dicomFileManager.hasPixelData(file)) {
                            imageFiles.add(file);
                        }
                    } else if (file.isDirectory()) {
                        imageFiles.addAll(getImageFilesInDirectory(file));
                    }
                }
            }
        }

        return imageFiles;
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
                    JOptionPane.showMessageDialog(ImageExportManager.this,
                            "Export completed successfully",
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(ImageExportManager.this,
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

    public static void showExporter(JFrame parent, DicomFileManager dicomFileManager,
                                    JTree dicomFileTree) {
        ImageExportManager exporter = new ImageExportManager(parent, dicomFileManager, dicomFileTree);
        exporter.setVisible(true);
    }
}