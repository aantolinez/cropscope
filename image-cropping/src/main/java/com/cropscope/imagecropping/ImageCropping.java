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

package com.cropscope.cloudbackuptool;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.cropscope.cloudstorage.model.ConnectionProfile;
import com.cropscope.cloudstorage.service.ConnectionProfileManager;
import com.cropscope.cloudstorage.model.StorageObjectSummary;
import com.cropscope.cloudstorage.service.StorageService;
import com.cropscope.cloudstorage.service.S3Service;

public class ImageCropping extends JFrame {
    private volatile boolean exportScheduled = false;

    private final Performance performance = new Performance();
    private boolean rulersEnabled = true;
    private ImageRulerPanel rulerPanel;

    private final ImageCroppingCore core;

    private boolean hierSecondary = false, hierTertiary = false;

    public void setHierarchyOptions(boolean enableSecondary, boolean enableTertiary) {
        this.hierSecondary = enableSecondary;
        this.hierTertiary = enableTertiary;
    }

    private enum CroppingMode {IMMEDIATE, DESIGNATION}

    private CroppingMode mode = CroppingMode.IMMEDIATE;

    private final CloudStorageManager cloudStorageManager;

    private boolean sourceUseCloud = false;
    private String sourceConnectionName = "";
    private String sourceBucket = "";
    private String sourceScope = "";

    private CloudImageSource sourceMirror;

    private final LocalSaveWorker localSaver;

    private final ExecutorService preloadExec = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ImagePreloader");
            t.setDaemon(true);
            return t;
        }
    });

    private final String sessionId = UUID.randomUUID().toString();
    private int exportSequence = 0;
    private String sessionExportStamp = null;
    private File sessionExportDir = null;

    private final MetadataExporter metadataExporter;
    private final AutoExportManager autoExportManager;

    private boolean exportCocoEnabled = false;

    public ImageCropping(File sourceFolder, File saveFolder, int cropWidth, int cropHeight) {
        this.core = new ImageCroppingCore(this, sourceFolder, saveFolder, cropWidth, cropHeight);
        this.metadataExporter = new MetadataExporter();
        this.autoExportManager = new AutoExportManager(new Runnable() {
            public void run() {
                core.exportMetadataJson();
            }
        });

        core.initializeUI();
        core.getCbAutoExport().addActionListener(e -> handleAutoExportToggle());
        core.getCbAutoExport().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                handleAutoExportToggle();
            }
        });
        this.cloudStorageManager = new CloudStorageManager(this);
        this.localSaver = new LocalSaveWorker(core, new Runnable() {
            public void run() {
                maybeAutoExport();
            }
        });
        this.localSaver.start();
        setTitle("Image Cropping");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestExit();
            }
        });
        rulerPanel = new ImageRulerPanel(core);
        add(new JScrollPane(rulerPanel), BorderLayout.CENTER);
        add(core.getBottomPanel(), BorderLayout.SOUTH);
        add(core.getStatusLabel(), BorderLayout.NORTH);
        installKeyBindings();
        core.loadCurrentSubfolder();
        core.showNextImage();

        setSize(980, 720);
        setMinimumSize(new Dimension(820, 600));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void setCloudStorageSettings(boolean useCloud, String bucketName, String connectionName) {
        cloudStorageManager.setCloudStorageSettings(useCloud, bucketName, connectionName);
    }

    public void setSourceCloudSettings(boolean useCloud, String connectionName, String bucket, String scope) {
        this.sourceUseCloud = useCloud;
        this.sourceConnectionName = (connectionName == null ? "" : connectionName);
        this.sourceBucket = (bucket == null ? "" : bucket);
        this.sourceScope = (scope == null ? "" : scope);
    }

    public void setSourceMirror(CloudImageSource cis) {
        this.sourceMirror = cis;
    }

    private void debugLog(String message) {
        if (core.isDebugEnabled()) {
            System.out.println("[DEBUG] " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " - " + message);
        }
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem miExport = new JMenuItem("Export Metadata");
        miExport.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                core.exportMetadataJson();
            }
        });

        JMenu modes = new JMenu("Cropping Modes");
        JRadioButtonMenuItem miImmediate = new JRadioButtonMenuItem("Immediate Cropping", true);
        JRadioButtonMenuItem miDesignation = new JRadioButtonMenuItem("Designation Cropping", false);
        ButtonGroup bg = new ButtonGroup();
        bg.add(miImmediate);
        bg.add(miDesignation);
        miImmediate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setCroppingMode(CroppingMode.IMMEDIATE);
            }
        });
        miDesignation.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setCroppingMode(CroppingMode.DESIGNATION);
            }
        });
        modes.add(miImmediate);
        modes.add(miDesignation);

        JMenuItem miExit = new JMenuItem("Exit");
        miExit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                requestExit();
            }
        });

        file.add(miExport);
        file.addSeparator();
        file.add(modes);
        file.addSeparator();
        file.add(miExit);

        JMenu settings = new JMenu("Settings");
        JMenuItem miSettings = new JMenuItem("Project/User…");
        miSettings.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                core.showSettingsDialog();
            }
        });

        JCheckBoxMenuItem miRulers = new JCheckBoxMenuItem("Show Rulers", rulersEnabled);
        miRulers.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                toggleRulers();
                miRulers.setSelected(rulersEnabled);
            }
        });

        JCheckBoxMenuItem miDebug = new JCheckBoxMenuItem("Enable Debug Logging", core.isDebugEnabled());
        miDebug.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                core.setDebugEnabled(miDebug.isSelected());
                cloudStorageManager.setDebugEnabled(miDebug.isSelected());
                debugLog("Debug logging " + (miDebug.isSelected() ? "enabled" : "disabled"));
            }
        });
        JCheckBoxMenuItem miExportCoco = new JCheckBoxMenuItem("Also export COCO (instances)", exportCocoEnabled);
        miExportCoco.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportCocoEnabled = miExportCoco.isSelected();
                setStatus(exportCocoEnabled ? "COCO export enabled" : "COCO export disabled", 1500);
            }
        });

        JMenu performanceMenu = new JMenu("Performance");
        JMenuItem miStartPerf = new JMenuItem("Start Performance Session");
        JMenuItem miStopPerf = new JMenuItem("Stop Performance Session");
        miStartPerf.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                performance.startSession();
                setStatus("Performance session started", 2000);
            }
        });
        miStopPerf.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (performance.isSessionActive()) {
                    performance.stopSession();
                    showMetricsDialog();
                } else {
                    setStatus("No active performance session", 2000);
                }
            }
        });

        settings.add(miSettings);
        settings.addSeparator();
        settings.add(miRulers);
        settings.addSeparator();
        settings.add(miDebug);
        settings.addSeparator();
        settings.add(miExportCoco);

        performanceMenu.add(miStartPerf);
        performanceMenu.add(miStopPerf);

        mb.add(file);
        mb.add(settings);
        mb.add(performanceMenu);
        return mb;
    }

    private void setCroppingMode(CroppingMode newMode) {
        this.mode = newMode;
        core.setCroppingMode(newMode == CroppingMode.IMMEDIATE);
    }

    public void showNextFolder() {
        core.showNextFolder();
    }

    public void showPrevFolder() {
        core.showPrevFolder();
    }

    public void showNextImage() {
        core.showNextImage();
    }

    public void showPrevImage() {
        core.showPrevImage();
    }

    public void handleCropClick(Point mousePos) {
        final boolean designationMode = (mode == CroppingMode.DESIGNATION);
        if (!hierSecondary && !hierTertiary) {
            ImageCroppingCore.CropResult result = core.performCrop(mousePos);
            if (result == null) return;
            result.metadata.designated = designationMode;
            result.metadata.isPrimary = true;
            enqueueSaveOrDesignation(result, designationMode);
            return;
        }
        CropEngine.HierarchicalCropResult batch = core.performHierarchicalCrop(
                mousePos,
                hierSecondary,
                hierTertiary, !designationMode
        );
        if (batch == null) return;
        batch.primary.metadata.designated = designationMode;
        batch.primary.metadata.isPrimary = true;
        enqueueSaveOrDesignation(batch.primary, designationMode);
        if (!designationMode) {
            for (ImageCroppingCore.CropResult r : batch.secondary) {
                r.metadata.designated = false;
                enqueueSaveOrDesignation(r, false);
            }
            for (ImageCroppingCore.CropResult r : batch.tertiary) {
                r.metadata.designated = false;
                enqueueSaveOrDesignation(r, false);
            }
        }

        int extra = designationMode ? 0 : (batch.secondary.size() + batch.tertiary.size());
        setStatus("✅ " + batch.primary.filename + (extra > 0 ? (" +" + extra + " sub-crops") : ""), 1200);
    }

    private void enqueueSaveOrDesignation(ImageCroppingCore.CropResult result, boolean designation) {
        performance.incrementCropCount();
        if (designation) {
            core.addMetadataToQueue(result.metadata);
            core.setStatus("📝 Designated: " + result.filename, 1000);
            maybeAutoExport();
        } else {
            try {
                performance.incrementCropCountForSize(result.metadata.cropWidth, result.metadata.cropHeight);

                if (cloudStorageManager.isUseCloudStorage()) {
                    debugLog("Adding to cloud save queue: " + result.filename);
                    cloudStorageManager.queueCloudSave(
                            result.croppedImage, result.filename, result.metadata,
                            core.getSavePrefix(),
                            result.metadata.cropWidth + "x" + result.metadata.cropHeight
                    );
                    core.setStatus("☁️ Queued for cloud: " + result.filename, 1000);
                } else {
                    debugLog("Adding to local save queue: " + result.filename);
                    localSaver.enqueue(result.croppedImage, result.filename, result.metadata);
                    core.setStatus("💾 Queued: " + result.filename, 1000);
                }
            } catch (Exception ex) {
                debugLog("Error adding to save queue: " + ex.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    public void maybeAutoExport() {
        if (!autoExportManager.isEnabled()) return;
        int primaryCount = 0;
        List<ImageCroppingCore.CropMetadata> q = core.getCropMetadataQueue();
        synchronized (q) {
            for (int i = 0; i < q.size(); i++) {
                ImageCroppingCore.CropMetadata m = q.get(i);
                if (m != null && m.isPrimary) primaryCount++;
            }
        }

        if (primaryCount >= autoExportManager.getThreshold()) {
            if (exportScheduled) return;
            exportScheduled = true;
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    try {
                        exportMetadataJson();
                    } finally {
                        exportScheduled = false;
                    }
                }
            });
        }
    }

    public void exportMetadataJson() {
        final List<ImageCroppingCore.CropMetadata> snapshot;
        synchronized (core.getCropMetadataQueue()) {
            if (core.getCropMetadataQueue().isEmpty()) {
                JOptionPane.showMessageDialog(this, "No crop metadata to export yet.",
                        "Nothing to Export", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            snapshot = new ArrayList<ImageCroppingCore.CropMetadata>(core.getCropMetadataQueue());
        }
        final int primaryW = core.getCropWidth();
        final int primaryH = core.getCropHeight();
        final int secondaryW = Math.max(1, primaryW / 2), secondaryH = Math.max(1, primaryH / 2);
        final int tertiaryW = Math.max(1, primaryW / 4), tertiaryH = Math.max(1, primaryH / 4);
        int countPrimary = 0, countSecondary = 0, countTertiary = 0;
        for (int i = 0; i < snapshot.size(); i++) {
            ImageCroppingCore.CropMetadata m = snapshot.get(i);
            if (m.isPrimary || (m.cropWidth == primaryW && m.cropHeight == primaryH)) {
                countPrimary++;
            } else if (m.cropWidth == secondaryW && m.cropHeight == secondaryH) {
                countSecondary++;
            } else if (m.cropWidth == tertiaryW && m.cropHeight == tertiaryH) {
                countTertiary++;
            }
        }
        final int total = snapshot.size();

        String exportStamp = ExportUtils.nowUtcStamp();
        if (sessionExportStamp == null) sessionExportStamp = exportStamp;

        if (cloudStorageManager.isUseCloudStorage()) {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            String jsonFilename = "crop_metadata_" + exportStamp + ".json";
            File tempFile = new File(tempDir, jsonFilename);
            try {
                JSONObject root = metadataExporter.buildExportJson(
                        snapshot,
                        ExportUtils.stampToIso(exportStamp),
                        ExportUtils.stampToIso(sessionExportStamp),
                        core.getProjectName(),
                        core.getUserName(),
                        core.getSourceRootDir().getAbsolutePath(),
                        core.getSaveDirectory().getAbsolutePath(),
                        primaryW,
                        primaryH
                );
                metadataExporter.writeJsonToFile(root, tempFile);
                String cloudKey = "Crop_Metadata_" + sessionExportStamp + "/" + jsonFilename;
                cloudStorageManager.queueCloudMetadataUpload(tempFile, cloudKey);
                if (exportCocoEnabled) {
                    try {
                        CocoExporter coco = new CocoExporter();
                        JSONObject cocoRoot = coco.buildCocoInstances(
                                snapshot,
                                core.getProjectName(),
                                core.getUserName(),
                                ExportUtils.stampToIso(exportStamp)
                        );
                        String cocoFilename = "Coco_instances_" + exportStamp + ".json";
                        File cocoTemp = new File(tempDir, cocoFilename);
                        metadataExporter.writeJsonToFile(cocoRoot, cocoTemp);
                        String cocoKey = "Crop_Metadata_" + sessionExportStamp + "/" + cocoFilename;
                        cloudStorageManager.queueCloudMetadataUpload(cocoTemp, cocoKey);
                    } catch (Throwable t) {
                        setStatus("COCO export (cloud) failed: " + t.getMessage(), 3000);
                    }
                }

                setStatus("📦 Metadata queued for cloud export: " + cloudKey, 2500);

                String alsoCoco = exportCocoEnabled ? ("\nCOCO: Coco_instances_" + exportStamp + ".json") : "";
                JOptionPane.showMessageDialog(this,
                        "Export complete to cloud:\n" + cloudKey +
                                "\n\nCrops exported:" +
                                "\n• Primary (" + primaryW + "x" + primaryH + "): " + countPrimary +
                                "\n• Secondary (" + secondaryW + "x" + secondaryH + "): " + countSecondary +
                                "\n• Tertiary (" + tertiaryW + "x" + tertiaryH + "): " + countTertiary +
                                "\n\nTotal: " + total + alsoCoco,
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);

                core.clearCropMetadataQueue();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            if (sessionExportDir == null) {
                sessionExportDir = new File(core.getSaveDirectory(), "Crop_Metadata_" + sessionExportStamp);
                if (!sessionExportDir.exists()) sessionExportDir.mkdirs();
            }
            String jsonFilename = "crop_metadata_" + exportStamp + ".json";
            File out = new File(sessionExportDir, jsonFilename);
            try {
                JSONObject root = metadataExporter.buildExportJson(
                        snapshot,
                        ExportUtils.stampToIso(exportStamp),
                        ExportUtils.stampToIso(sessionExportStamp),
                        core.getProjectName(),
                        core.getUserName(),
                        core.getSourceRootDir().getAbsolutePath(),
                        core.getSaveDirectory().getAbsolutePath(),
                        primaryW,
                        primaryH
                );
                metadataExporter.writeJsonToFile(root, out);
                if (exportCocoEnabled) {
                    try {
                        CocoExporter coco = new CocoExporter();
                        JSONObject cocoRoot = coco.buildCocoInstances(
                                snapshot,
                                core.getProjectName(),
                                core.getUserName(),
                                ExportUtils.stampToIso(exportStamp)
                        );
                        String cocoFilename = "Coco_instances_" + exportStamp + ".json";
                        File cocoOut = new File(sessionExportDir, cocoFilename);
                        metadataExporter.writeJsonToFile(cocoRoot, cocoOut);
                    } catch (Throwable t) {
                        setStatus("COCO export failed: " + t.getMessage(), 3000);
                    }
                }

                setStatus("📦 Metadata exported: " + out.getName(), 2500);

                String alsoCoco = exportCocoEnabled ? ("\nCOCO: Coco_instances_" + exportStamp + ".json") : "";
                JOptionPane.showMessageDialog(this,
                        "Export complete:\n" + out.getAbsolutePath() +
                                "\n\nCrops exported:" +
                                "\n• Primary (" + primaryW + "x" + primaryH + "): " + countPrimary +
                                "\n• Secondary (" + secondaryW + "x" + secondaryH + "): " + countSecondary +
                                "\n• Tertiary (" + tertiaryW + "x" + tertiaryH + "): " + countTertiary +
                                "\n\nTotal: " + total + alsoCoco,
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);

                core.clearCropMetadataQueue();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void requestExit() {
        if (confirmExportIfPending()) {
            debugLog("Shutting down application...");
            localSaver.stop();

            cloudStorageManager.shutdown();
            preloadExec.shutdownNow();
            if (sourceMirror != null) {
                try {
                    sourceMirror.shutdown();
                } catch (Throwable ignore) {
                }
            }
            System.exit(0);
        }
    }

    private boolean confirmExportIfPending() {
        int pending = core.getCropMetadataQueue().size();
        if (pending <= 0) return true;
        Object[] options = {"Export & Exit", "Exit without Export", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "There are " + pending + " crop metadata items pending export.\n" +
                        "Do you want to export them before exiting?",
                "Pending metadata", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            exportMetadataJson();
            return true;
        }
        if (choice == 1) {
            return true;
        }
        return false;
    }

    private void installKeyBindings() {
        JComponent c = core.getImagePanel();
        InputMap im = c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = c.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prevImage");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "nextImage");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "prevFolder");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "nextFolder");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "firstImage");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "lastImage");
        am.put("prevImage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                core.showPrevImage();
            }
        });
        am.put("nextImage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                core.showNextImage();
            }
        });
        am.put("prevFolder", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                core.showPrevFolder();
            }
        });
        am.put("nextFolder", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                core.showNextFolder();
            }
        });
        am.put("firstImage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                core.jumpToImage(0);
            }
        });
        am.put("lastImage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (core.getImageFiles() != null && !core.getImageFiles().isEmpty())
                    core.jumpToImage(core.getImageFiles().size() - 1);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final JFrame mainFrame = new JFrame("Image Crop Tool");
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainFrame.setSize(540, 380);
                mainFrame.setLocationRelativeTo(null);

                JMenuBar menuBar = new JMenuBar();
                JMenu fileMenu = new JMenu("File");
                JMenuItem cropImagesItem = new JMenuItem("Crop Images");
                JMenuItem exitItem = new JMenuItem("Exit");
                fileMenu.add(cropImagesItem);
                fileMenu.addSeparator();
                fileMenu.add(exitItem);
                menuBar.add(fileMenu);
                mainFrame.setJMenuBar(menuBar);

                JPanel contentPanel = new JPanel(new BorderLayout());
                contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                JLabel titleLabel = new JLabel("Welcome to Image Crop Tool", SwingConstants.CENTER);
                titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
                contentPanel.add(titleLabel, BorderLayout.NORTH);

                JPanel instructionPanel = new JPanel(new GridLayout(2, 1, 0, 10));
                instructionPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
                JLabel instruction1 = new JLabel("Use File > Crop Images to start", SwingConstants.CENTER);
                instruction1.setFont(new Font("SansSerif", Font.PLAIN, 14));
                JLabel instruction2 = new JLabel("Select source & destination folders and crop dimensions", SwingConstants.CENTER);
                instruction2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                instruction2.setForeground(Color.GRAY);
                instructionPanel.add(instruction1);
                instructionPanel.add(instruction2);
                contentPanel.add(instructionPanel, BorderLayout.CENTER);

                JLabel iconLabel = new JLabel("🖼️", SwingConstants.CENTER);
                iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 60));
                contentPanel.add(iconLabel, BorderLayout.SOUTH);

                mainFrame.add(contentPanel);

                cropImagesItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt1) {
                        final JDialog dialog = new JDialog(mainFrame, "Crop Settings", true);
                        dialog.setLayout(new BorderLayout(10, 10));
                        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
                        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

                        JLabel title = new JLabel("Configure Crop Settings", SwingConstants.CENTER);
                        title.setFont(new Font("SansSerif", Font.BOLD, 16));
                        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

                        JPanel grid = new JPanel(new GridBagLayout());
                        grid.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
                        GridBagConstraints gbc = new GridBagConstraints();
                        gbc.insets = new Insets(8, 8, 8, 8);
                        gbc.anchor = GridBagConstraints.WEST;
                        gbc.fill = GridBagConstraints.HORIZONTAL;
                        gbc.gridx = 0;
                        gbc.gridy = 0;
                        gbc.weightx = 0;
                        grid.add(new JLabel("Source directory:"), gbc);
                        final JTextField tfSourceDir = new JTextField(System.getProperty("user.home"), 24);
                        gbc.gridx = 1;
                        gbc.weightx = 1.0;
                        grid.add(tfSourceDir, gbc);
                        JButton btnSourceDir = new JButton("...");
                        btnSourceDir.setPreferredSize(new Dimension(30, 24));
                        gbc.gridx = 2;
                        gbc.weightx = 0;
                        grid.add(btnSourceDir, gbc);
                        gbc.gridx = 0;
                        gbc.gridy = 1;
                        gbc.weightx = 0;
                        final JCheckBox cbUseCloudSrc = new JCheckBox("Use cloud storage (source)", false);
                        grid.add(cbUseCloudSrc, gbc);

                        final ConnectionProfileManager srcConnManager = new ConnectionProfileManager();
                        final List<String> srcConnections = srcConnManager.listConnections();
                        final JComboBox<String> cbSrcConnections = new JComboBox<String>(srcConnections.toArray(new String[0]));
                        cbSrcConnections.setEnabled(false);

                        gbc.gridx = 1;
                        gbc.gridy = 1;
                        gbc.weightx = 1.0;
                        JPanel srcCloudPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                        srcCloudPanel.add(new JLabel("Cloud connection:"));
                        srcCloudPanel.add(cbSrcConnections);
                        grid.add(srcCloudPanel, gbc);
                        gbc.gridx = 0;
                        gbc.gridy = 2;
                        gbc.weightx = 0;
                        grid.add(new JLabel("Source bucket:"), gbc);
                        final JComboBox<String> cbSrcBucket = new JComboBox<String>();
                        cbSrcBucket.setEnabled(false);
                        cbSrcBucket.setPreferredSize(new Dimension(200, 24));
                        gbc.gridx = 1;
                        gbc.weightx = 1.0;
                        grid.add(cbSrcBucket, gbc);
                        gbc.gridx = 0;
                        gbc.gridy = 3;
                        gbc.weightx = 0;
                        grid.add(new JLabel("Source scope:"), gbc);
                        final JComboBox<String> cbSrcScope = new JComboBox<String>();
                        cbSrcScope.setEnabled(false);
                        cbSrcScope.setToolTipText("Select {bucket}/, {bucket}/{prefix}/ or {bucket}/{prefix}/{res}/");
                        gbc.gridx = 1;
                        gbc.weightx = 1.0;
                        grid.add(cbSrcScope, gbc);
                        gbc.gridx = 0;
                        gbc.gridy = 4;
                        gbc.weightx = 0;
                        grid.add(new JLabel("Sink directory:"), gbc);
                        final JTextField tfSinkDir = new JTextField(System.getProperty("user.home") + "/crops", 24);
                        gbc.gridx = 1;
                        gbc.weightx = 1.0;
                        grid.add(tfSinkDir, gbc);
                        JButton btnSinkDir = new JButton("...");
                        btnSinkDir.setPreferredSize(new Dimension(30, 24));
                        gbc.gridx = 2;
                        gbc.weightx = 0;
                        grid.add(btnSinkDir, gbc);
                        gbc.gridx = 0;
                        gbc.gridy = 5;
                        gbc.weightx = 0;
                        grid.add(new JLabel("Sink bucket:"), gbc);
                        final JComboBox<String> cbSinkBucket = new JComboBox<String>();
                        cbSinkBucket.setEnabled(false);
                        cbSinkBucket.setPreferredSize(new Dimension(200, 24));
                        gbc.gridx = 1;
                        gbc.weightx = 1.0;
                        grid.add(cbSinkBucket, gbc);
                        gbc.gridx = 0;
                        gbc.gridy = 6;
                        gbc.weightx = 0;
                        final JCheckBox cbUseCloud = new JCheckBox("Use cloud storage", false);
                        grid.add(cbUseCloud, gbc);

                        final ConnectionProfileManager connManager = new ConnectionProfileManager();
                        final List<String> connections = connManager.listConnections();
                        final JComboBox<String> cbCloudConnections = new JComboBox<String>(connections.toArray(new String[0]));
                        cbCloudConnections.setEnabled(false);

                        gbc.gridx = 1;
                        gbc.gridy = 6;
                        gbc.weightx = 1.0;
                        JPanel cloudPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                        cloudPanel.add(new JLabel("Cloud connection:"));
                        cloudPanel.add(cbCloudConnections);
                        grid.add(cloudPanel, gbc);
                        cbSinkBucket.setEnabled(false);
                        cbUseCloud.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent evt2) {
                                boolean selected = cbUseCloud.isSelected();
                                cbSinkBucket.setEnabled(selected);
                                cbCloudConnections.setEnabled(selected);
                                if (!selected) cbSinkBucket.removeAllItems();
                            }
                        });
                        cbCloudConnections.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent evt3) {
                                if (cbUseCloud.isSelected()) {
                                    String selectedConnection = (String) cbCloudConnections.getSelectedItem();
                                    if (selectedConnection != null && selectedConnection.length() > 0) {
                                        CloudStorageManager tempManager = new CloudStorageManager(null);
                                        List<String> buckets = tempManager.listBuckets(selectedConnection);
                                        cbSinkBucket.removeAllItems();
                                        if (buckets != null && !buckets.isEmpty()) {
                                            for (int i = 0; i < buckets.size(); i++)
                                                cbSinkBucket.addItem(buckets.get(i));
                                            cbSinkBucket.setSelectedIndex(0);
                                        } else {
                                            cbSinkBucket.addItem("No buckets found");
                                            cbSinkBucket.setEnabled(false);
                                        }
                                    }
                                }
                            }
                        });
                        cbUseCloudSrc.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                boolean selected = cbUseCloudSrc.isSelected();
                                cbSrcConnections.setEnabled(selected);
                                cbSrcBucket.setEnabled(selected);
                                cbSrcScope.setEnabled(selected);
                                if (!selected) {
                                    cbSrcBucket.removeAllItems();
                                    cbSrcScope.removeAllItems();
                                }
                            }
                        });

                        cbSrcConnections.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                if (!cbUseCloudSrc.isSelected()) return;
                                String conn = (String) cbSrcConnections.getSelectedItem();
                                if (conn == null || conn.trim().isEmpty()) return;
                                cbSrcBucket.removeAllItems();
                                cbSrcScope.removeAllItems();
                                CloudStorageManager temp = new CloudStorageManager(null);
                                List<String> buckets = temp.listBuckets(conn);
                                if (buckets != null && !buckets.isEmpty()) {
                                    for (String b : buckets) cbSrcBucket.addItem(b);
                                    cbSrcBucket.setSelectedIndex(0);
                                    cbSrcBucket.setEnabled(true);
                                } else {
                                    cbSrcBucket.addItem("No buckets found");
                                    cbSrcBucket.setEnabled(false);
                                }
                            }
                        });

                        cbSrcBucket.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                if (!cbUseCloudSrc.isSelected()) return;
                                String conn = (String) cbSrcConnections.getSelectedItem();
                                String bucket = (String) cbSrcBucket.getSelectedItem();
                                if (conn == null || bucket == null ||
                                        conn.trim().isEmpty() || bucket.trim().isEmpty() ||
                                        "No buckets found".equals(bucket)) return;

                                cbSrcScope.removeAllItems();
                                List<StorageObjectSummary> objs = listObjectsForConnection(conn, bucket);
                                List<String> scopes = buildScopeOptions(bucket, objs);
                                if (scopes.isEmpty()) {
                                    cbSrcScope.addItem(bucket + "/");
                                } else {
                                    for (String s : scopes) cbSrcScope.addItem(s);
                                    cbSrcScope.setSelectedIndex(0);
                                }
                                cbSrcScope.setEnabled(true);
                            }
                        });
                        gbc.gridx = 0;
                        gbc.gridy = 7;
                        gbc.weightx = 0;
                        grid.add(new JLabel("Crop dimensions:"), gbc);
                        JPanel dimPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                        final JTextField tfCropWidth = new JTextField("512", 6);
                        final JTextField tfCropHeight = new JTextField("512", 6);
                        dimPanel.add(tfCropWidth);
                        dimPanel.add(new JLabel("x"));
                        dimPanel.add(tfCropHeight);
                        dimPanel.add(new JLabel("pixels"));
                        gbc.gridx = 1;
                        gbc.weightx = 1.0;
                        grid.add(dimPanel, gbc);
                        final JCheckBox cbSecondary = new JCheckBox();
                        final JCheckBox cbTertiary = new JCheckBox();

                        final Runnable updateLabels = new Runnable() {
                            public void run() {
                                try {
                                    int w = Integer.parseInt(tfCropWidth.getText());
                                    int h = Integer.parseInt(tfCropHeight.getText());
                                    int w2 = Math.max(1, w / 2), h2 = Math.max(1, h / 2);
                                    int w4 = Math.max(1, w / 4), h4 = Math.max(1, h / 4);
                                    cbSecondary.setText(" " + h2 + " x " + w2 + " secondary crop");
                                    cbTertiary.setText(" " + h4 + " x " + w4 + " tertiary crop");
                                } catch (Throwable ex) {
                                    cbSecondary.setText(" secondary crop");
                                    cbTertiary.setText(" tertiary crop");
                                }
                            }
                        };
                        updateLabels.run();

                        tfCropWidth.getDocument().addDocumentListener(new DocumentListener() {
                            public void insertUpdate(DocumentEvent e) {
                                updateLabels.run();
                            }

                            public void removeUpdate(DocumentEvent e) {
                                updateLabels.run();
                            }

                            public void changedUpdate(DocumentEvent e) {
                                updateLabels.run();
                            }
                        });
                        tfCropHeight.getDocument().addDocumentListener(new DocumentListener() {
                            public void insertUpdate(DocumentEvent e) {
                                updateLabels.run();
                            }

                            public void removeUpdate(DocumentEvent e) {
                                updateLabels.run();
                            }

                            public void changedUpdate(DocumentEvent e) {
                                updateLabels.run();
                            }
                        });

                        gbc.gridx = 1;
                        gbc.gridy = 8;
                        gbc.weightx = 1.0;
                        grid.add(cbSecondary, gbc);
                        gbc.gridx = 1;
                        gbc.gridy = 9;
                        gbc.weightx = 1.0;
                        grid.add(cbTertiary, gbc);
                        btnSourceDir.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent evt4) {
                                JFileChooser chooser = new JFileChooser(tfSourceDir.getText());
                                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                                if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                                    tfSourceDir.setText(chooser.getSelectedFile().getAbsolutePath());
                                }
                            }
                        });
                        btnSinkDir.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent evt5) {
                                JFileChooser chooser = new JFileChooser(tfSinkDir.getText());
                                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                                if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                                    tfSinkDir.setText(chooser.getSelectedFile().getAbsolutePath());
                                }
                            }
                        });
                        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                        JButton acceptButton = new JButton("Accept");
                        JButton cancelButton = new JButton("Cancel");
                        buttonPanel.add(acceptButton);
                        buttonPanel.add(cancelButton);

                        acceptButton.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent evt6) {
                                try {
                                    File sourceDir = new File(tfSourceDir.getText());
                                    File sinkDir = new File(tfSinkDir.getText());
                                    int cropWidth = Integer.parseInt(tfCropWidth.getText());
                                    int cropHeight = Integer.parseInt(tfCropHeight.getText());
                                    if (!cbUseCloudSrc.isSelected() && (!sourceDir.exists() || !sourceDir.isDirectory())) {
                                        JOptionPane.showMessageDialog(dialog, "Source directory does not exist or is not a directory", "Error", JOptionPane.ERROR_MESSAGE);
                                        return;
                                    }
                                    if (!cbUseCloud.isSelected() && (!sinkDir.exists() || !sinkDir.isDirectory())) {
                                        JOptionPane.showMessageDialog(dialog, "Sink directory does not exist or is not a directory", "Error", JOptionPane.ERROR_MESSAGE);
                                        return;
                                    }
                                    if (cbUseCloud.isSelected()) {
                                        if (cbSinkBucket.getSelectedItem() == null ||
                                                cbSinkBucket.getSelectedItem().toString().trim().length() == 0 ||
                                                "No buckets found".equals(cbSinkBucket.getSelectedItem().toString())) {
                                            JOptionPane.showMessageDialog(dialog, "Bucket selection is required when using cloud storage", "Error", JOptionPane.ERROR_MESSAGE);
                                            return;
                                        }
                                        if (cbCloudConnections.getSelectedItem() == null ||
                                                cbCloudConnections.getSelectedItem().toString().trim().length() == 0) {
                                            JOptionPane.showMessageDialog(dialog, "Cloud connection selection is required when using cloud storage", "Error", JOptionPane.ERROR_MESSAGE);
                                            return;
                                        }
                                    }
                                    if (cropWidth <= 0 || cropHeight <= 0) {
                                        JOptionPane.showMessageDialog(dialog, "Crop dimensions must be positive integers", "Error", JOptionPane.ERROR_MESSAGE);
                                        return;
                                    }
                                    File effectiveSourceDir = sourceDir;
                                    CloudImageSource cis = null;
                                    String chosenSrcConn = null, chosenSrcBucket = null, chosenSrcScope = null;

                                    if (cbUseCloudSrc.isSelected()) {
                                        chosenSrcConn = (String) cbSrcConnections.getSelectedItem();
                                        chosenSrcBucket = (String) cbSrcBucket.getSelectedItem();
                                        chosenSrcScope = (String) cbSrcScope.getSelectedItem();
                                        if (chosenSrcConn == null || chosenSrcBucket == null || chosenSrcScope == null ||
                                                chosenSrcConn.trim().isEmpty() || chosenSrcBucket.trim().isEmpty() || chosenSrcScope.trim().isEmpty() ||
                                                "No buckets found".equals(chosenSrcBucket)) {
                                            JOptionPane.showMessageDialog(dialog, "Please choose a valid Source connection, bucket and scope.", "Error", JOptionPane.ERROR_MESSAGE);
                                            return;
                                        }
                                        try {
                                            File cacheBase = new File(System.getProperty("java.io.tmpdir"), "cropscope_cloud_cache");
                                            cis = new CloudImageSource(chosenSrcConn, chosenSrcBucket, chosenSrcScope, cacheBase, 20);
                                            cis.connect();
                                            cis.index();

                                            final int HEAD_COUNT = 24;
                                            cis.mirrorHeadBlocking(HEAD_COUNT, null);

                                            effectiveSourceDir = cis.getLocalRoot();
                                            debugDumpDir(effectiveSourceDir, 2);
                                        } catch (Exception ex) {
                                            JOptionPane.showMessageDialog(dialog, "Cloud source mirror failed:\n" + ex.getMessage(),
                                                    "Cloud Source", JOptionPane.ERROR_MESSAGE);
                                            return;
                                        }
                                    }
                                    ImageCropping croppingTool = new ImageCropping(effectiveSourceDir, sinkDir, cropWidth, cropHeight);
                                    croppingTool.setHierarchyOptions(cbSecondary.isSelected(), cbTertiary.isSelected());
                                    if (cbUseCloud.isSelected()) {
                                        String selectedConnection = cbCloudConnections.getSelectedItem().toString();
                                        String selectedBucket = cbSinkBucket.getSelectedItem().toString();
                                        croppingTool.setCloudStorageSettings(true, selectedBucket, selectedConnection);
                                    }
                                    if (cbUseCloudSrc.isSelected()) {
                                        croppingTool.setSourceCloudSettings(true, chosenSrcConn, chosenSrcBucket, chosenSrcScope);
                                        if (cis != null) {
                                            croppingTool.setSourceMirror(cis);
                                            cis.mirrorTailAsync(new CloudImageSource.ProgressListener() {
                                                @Override
                                                public void onFinish(CloudImageSource.MirrorSummary s) {
                                                    SwingUtilities.invokeLater(new Runnable() {
                                                        public void run() {
                                                            croppingTool.setStatus("Cloud mirror: " + s.downloaded + " new, " + s.skipped + " cached", 2500);
                                                        }
                                                    });
                                                }
                                            });
                                        }
                                    }

                                    dialog.dispose();
                                    mainFrame.dispose();
                                } catch (NumberFormatException ex) {
                                    JOptionPane.showMessageDialog(dialog, "Crop dimensions must be valid integers", "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        });
                        cancelButton.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent evt7) {
                                dialog.dispose();
                            }
                        });

                        mainPanel.add(title, BorderLayout.NORTH);
                        mainPanel.add(grid, BorderLayout.CENTER);
                        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
                        dialog.add(mainPanel);
                        dialog.pack();
                        dialog.setLocationRelativeTo(mainFrame);
                        dialog.setVisible(true);
                    }
                });

                exitItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt8) {
                        System.exit(0);
                    }
                });

                mainFrame.setVisible(true);
            }
        });
    }

    private static final Pattern RES_DIR =
            Pattern.compile("\\d{1,5}x\\d{1,5}");

    private static List<String> buildScopeOptions(
            String bucket, List<StorageObjectSummary> objs) {
        LinkedHashSet<String> opts = new LinkedHashSet<String>();
        opts.add(bucket + "/");

        for (StorageObjectSummary s : objs) {
            String key = s.getKey();
            if (key == null || key.isEmpty()) continue;
            String[] parts = key.split("/");
            if (parts.length < 1) continue;

            if (parts.length >= 2) {
                String parent = join("/", Arrays.copyOf(parts, parts.length - 1));
                if (!parent.isEmpty()) {
                    String resCandidate = parts[parts.length - 2];
                    if (RES_DIR.matcher(resCandidate).matches()) {
                        String prefix = (parts.length >= 3)
                                ? join("/", Arrays.copyOf(parts, parts.length - 2))
                                : "";
                        if (!prefix.isEmpty()) {
                            opts.add(bucket + "/" + prefix + "/");
                            opts.add(bucket + "/" + prefix + "/" + resCandidate + "/");
                        } else {
                            opts.add(bucket + "/" + resCandidate + "/");
                        }
                    } else {
                        opts.add(bucket + "/" + parent + "/");
                    }
                }
            }
        }
        return new ArrayList<String>(opts);
    }

    private static List<StorageObjectSummary> listObjectsForConnection(String connectionName, String bucket) {
        ArrayList<StorageObjectSummary> out = new ArrayList<StorageObjectSummary>();
        try {
            ConnectionProfile prof = new ConnectionProfileManager().getConnection(connectionName);
            if (prof == null) return out;
            StorageService svc = new S3Service(prof);
            if (!svc.connect()) return out;
            List<StorageObjectSummary> objs = svc.listObjects(bucket);
            if (objs != null) out.addAll(objs);
            svc.disconnect();
        } catch (Exception ignore) {
        }
        return out;
    }

    private static String join(String sep, String[] parts) {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static Integer promptForThreshold(Component parent, int current) {
        JPanel p = new JPanel(new FlowLayout());
        p.add(new JLabel("Auto-export threshold:"));
        JTextField tf = new JTextField(String.valueOf(current), 8);
        p.add(tf);
        int option = JOptionPane.showConfirmDialog(parent, p, "Set Threshold", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) return null;
        try {
            return Integer.valueOf(Integer.parseInt(tf.getText()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setAutoExportEnabled(boolean enabled) {
        autoExportManager.setEnabled(enabled);
        core.getCbAutoExport().setSelected(enabled);
        setStatus(enabled ? "Auto export enabled" : "Auto export disabled", 1400);
    }

    public void setAutoExportThreshold(int threshold) {
        autoExportManager.setThreshold(threshold);
    }

    public void setAutoExportThreshold(Integer threshold) {
        if (threshold == null) return;
        setAutoExportThreshold(threshold.intValue());
    }


    private static void debugDumpDir(File dir, int levels) {
        try {
            if (dir == null) {
                System.out.println("[DEBUG] null dir");
                return;
            }
            System.out.println("[DEBUG] Dump " + dir.getAbsolutePath() + " (levels=" + levels + ")");
            dump(dir, levels, "");
        } catch (Throwable t) {
            System.out.println("[DEBUG] dump failed: " + t);
        }
    }

    private static void dump(File f, int levels, String indent) {
        if (f == null || levels < 0) return;
        if (!f.exists()) {
            System.out.println(indent + "(missing) " + f.getAbsolutePath());
            return;
        }
        File[] kids = f.listFiles();
        if (kids == null) {
            System.out.println(indent + (f.isDirectory() ? "[DIR]" : "[FILE]") + " " + f.getName());
            return;
        }
        Arrays.sort(kids, Comparator.comparing(File::getName, String::compareToIgnoreCase));
        System.out.println(indent + "[DIR] " + f.getName() + "  (" + kids.length + ")");
        if (levels == 0) return;
        for (int i = 0; i < kids.length; i++) {
            File k = kids[i];
            if (k.isDirectory()) dump(k, levels - 1, indent + "  ");
            else System.out.println(indent + "  " + k.getName() + "  [" + k.length() + " bytes]");
            if (i >= 100) {
                System.out.println(indent + "  ... (more)");
                break;
            }
        }
    }

    public void setStatus(String text, int clearAfterMs) {
        core.setStatus(text, clearAfterMs);
    }

    public void addMetadataToQueue(ImageCroppingCore.CropMetadata meta) {
        core.addMetadataToQueue(meta);
    }

    public void toggleRulers() {
        rulersEnabled = !rulersEnabled;
        if (rulerPanel != null) {
            rulerPanel.setRulersVisible(rulersEnabled);
        }
    }

    private void showMetricsDialog() {
        new MetricsDialog(
                this,
                performance,
                core,
                cloudStorageManager,
                performance.getMetricsReport()
        ).setVisible(true);
    }

    private void handleAutoExportToggle() {
        if (core.getCbAutoExport().isSelected()) {
            Integer v = promptForThreshold(this, autoExportManager.getThreshold());
            if (v == null || v <= 0) {
                core.getCbAutoExport().setSelected(false);
                return;
            }
            autoExportManager.setThreshold(v.intValue());
            autoExportManager.setEnabled(true);
            setStatus("Auto export enabled: every " + v + " crops", 2000);
        } else {
            autoExportManager.setEnabled(false);
            setStatus("Auto export disabled", 1400);
        }
    }

}
