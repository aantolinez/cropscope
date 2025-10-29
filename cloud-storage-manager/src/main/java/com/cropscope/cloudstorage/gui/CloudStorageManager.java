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

package com.cropscope.cloudstorage.gui;

import com.cropscope.cloudstorage.model.ConnectionProfile;
import com.cropscope.cloudstorage.model.StorageObjectSummary;
import com.cropscope.cloudstorage.service.ConnectionProfileManager;
import com.cropscope.cloudstorage.service.StorageService;
import com.cropscope.cloudstorage.service.S3Service;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class CloudStorageManager extends JFrame {
    private StorageService storageService;
    private String bucketName;
    private JTextField bucketField;
    private JTextArea logArea;
    private JTable filesTable;
    private DefaultTableModel tableModel;
    private JTabbedPane tabbedPane;
    private JComboBox<String> bucketComboBox;
    private JComboBox<String> connectionCombo;
    private JButton connectBtn;

    private ConnectionProfileManager profileManager;

    private JLabel connLabel;
    private JLabel bucketLabel;

    private JDialog progressDialog;
    private JProgressBar dialogProgressBar;
    private JLabel dialogStatusLabel;
    private JButton acceptButton;

    public CloudStorageManager() {
        setTitle("Cloud Storage Manager");
        setSize(1320, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        createMenuBar();
        bucketField = new JTextField();
        bucketComboBox = new JComboBox<>();
        connectionCombo = new JComboBox<>();
        tableModel = new DefaultTableModel(new String[]{"Key", "Size (bytes)", "Last Modified"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        try {
            profileManager = new ConnectionProfileManager();
            log("Profile manager initialized successfully");
        } catch (Exception e) {
            log("Failed to initialize profile manager: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to load connection profiles: " + e.getMessage(),
                    "Initialization Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);
        JPanel filePanel = createFileOperationsTab();
        tabbedPane.addTab("File Operations", filePanel);

        JPanel bucketPanel = createBucketOperationsTab();
        tabbedPane.addTab("Bucket Operations", bucketPanel);
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        bottomPanel.add(new JLabel("Connection:"));
        updateConnectionCombo();
        bottomPanel.add(connectionCombo);

        connectBtn = new JButton("Connect");
        styleButton(connectBtn);
        connectBtn.addActionListener(e -> connectToSelectedProfile());
        Dimension originalSize = connectBtn.getPreferredSize();
        connectBtn.setPreferredSize(new Dimension((int) (originalSize.width * 0.75), (int) (originalSize.height * 0.75)));
        connectBtn.setMaximumSize(connectBtn.getPreferredSize());
        connectBtn.setMinimumSize(connectBtn.getPreferredSize());
        bottomPanel.add(connectBtn);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to exit?",
                    "Confirm Exit",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                if (storageService != null) storageService.disconnect();
                System.exit(0);
            }
        });
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        JMenu settingsMenu = new JMenu("Settings");
        JMenuItem manageConnectionsItem = new JMenuItem("Manage Connections");
        manageConnectionsItem.addActionListener(e -> showManageConnectionsDialog());
        settingsMenu.add(manageConnectionsItem);
        menuBar.add(settingsMenu);
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        String aboutMessage = "<html><b>Cloud Storage Manager</b><br>Version 1.0<br>Supports AWS S3 and other compatible services.</html>";
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(
                this,
                aboutMessage,
                "About",
                JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private JPanel createFileOperationsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel bucketPanel = new JPanel(new BorderLayout());
        bucketPanel.add(new JLabel("Bucket:"), BorderLayout.WEST);
        bucketComboBox.setEditable(true);
        bucketComboBox.addActionListener(e -> {
            String selected = (String) bucketComboBox.getSelectedItem();
            if (selected != null && !selected.trim().isEmpty()) {
                bucketField.setText(selected);
                updateBucketLabel();
            }
        });
        bucketPanel.add(bucketComboBox, BorderLayout.CENTER);

        JButton listBucketsBtn = new JButton("List Buckets");
        styleButton(listBucketsBtn);
        listBucketsBtn.setToolTipText("Refresh the list of available buckets");
        listBucketsBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        Dimension originalSize = listBucketsBtn.getPreferredSize();
        listBucketsBtn.setPreferredSize(new Dimension((int) (originalSize.width * 0.75), (int) (originalSize.height * 0.75)));
        listBucketsBtn.setMaximumSize(new Dimension((int) (originalSize.width * 0.75), (int) (originalSize.height * 0.75)));
        listBucketsBtn.addActionListener(this::listBuckets);
        bucketPanel.add(listBucketsBtn, BorderLayout.EAST);
        topPanel.add(bucketPanel, BorderLayout.NORTH);
        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.add(new JLabel("Bucket Name:"), BorderLayout.WEST);
        bucketField.setText(bucketName);
        bucketField.addActionListener(e -> updateBucketLabel());

        bucketField.addActionListener(e -> {
            String selected = bucketField.getText().trim();
            if (!selected.isEmpty()) {
                bucketComboBox.setSelectedItem(selected);
            }
        });
        namePanel.add(bucketField, BorderLayout.CENTER);
        topPanel.add(namePanel, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBucketOperationsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        connLabel = new JLabel("Current connection: <none>");
        connLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        connLabel.setForeground(Color.BLACK);
        statusPanel.add(connLabel);
        bucketLabel = new JLabel();
        updateBucketLabel();
        bucketLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        bucketLabel.setForeground(Color.BLACK);
        statusPanel.add(bucketLabel);

        connectionCombo.addActionListener(e -> {
            String conn = (String) connectionCombo.getSelectedItem();
            connLabel.setText("Current connection: " + (conn != null ? conn : "<none>"));
        });
        bucketField.addActionListener(e -> {
            String bucket = bucketField.getText().trim();
            bucketLabel.setText("Current bucket: " + (bucket.isEmpty() ? "<none>" : bucket));
        });

        panel.add(statusPanel, BorderLayout.NORTH);
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JButton createBucketBtn = new JButton("Create Bucket");
        JButton deleteBucketBtn = new JButton("Delete Bucket");
        JButton listFilesBtn = new JButton("List Files");
        JButton deleteFileBtn = new JButton("Delete Selected File");
        JButton renameFileBtn = new JButton("Rename Selected File");
        JButton uploadBtn = new JButton("📁 Upload File");
        JButton uploadDirBtn = new JButton("📁 Upload Directory");
        JButton downloadBtn = new JButton("💾 Download");
        styleButton(createBucketBtn);
        styleButton(deleteBucketBtn);
        styleButton(listFilesBtn);
        styleButton(deleteFileBtn);
        styleButton(renameFileBtn);
        styleButton(uploadBtn);
        styleButton(uploadDirBtn);
        styleButton(downloadBtn);
        Dimension targetSize = deleteFileBtn.getPreferredSize();
        createBucketBtn.setPreferredSize(targetSize);
        deleteBucketBtn.setPreferredSize(targetSize);
        listFilesBtn.setPreferredSize(targetSize);
        deleteFileBtn.setPreferredSize(targetSize);
        renameFileBtn.setPreferredSize(targetSize);
        uploadBtn.setPreferredSize(targetSize);
        uploadDirBtn.setPreferredSize(targetSize);
        downloadBtn.setPreferredSize(targetSize);
        for (JButton btn : new JButton[]{
                createBucketBtn, deleteBucketBtn, listFilesBtn, deleteFileBtn,
                renameFileBtn, uploadBtn, uploadDirBtn, downloadBtn
        }) {
            btn.setMaximumSize(targetSize);
            btn.setMinimumSize(targetSize);
        }
        createBucketBtn.addActionListener(e -> createBucket());
        deleteBucketBtn.addActionListener(e -> deleteBucket());
        listFilesBtn.addActionListener(this::listFiles);
        deleteFileBtn.addActionListener(this::deleteFile);
        renameFileBtn.addActionListener(this::renameFile);
        uploadBtn.addActionListener(this::handleUpload);
        uploadDirBtn.addActionListener(this::handleUploadDirectory);
        downloadBtn.addActionListener(this::handleDownload);
        topPanel.add(createBucketBtn);
        topPanel.add(deleteBucketBtn);
        topPanel.add(listFilesBtn);
        topPanel.add(deleteFileBtn);
        topPanel.add(renameFileBtn);
        topPanel.add(uploadBtn);
        topPanel.add(uploadDirBtn);
        topPanel.add(downloadBtn);

        panel.add(topPanel, BorderLayout.CENTER);
        filesTable = new JTable(tableModel);
        JScrollPane tableScrollPane = new JScrollPane(filesTable);
        panel.add(tableScrollPane, BorderLayout.SOUTH);

        return panel;
    }

    private void resizeButtonTo75Percent(JButton btn) {
        Dimension size = btn.getPreferredSize();
        btn.setPreferredSize(new Dimension((int) (size.width * 0.75), (int) (size.height * 0.75)));
    }

    private void styleButton(JButton btn) {
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setBackground(new Color(220, 220, 220));
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(150, 36));
    }

    private void resetUI() {
        if (storageService != null) {
            storageService.disconnect();
        }
        storageService = null;
        bucketField.setText("");
        bucketComboBox.removeAllItems();
        tableModel.setRowCount(0);
        logArea.setText("");
        if (connLabel != null) connLabel.setText("Current connection: <none>");
        if (bucketLabel != null) bucketLabel.setText("Current bucket: <none>");
    }

    private void connectToSelectedProfile() {
        String selectedName = (String) connectionCombo.getSelectedItem();
        if (selectedName == null) {
            log("No connection selected.");
            return;
        }
        resetUI();

        ConnectionProfile profile = profileManager.getConnection(selectedName);
        if (profile == null) return;

        try {
            storageService = createStorageService(profile);
            boolean connected = storageService.connect();

            if (!connected) {
                log("Connection failed: Could not connect to endpoint");
                storageService = null;
                return;
            }

            bucketName = bucketField.getText().trim();
            if (!bucketName.isEmpty() && !storageService.bucketExists(bucketName)) {
                log("Bucket does not exist: " + bucketName);
                storageService.disconnect();
                storageService = null;
                return;
            }

            if (connLabel != null) connLabel.setText("Current connection: " + profile.getName());
            if (!bucketName.isEmpty()) {
                bucketField.setText(bucketName);
                updateBucketLabel();
            }

            log("Connected to: " + profile.getName() + " (" + profile.getEndpoint() + ")");
        } catch (Exception e) {
            log("Connection failed: " + e.getMessage());
            storageService = null;
        }
    }

    private StorageService createStorageService(ConnectionProfile profile) {
        switch (profile.getType()) {
            case "AWS_S3":
                return new S3Service(profile);
            default:
                throw new IllegalArgumentException("Unsupported storage type: " + profile.getType());
        }
    }

    private void updateBucketLabel() {
        String bucket = bucketField.getText().trim();
        bucketLabel.setText("Current bucket: " + (bucket.isEmpty() ? "<none>" : bucket));
    }

    private void handleUpload(ActionEvent e) {
        if (storageService == null) {
            log("Not connected. Please connect first.");
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select File to Upload");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setAcceptAllFileFilterUsed(true);

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (file.isFile()) {
                String defaultKey = file.getName();
                String input = (String) JOptionPane.showInputDialog(
                        this,
                        "Enter S3 object key (e.g., raw/2025/data.csv):",
                        "Upload to S3",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        null,
                        defaultKey
                );

                if (input == null || input.trim().isEmpty()) {
                    log("Upload canceled: no key provided.");
                    return;
                }

                final String objectKey = input.trim();

                new SwingWorker<Boolean, String>() {
                    @Override
                    protected Boolean doInBackground() throws Exception {
                        publish("Uploading as: " + objectKey);
                        return storageService.uploadFile(bucketName, file, objectKey);
                    }

                    @Override
                    protected void process(List<String> chunks) {
                        for (String msg : chunks) log(msg);
                    }

                    @Override
                    protected void done() {
                        try {
                            if (get()) {
                                log("Uploaded: " + file.getName() + " → s3://" + bucketName + "/" + objectKey);
                            } else {
                                log("Upload failed: " + file.getName());
                            }
                        } catch (Exception ex) {
                            log("Upload error: " + ex.getMessage());
                        }
                    }
                }.execute();
            }
        }
    }

    private void handleUploadDirectory(ActionEvent e) {
        if (storageService == null || !storageService.isConnected()) {
            log("Not connected.");
            return;
        }
        if (bucketName == null || bucketName.trim().isEmpty()) {
            log("No bucket selected.");
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select Directory to Upload");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File dir = fc.getSelectedFile();
        if (!dir.isDirectory()) return;

        String prefixInput = JOptionPane.showInputDialog(
                this,
                "Enter S3 key prefix (e.g., data/2025/), or leave empty:",
                "Directory Upload",
                JOptionPane.QUESTION_MESSAGE
        );
        String prefix = (prefixInput == null) ? "" : (prefixInput.endsWith("/") ? prefixInput : prefixInput + "/");

        int[] totalFiles = {0};
        countFiles(dir, totalFiles);
        if (totalFiles[0] == 0) {
            log("No files to upload.");
            return;
        }

        new SwingWorker<Boolean, String>() {
            int uploadedFiles = 0;

            @Override
            protected Boolean doInBackground() throws Exception {
                SwingUtilities.invokeLater(() -> showProgressDialog());
                return uploadDirectoryRecursively(dir, bucketName, prefix, s -> {
                    uploadedFiles++;
                    int percent = (int) (((double) uploadedFiles / totalFiles[0]) * 100);
                    publish(s, String.valueOf(uploadedFiles), String.valueOf(totalFiles[0]), String.valueOf(percent));
                });
            }

            @Override
            protected void process(List<String> chunks) {
                String latestStatus = chunks.get(chunks.size() - 3);
                String percentStr = chunks.get(chunks.size() - 1);
                dialogStatusLabel.setText(latestStatus);
                dialogProgressBar.setValue(Integer.parseInt(percentStr));
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    dialogStatusLabel.setText(success ? "Upload completed!" : "Upload failed.");
                    dialogProgressBar.setValue(100);
                    acceptButton.setEnabled(true);
                    log(success ? "Directory upload completed." : "Directory upload failed.");
                } catch (Exception ex) {
                    log("Upload error: " + ex.getMessage());
                    dialogStatusLabel.setText("Error: " + ex.getMessage());
                    acceptButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void showProgressDialog() {
        progressDialog = new JDialog(this, "Upload Progress", true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(this);

        dialogProgressBar = new JProgressBar(0, 100);
        dialogProgressBar.setStringPainted(true);
        dialogProgressBar.setValue(0);

        dialogStatusLabel = new JLabel("Starting upload...");
        dialogStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        acceptButton = new JButton("Accept");
        acceptButton.setEnabled(false);
        acceptButton.addActionListener(ev -> progressDialog.dispose());
        buttonPanel.add(acceptButton);

        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        progressPanel.add(dialogStatusLabel, BorderLayout.NORTH);
        progressPanel.add(dialogProgressBar, BorderLayout.CENTER);

        progressDialog.add(progressPanel, BorderLayout.CENTER);
        progressDialog.add(buttonPanel, BorderLayout.SOUTH);

        progressDialog.setVisible(true);
    }

    private boolean uploadDirectoryRecursively(File dir, String bucketName, String prefix, Consumer<String> publisher) {
        boolean success = true;
        File[] files = dir.listFiles();
        if (files == null) return true;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!uploadDirectoryRecursively(file, bucketName, prefix + file.getName() + "/", publisher)) {
                    success = false;
                }
            } else {
                String objectKey = prefix + getRelativePath(file, dir);
                publisher.accept("Uploading: " + objectKey);
                try {
                    boolean uploaded = storageService.uploadFile(bucketName, file, objectKey);
                    if (!uploaded) {
                        log("Failed: " + objectKey);
                        success = false;
                    }
                } catch (Exception ex) {
                    log("Error: " + ex.getMessage());
                    success = false;
                }
            }
        }
        return success;
    }

    private void countFiles(File dir, int[] count) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile()) count[0]++;
            else if (file.isDirectory()) countFiles(file, count);
        }
    }

    private String getRelativePath(File file, File baseDir) {
        return baseDir.toURI().relativize(file.toURI()).getPath();
    }

    private void handleDownload(ActionEvent e) {
        if (storageService == null) {
            log("Not connected.");
            return;
        }
        int row = filesTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a file to download.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String key = (String) tableModel.getValueAt(row, 0);
        if (key == null || key.trim().isEmpty()) {
            log("Invalid file key.");
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save As");
        fc.setSelectedFile(new File(key));
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File saveFile = fc.getSelectedFile();
            if (saveFile.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(
                        this,
                        "File already exists. Overwrite?",
                        "Confirm Overwrite",
                        JOptionPane.YES_NO_OPTION
                );
                if (overwrite != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            new SwingWorker<Boolean, String>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    publish("Downloading: " + key);
                    return storageService.downloadFile(bucketName, key, saveFile);
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String msg : chunks) log(msg);
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            log("Downloaded: " + saveFile.getAbsolutePath());
                            JOptionPane.showMessageDialog(
                                    CloudStorageManager.this,
                                    "File downloaded successfully:\n" + saveFile.getAbsolutePath(),
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        } else {
                            log("Download failed: " + key);
                            JOptionPane.showMessageDialog(
                                    CloudStorageManager.this,
                                    "Download failed. Check logs for details.",
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                    } catch (Exception ex) {
                        log("Download error: " + ex.getMessage());
                        JOptionPane.showMessageDialog(
                                CloudStorageManager.this,
                                "Error: " + ex.getMessage(),
                                "Download Failed",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            }.execute();
        }
    }

    private void createBucket() {
        if (storageService == null) {
            log("Not connected.");
            return;
        }
        String bucketName = bucketField.getText().trim();
        if (bucketName.isEmpty()) {
            log("Please enter a bucket name");
            return;
        }
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return storageService.createBucket(bucketName);
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        log("Bucket created: " + bucketName);
                        listBuckets(null);
                    } else {
                        log("Failed to create bucket: " + bucketName);
                    }
                } catch (Exception ex) {
                    log("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void deleteBucket() {
        if (storageService == null) {
            log("Not connected.");
            return;
        }
        String bucketName = bucketField.getText().trim();
        if (bucketName.isEmpty()) {
            log("Please enter a bucket name");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete bucket '" + bucketName + "'? All files must be deleted first.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return storageService.deleteBucket(bucketName);
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        log("Bucket deleted: " + bucketName);
                        bucketComboBox.removeItem(bucketName);
                    } else {
                        log("Failed to delete bucket: " + bucketName);
                    }
                } catch (Exception ex) {
                    log("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void listBuckets(ActionEvent e) {
        if (storageService == null) {
            connectToSelectedProfile();
            if (storageService == null) return;
        }
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return storageService.listBuckets();
            }

            @Override
            protected void done() {
                try {
                    List<String> buckets = get();
                    if (buckets != null && !buckets.isEmpty()) {
                        updateBucketCombo(buckets);
                        StringBuilder sb = new StringBuilder("Buckets (" + buckets.size() + " found):\n");
                        for (String bucket : buckets) {
                            sb.append("  • ").append(bucket).append("\n");
                        }
                        log(sb.toString());
                    } else {
                        log("No buckets found or unable to list.");
                    }
                } catch (Exception ex) {
                    log("Error listing buckets: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void updateBucketCombo(List<String> bucketNames) {
        bucketComboBox.removeAllItems();
        for (String name : bucketNames) {
            bucketComboBox.addItem(name);
        }
        if (bucketName != null && !bucketName.isEmpty()) {
            bucketComboBox.setSelectedItem(bucketName);
        }
    }

    private void listFiles(ActionEvent e) {
        if (storageService == null) {
            connectToSelectedProfile();
            if (storageService == null) return;
        }
        bucketName = bucketField.getText().trim();
        if (bucketName.isEmpty()) {
            log("Please enter a bucket name");
            return;
        }
        new SwingWorker<List<StorageObjectSummary>, Void>() {
            @Override
            protected List<StorageObjectSummary> doInBackground() throws Exception {
                return storageService.listObjects(bucketName);
            }

            @Override
            protected void done() {
                try {
                    List<StorageObjectSummary> objects = get();
                    if (objects != null) {
                        tableModel.setRowCount(0);
                        for (StorageObjectSummary obj : objects) {
                            tableModel.addRow(new Object[]{obj.getKey(), obj.getSize(), obj.getLastModified()});
                        }
                        log("Found " + objects.size() + " files in bucket: " + bucketName);
                    }
                } catch (Exception ex) {
                    log("Error listing files: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void deleteFile(ActionEvent e) {
        if (storageService == null) {
            log("Not connected.");
            return;
        }
        int row = filesTable.getSelectedRow();
        if (row == -1) {
            log("Select a file to delete.");
            return;
        }
        String key = (String) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete file: " + key + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    storageService.deleteObject(bucketName, key);
                    return null;
                }

                @Override
                protected void done() {
                    log("File deleted: " + key);
                    listFiles(null);
                }
            }.execute();
        }
    }

    private void renameFile(ActionEvent e) {
        if (storageService == null) {
            log("Not connected.");
            return;
        }
        int row = filesTable.getSelectedRow();
        if (row == -1) {
            log("Select a file to rename.");
            return;
        }
        String oldKey = (String) tableModel.getValueAt(row, 0);
        String newKey = JOptionPane.showInputDialog(this, "New name:", oldKey);
        if (newKey != null && !newKey.trim().isEmpty() && !newKey.equals(oldKey)) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    storageService.renameObject(bucketName, oldKey, newKey);
                    return null;
                }

                @Override
                protected void done() {
                    log("Renamed: " + oldKey + " → " + newKey);
                    listFiles(null);
                }
            }.execute();
        }
    }

    private void showManageConnectionsDialog() {
        if (profileManager == null) {
            log("Profile manager not initialized");
            return;
        }
        JDialog dialog = new JDialog(this, "Manage Connections", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(550, 450);
        dialog.setLocationRelativeTo(this);

        List<String> connectionNames = profileManager.listConnections();
        List<ConnectionProfile> sortedProfiles = new ArrayList<>();
        for (String name : connectionNames) {
            ConnectionProfile p = profileManager.getConnection(name);
            if (p != null) sortedProfiles.add(p);
        }
        sortedProfiles.sort(Comparator.comparing(p -> p.getName().toLowerCase()));

        DefaultListModel<ConnectionProfile> listModel = new DefaultListModel<>();
        sortedProfiles.forEach(listModel::addElement);

        JList<ConnectionProfile> profileList = new JList<>(listModel);
        profileList.setCellRenderer(new ProfileListRenderer());
        JScrollPane scroll = new JScrollPane(profileList);
        dialog.add(scroll, BorderLayout.CENTER);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        statusLabel.setForeground(Color.GRAY);
        dialog.add(statusLabel, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.Y_AXIS));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton addBtn = new JButton("Add");
        styleButton(addBtn);
        addBtn.setPreferredSize(new Dimension(80, 28));
        addBtn.addActionListener(ev -> {
            ConnectionProfile p = showConnectionEditor(null);
            if (p != null) {
                profileManager.saveProfile(p);
                listModel.addElement(p);
                updateConnectionCombo();
            }
        });

        JButton editBtn = new JButton("Edit");
        styleButton(editBtn);
        editBtn.setPreferredSize(new Dimension(80, 28));
        editBtn.addActionListener(ev -> {
            ConnectionProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                ConnectionProfile edited = showConnectionEditor(selected);
                if (edited != null) {
                    profileManager.saveProfile(edited);
                    listModel.clear();
                    profileManager.listConnections().forEach(name -> {
                        ConnectionProfile p = profileManager.getConnection(name);
                        if (p != null) listModel.addElement(p);
                    });
                    updateConnectionCombo();
                }
            }
        });

        JButton testBtn = new JButton("✓ Test");
        styleButton(testBtn);
        testBtn.setPreferredSize(new Dimension(80, 28));
        testBtn.addActionListener(ev -> {
            ConnectionProfile selected = profileList.getSelectedValue();
            if (selected == null) {
                statusLabel.setText("No connection selected.");
                return;
            }
            statusLabel.setText("Testing...");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    StorageService testService = createStorageService(selected);
                    boolean ok = testService.connect();
                    testService.disconnect();
                    return ok ? "success" : "fail";
                }

                @Override
                protected void done() {
                    try {
                        if ("success".equals(get())) {
                            statusLabel.setText("✓ Success");
                        } else {
                            statusLabel.setText("✗ Failed");
                        }
                    } catch (Exception e) {
                        statusLabel.setText("✗ Error");
                    }
                }
            }.execute();
        });

        JButton removeBtn = new JButton("Remove");
        styleButton(removeBtn);
        removeBtn.setPreferredSize(new Dimension(80, 28));
        removeBtn.addActionListener(ev -> {
            ConnectionProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                log("SECURITY: Deletion attempt for connection: " + selected.getName());
                int confirm = JOptionPane.showConfirmDialog(dialog, "Delete connection: " + selected.getName() + "?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    profileManager.deleteProfile(selected.getName());
                    listModel.removeElement(selected);
                    updateConnectionCombo();
                    log("SECURITY: Connection deleted: " + selected.getName());
                }
            }
        });

        JButton okBtn = new JButton("OK");
        styleButton(okBtn);
        okBtn.setPreferredSize(new Dimension(80, 28));
        okBtn.addActionListener(ev -> {
            updateConnectionCombo();
            dialog.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        styleButton(cancelBtn);
        cancelBtn.setPreferredSize(new Dimension(80, 28));
        cancelBtn.addActionListener(ev -> dialog.dispose());

        JButton closeBtn = new JButton("Close");
        styleButton(closeBtn);
        closeBtn.setPreferredSize(new Dimension(80, 28));
        closeBtn.addActionListener(ev -> dialog.dispose());

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        topRow.add(addBtn);
        topRow.add(editBtn);
        topRow.add(testBtn);
        topRow.add(removeBtn);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        bottomRow.add(okBtn);
        bottomRow.add(cancelBtn);
        bottomRow.add(closeBtn);

        btnPanel.add(topRow);
        btnPanel.add(Box.createVerticalStrut(10));
        btnPanel.add(bottomRow);

        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.getRootPane().setDefaultButton(closeBtn);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("ESCAPE"), "closeDialog"
        );
        dialog.getRootPane().getActionMap().put("closeDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
    }

    private ConnectionProfile showConnectionEditor(ConnectionProfile existing) {
        JTextField name = new JTextField(existing != null ? existing.getName() : "");
        JTextField accessKey = new JTextField(existing != null ? existing.getAccessKey() : "");
        JTextField secretKey = new JTextField(existing != null ? existing.getSecretKey() : "");
        JTextField endpoint = new JTextField(existing != null ? existing.getEndpoint() : "https://s3.amazonaws.com");
        JTextField region = new JTextField(existing != null ? existing.getRegion() : "us-east-1");

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.add(new JLabel("Name:"));
        panel.add(name);
        panel.add(new JLabel("Access Key:"));
        panel.add(accessKey);
        panel.add(new JLabel("Secret Key:"));
        panel.add(secretKey);
        panel.add(new JLabel("Endpoint:"));
        panel.add(endpoint);
        panel.add(new JLabel("Region:"));
        panel.add(region);

        int result = JOptionPane.showConfirmDialog(this, panel,
                existing == null ? "Add Connection" : "Edit Connection",
                JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            return new ConnectionProfile(name.getText(), accessKey.getText(), secretKey.getText(),
                    endpoint.getText(), region.getText(), "AWS_S3");
        }
        return null;
    }

    private void updateConnectionCombo() {
        if (profileManager == null || connectionCombo == null) {
            System.err.println("Profile manager or connection combo is not initialized");
            return;
        }
        String current = (String) connectionCombo.getSelectedItem();
        connectionCombo.removeAllItems();
        try {
            List<String> connections = profileManager.listConnections();
            if (connections != null) {
                connections.forEach(connectionCombo::addItem);
            }
        } catch (Exception e) {
            System.err.println("Error loading connections: " + e.getMessage());
            log("Error loading connection profiles: " + e.getMessage());
        }
        if (current != null) {
            connectionCombo.setSelectedItem(current);
        }
    }

    private void loadProfiles() {
        if (profileManager == null) {
            System.err.println("Profile manager is not initialized");
            return;
        }
        try {
            updateConnectionCombo();
            log("Connection profiles loaded successfully");
        } catch (Exception e) {
            System.err.println("Error loading profiles: " + e.getMessage());
            log("Error loading connection profiles: " + e.getMessage());
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            } else {
                System.out.println(message);
            }
        });
    }

    private static class ProfileListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ConnectionProfile) {
                ConnectionProfile p = (ConnectionProfile) value;
                setText(p.getName());
                setToolTipText(p.getEndpoint());
            }
            return this;
        }
    }
}