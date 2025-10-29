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
import com.cropscope.dicommanager.threedfeatures.Dicon3DFeatures;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class DicomManagerGui extends JFrame {
    private DicomFileManager dicomFileManager;
    private Dicon3DFeatures dicon3DFeatures;
    private ImageRectangleOverlay imageRectangleOverlay;
    private ImageRulerPanel imageRulerPanel;

    private JButton openButton;
    private JButton prevButton;
    private JButton nextButton;
    private JButton infoButton;
    private JButton exportButton;
    private JButton view3DButton;
    private JButton exportBatchButton;
    private JLabel statusLabel;
    private JTree dicomFileTree;
    private JScrollPane treeScrollPane;
    private JSplitPane splitPane;

    private int currentImageIndex = -1;

    private List<File> batchExportList = new ArrayList<>();

    private JPopupMenu treePopupMenu;
    private JMenuItem addToBatchExportItem;
    private JMenuItem showBatchExportItem;

    public DicomManagerGui() {
        dicomFileManager = new DicomFileManager();
        dicon3DFeatures = new Dicon3DFeatures();

        setTitle("DICOM Image Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLayout(new BorderLayout());
        dicomFileManager.initializeTreeModel();
        createMenuBar();
        imageRulerPanel = new ImageRulerPanel();
        dicomFileTree = new JTree(dicomFileManager.getTreeModel());
        dicomFileTree.setCellRenderer(new DicomTreeCellRenderer());
        dicomFileTree.setRootVisible(false);
        dicomFileTree.setShowsRootHandles(true);
        dicomFileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        treeScrollPane = new JScrollPane(dicomFileTree);
        treeScrollPane.setPreferredSize(new Dimension(300, 0));
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, new JScrollPane(imageRulerPanel));
        splitPane.setDividerLocation(300);
        JPanel buttonPanel = new JPanel();
        openButton = new JButton("Open DICOM Files/Folder");
        prevButton = new JButton("Previous");
        nextButton = new JButton("Next");
        infoButton = new JButton("Show DICOM Info");
        exportButton = new JButton("Export Image");
        view3DButton = new JButton("3D View");
        exportBatchButton = new JButton("Batch Export");

        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        infoButton.setEnabled(false);
        exportButton.setEnabled(false);
        view3DButton.setEnabled(false);
        exportBatchButton.setEnabled(false);

        buttonPanel.add(openButton);
        buttonPanel.add(prevButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(infoButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(view3DButton);
        buttonPanel.add(exportBatchButton);
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("No DICOM files loaded", SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        statusPanel.add(progressBar, BorderLayout.SOUTH);
        add(buttonPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
        openButton.addActionListener(e -> openDicomFiles());
        prevButton.addActionListener(e -> showPreviousImage());
        nextButton.addActionListener(e -> showNextImage());
        infoButton.addActionListener(e -> showDicomInfo());
        exportButton.addActionListener(e -> exportImage());
        view3DButton.addActionListener(e -> show3DView());
        exportBatchButton.addActionListener(e -> showBatchExportDialog());
        dicomFileTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) dicomFileTree.getLastSelectedPathComponent();
                if (selectedNode != null && selectedNode.getUserObject() instanceof DicomFileManager.FileWrapper) {
                    File selectedFile = ((DicomFileManager.FileWrapper) selectedNode.getUserObject()).getFile();
                    List<File> imageFiles = dicomFileManager.getImageFiles();
                    if (imageFiles.contains(selectedFile)) {
                        currentImageIndex = imageFiles.indexOf(selectedFile);
                        loadImage(selectedFile);
                        updateNavigationButtons();
                    }
                }
            }
        });
        createTreeContextMenu();
        dicomFileManager.testDicomPlugins();
        imageRectangleOverlay = new ImageRectangleOverlay(getImageLabelFromRulerPanel(imageRulerPanel));
    }

    private void createTreeContextMenu() {
        treePopupMenu = new JPopupMenu();
        addToBatchExportItem = new JMenuItem("Add to Batch Export");
        addToBatchExportItem.addActionListener(e -> addSelectedFilesToBatchExport());
        treePopupMenu.add(addToBatchExportItem);
        showBatchExportItem = new JMenuItem("Show Batch Export Dialog");
        showBatchExportItem.addActionListener(e -> showBatchExportDialog());
        treePopupMenu.add(showBatchExportItem);
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

                            if (userObject instanceof DicomFileManager.FileWrapper) {
                                DicomFileManager.FileWrapper wrapper = (DicomFileManager.FileWrapper) userObject;
                                File file = wrapper.getFile();
                                if (file.isDirectory()) {
                                    addToBatchExportItem.setText("Add Directory to Batch Export");
                                } else {
                                    addToBatchExportItem.setText("Add File to Batch Export");
                                }
                                treePopupMenu.show(dicomFileTree, e.getX(), e.getY());
                            }
                        }
                    }
                }
            }
        });
    }

    private void addSelectedFilesToBatchExport() {
        TreePath selectedPath = dicomFileTree.getSelectionPath();
        if (selectedPath != null) {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
            if (selectedNode.getUserObject() instanceof DicomFileManager.FileWrapper) {
                DicomFileManager.FileWrapper wrapper = (DicomFileManager.FileWrapper) selectedNode.getUserObject();
                File file = wrapper.getFile();

                int addedCount = 0;

                if (file.isDirectory()) {
                    List<File> dicomFiles = dicomFileManager.findImageFilesInDirectory(file);
                    for (File dicomFile : dicomFiles) {
                        if (!batchExportList.contains(dicomFile)) {
                            batchExportList.add(dicomFile);
                            addedCount++;
                        }
                    }
                } else {
                    if (!batchExportList.contains(file)) {
                        batchExportList.add(file);
                        addedCount = 1;
                    }
                }

                statusLabel.setText("Added " + addedCount + " files to batch export list");
            }
        }
    }

    private JLabel getImageLabelFromRulerPanel(ImageRulerPanel rulerPanel) {
        try {
            Field field = ImageRulerPanel.class.getDeclaredField("imageLabel");
            field.setAccessible(true);
            return (JLabel) field.get(rulerPanel);
        } catch (Exception e) {
            JLabel dummyLabel = new JLabel();
            rulerPanel.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    MouseEvent newEvent = new MouseEvent(
                            dummyLabel, e.getID(), e.getWhen(), e.getModifiersEx(),
                            e.getX(), e.getY(), e.getXOnScreen(), e.getYOnScreen(),
                            e.getClickCount(), e.isPopupTrigger(), e.getButton());
                    dummyLabel.dispatchEvent(newEvent);
                }
            });

            rulerPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    MouseEvent newEvent = new MouseEvent(
                            dummyLabel, e.getID(), e.getWhen(), e.getModifiersEx(),
                            e.getX(), e.getY(), e.getXOnScreen(), e.getYOnScreen(),
                            e.getClickCount(), e.isPopupTrigger(), e.getButton());
                    dummyLabel.dispatchEvent(newEvent);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    MouseEvent newEvent = new MouseEvent(
                            dummyLabel, e.getID(), e.getWhen(), e.getModifiersEx(),
                            e.getX(), e.getY(), e.getXOnScreen(), e.getYOnScreen(),
                            e.getClickCount(), e.isPopupTrigger(), e.getButton());
                    dummyLabel.dispatchEvent(newEvent);
                }
            });

            return dummyLabel;
        }
    }

    private class DicomTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon folderIcon;
        private final Icon fileIcon;
        private final ImageIcon imageIcon;

        public DicomTreeCellRenderer() {
            folderIcon = UIManager.getIcon("Tree.openIcon");
            fileIcon = UIManager.getIcon("Tree.leafIcon");
            imageIcon = createImageIcon();
        }

        private ImageIcon createImageIcon() {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(new Color(0, 0, 255, 100));
            g2d.fillRect(0, 0, 16, 16);
            g2d.setColor(Color.BLUE);
            g2d.drawRect(0, 0, 15, 15);
            g2d.dispose();
            return new ImageIcon(image);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof DicomFileManager.FileWrapper) {
                File file = ((DicomFileManager.FileWrapper) userObject).getFile();
                Map<File, Boolean> fileHasImageMap = dicomFileManager.getFileHasImageMap();

                if (file.isDirectory()) {
                    setIcon(folderIcon);
                    int imageCount = countImageFilesInDirectory(file);
                    setText(file.getName() + " (" + imageCount + " images)");
                } else if (fileHasImageMap.getOrDefault(file, false)) {
                    setIcon(imageIcon);
                    setText(file.getName());
                } else {
                    setIcon(fileIcon);
                    setText(file.getName());
                }
            }

            return this;
        }

        private int countImageFilesInDirectory(File directory) {
            int count = 0;
            Map<File, Boolean> fileHasImageMap = dicomFileManager.getFileHasImageMap();

            if (directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.isDirectory() && fileHasImageMap.getOrDefault(file, false)) {
                            count++;
                        }
                    }
                }
            }
            return count;
        }
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        JMenuItem openMenuItem = new JMenuItem("Open DICOM Files/Folder");
        openMenuItem.setMnemonic(KeyEvent.VK_O);
        openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        openMenuItem.addActionListener(e -> openDicomFiles());
        fileMenu.add(openMenuItem);
        JMenuItem exportBatchMenuItem = new JMenuItem("Batch Export Images");
        exportBatchMenuItem.setMnemonic(KeyEvent.VK_B);
        exportBatchMenuItem.addActionListener(e -> showBatchExportDialog());
        fileMenu.add(exportBatchMenuItem);

        fileMenu.addSeparator();
        JMenuItem exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.setMnemonic(KeyEvent.VK_X);
        exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, KeyEvent.ALT_DOWN_MASK));
        exitMenuItem.addActionListener(e -> exitApplication());
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);
        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.setMnemonic(KeyEvent.VK_S);
        JMenuItem croppingDimensionsMenuItem = new JMenuItem("Cropping Dimensions");
        croppingDimensionsMenuItem.setMnemonic(KeyEvent.VK_C);
        croppingDimensionsMenuItem.addActionListener(e -> imageRectangleOverlay.showSettingsDialog(DicomManagerGui.this));
        settingsMenu.add(croppingDimensionsMenuItem);
        JMenuItem rulerSettingsMenuItem = new JMenuItem("Ruler Settings");
        rulerSettingsMenuItem.setMnemonic(KeyEvent.VK_R);
        rulerSettingsMenuItem.addActionListener(e -> showRulerSettingsDialog());
        settingsMenu.add(rulerSettingsMenuItem);

        menuBar.add(settingsMenu);
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        JMenuItem aboutMenuItem = new JMenuItem("About");
        aboutMenuItem.setMnemonic(KeyEvent.VK_A);
        aboutMenuItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void exitApplication() {
        int option = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit?",
                "Exit Application",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (option == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    private void showAboutDialog() {
        JTextArea aboutText = new JTextArea(
                "DICOM Image Manager\n\n" +
                        "Features:\n" +
                        "- Open DICOM files or folders\n" +
                        "- Recursive search for DICOM files\n" +
                        "- Hierarchical file tree view\n" +
                        "- View DICOM metadata\n" +
                        "- Export images to various formats\n" +
                        "- Batch export multiple images\n" +
                        "- Image cropping tool\n" +
                        "- Image rulers with customizable units\n" +
                        "- Advanced 3D volume visualization\n" +
                        "  - Multiple rendering techniques\n" +
                        "  - Transfer function control\n" +
                        "  - Interpolation between slices\n" +
                        "  - Efficient memory management\n\n" +
                        "Created with Java and dcm4che library"
        );
        aboutText.setEditable(false);
        aboutText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        aboutText.setBackground(new JLabel().getBackground());
        aboutText.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JOptionPane.showMessageDialog(
                this,
                aboutText,
                "About DICOM Image Manager",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void showRulerSettingsDialog() {
        JDialog dialog = new JDialog(this, "Ruler Settings", true);
        dialog.setSize(300, 200);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(new JLabel("Show Rulers:"));
        JCheckBox showRulersCheckBox = new JCheckBox();
        showRulersCheckBox.setSelected(imageRulerPanel.isShowRulers());
        mainPanel.add(showRulersCheckBox);
        mainPanel.add(new JLabel("Pixels per Unit:"));
        JSpinner pixelsPerUnitSpinner = new JSpinner(
                new SpinnerNumberModel(imageRulerPanel.getPixelsPerUnit(), 10, 500, 10));
        mainPanel.add(pixelsPerUnitSpinner);
        JPanel unitLabelPanel = new JPanel(new BorderLayout(5, 0));
        unitLabelPanel.add(new JLabel("Unit Label:"), BorderLayout.WEST);
        JTextField unitLabelField = new JTextField(imageRulerPanel.getUnitLabel());
        unitLabelPanel.add(unitLabelField, BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> {
            imageRulerPanel.setShowRulers(showRulersCheckBox.isSelected());
            imageRulerPanel.setPixelsPerUnit((Integer) pixelsPerUnitSpinner.getValue());
            imageRulerPanel.setUnitLabel(unitLabelField.getText());
            dialog.dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonsPanel.add(applyButton);
        buttonsPanel.add(cancelButton);
        JPanel containerPanel = new JPanel(new BorderLayout(10, 10));
        containerPanel.add(mainPanel, BorderLayout.CENTER);
        containerPanel.add(unitLabelPanel, BorderLayout.NORTH);
        containerPanel.add(buttonsPanel, BorderLayout.SOUTH);

        dialog.add(containerPanel);
        dialog.setVisible(true);
    }

    private void openDicomFiles() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select DICOM Files or Folder");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setAcceptAllFileFilterUsed(true);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length == 1 && selectedFiles[0].isDirectory()) {
                loadDicomFilesFromFolder(selectedFiles[0]);
            } else {
                loadSelectedDicomFiles(selectedFiles);
            }
        }
    }

    private void loadDicomFilesFromFolder(File folder) {
        batchExportList.clear();

        dicomFileManager.clearFiles();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) dicomFileManager.getTreeModel().getRoot();
        root.setUserObject("Loading...");
        dicomFileManager.getTreeModel().reload();
        currentImageIndex = -1;

        statusLabel.setText("Searching for DICOM files...");

        new SwingWorker<List<File>, String>() {
            private JProgressBar progressBar = (JProgressBar) ((JPanel) getContentPane().getComponent(2)).getComponent(1);

            @Override
            protected List<File> doInBackground() throws Exception {
                progressBar.setVisible(true);
                progressBar.setIndeterminate(true);

                List<File> foundFiles = dicomFileManager.findAllFilesRecursively(folder);

                progressBar.setIndeterminate(false);
                progressBar.setMaximum(foundFiles.size());

                List<File> imageFiles = new ArrayList<>();
                Map<File, Boolean> fileHasImageMap = dicomFileManager.getFileHasImageMap();

                for (int i = 0; i < foundFiles.size(); i++) {
                    File file = foundFiles.get(i);
                    publish("Checking: " + file.getName());
                    progressBar.setValue(i + 1);

                    boolean isDicom = dicomFileManager.isDicomFile(file);
                    boolean hasPixelData = dicomFileManager.hasPixelData(file);

                    fileHasImageMap.put(file, isDicom && hasPixelData);

                    if (isDicom && hasPixelData) {
                        imageFiles.add(file);
                        publish("Found image: " + file.getName());
                    } else if (dicomFileManager.isDebugMode()) {
                        publish("Skipped: " + file.getName() +
                                " (DICOM=" + isDicom +
                                ", PixelData=" + hasPixelData + ")");
                    }
                }

                return imageFiles;
            }

            @Override
            protected void process(List<String> chunks) {
                statusLabel.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);

                try {
                    List<File> foundImageFiles = get();

                    if (foundImageFiles.isEmpty()) {
                        statusLabel.setText("No image files found in the selected folder");
                        JOptionPane.showMessageDialog(DicomManagerGui.this,
                                "No DICOM image files found in the selected folder and its subfolders.\n" +
                                        "The DICOM files may be Structured Reports (SR) or other non-image types.",
                                "No Images", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        dicomFileManager.addFiles(foundImageFiles);
                        dicomFileManager.buildFileTree(folder, foundImageFiles);
                        expandAllNodes(dicomFileTree);

                        statusLabel.setText("Found " + foundImageFiles.size() + " DICOM image files");

                        if (!foundImageFiles.isEmpty()) {
                            currentImageIndex = 0;
                            loadImage(foundImageFiles.get(0));
                            updateNavigationButtons();
                        }
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                    JOptionPane.showMessageDialog(DicomManagerGui.this,
                            "Error scanning for DICOM files: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void expandAllNodes(JTree tree) {
        int row = 0;
        while (row < tree.getRowCount()) {
            tree.expandRow(row);
            row++;
        }
    }

    private void loadSelectedDicomFiles(File[] selectedFiles) {
        batchExportList.clear();

        dicomFileManager.clearFiles();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) dicomFileManager.getTreeModel().getRoot();
        root.setUserObject("DICOM Files");
        dicomFileManager.getTreeModel().reload();
        currentImageIndex = -1;

        statusLabel.setText("Checking selected files...");

        new SwingWorker<List<File>, String>() {
            private JProgressBar progressBar = (JProgressBar) ((JPanel) getContentPane().getComponent(2)).getComponent(1);

            @Override
            protected List<File> doInBackground() throws Exception {
                progressBar.setVisible(true);
                progressBar.setMaximum(selectedFiles.length);

                List<File> imageFiles = new ArrayList<>();
                Map<File, Boolean> fileHasImageMap = dicomFileManager.getFileHasImageMap();

                for (int i = 0; i < selectedFiles.length; i++) {
                    File file = selectedFiles[i];
                    publish("Checking: " + file.getName());
                    progressBar.setValue(i + 1);

                    if (file.isFile()) {
                        boolean isDicom = dicomFileManager.isDicomFile(file);
                        boolean hasPixelData = dicomFileManager.hasPixelData(file);

                        fileHasImageMap.put(file, isDicom && hasPixelData);

                        if (isDicom && hasPixelData) {
                            imageFiles.add(file);
                            publish("Found image: " + file.getName());
                        } else if (dicomFileManager.isDebugMode()) {
                            publish("Skipped: " + file.getName() +
                                    " (DICOM=" + isDicom +
                                    ", PixelData=" + hasPixelData + ")");
                        }
                    } else if (file.isDirectory()) {
                        List<File> dirFiles = dicomFileManager.findAllFilesRecursively(file);
                        for (File dirFile : dirFiles) {
                            boolean isDicom = dicomFileManager.isDicomFile(dirFile);
                            boolean hasPixelData = dicomFileManager.hasPixelData(dirFile);

                            fileHasImageMap.put(dirFile, isDicom && hasPixelData);

                            if (isDicom && hasPixelData) {
                                imageFiles.add(dirFile);
                                publish("Found image: " + dirFile.getName());
                            } else if (dicomFileManager.isDebugMode()) {
                                publish("Skipped: " + dirFile.getName() +
                                        " (DICOM=" + isDicom +
                                        ", PixelData=" + hasPixelData + ")");
                            }
                        }
                    }
                }

                return imageFiles;
            }

            @Override
            protected void process(List<String> chunks) {
                statusLabel.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);

                try {
                    List<File> foundImageFiles = get();

                    if (foundImageFiles.isEmpty()) {
                        statusLabel.setText("No valid DICOM image files found");
                        JOptionPane.showMessageDialog(DicomManagerGui.this,
                                "No valid DICOM image files found in the selection.",
                                "No Images", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        dicomFileManager.addFiles(foundImageFiles);
                        dicomFileManager.buildFlatFileTree(foundImageFiles);
                        expandAllNodes(dicomFileTree);

                        statusLabel.setText("Found " + foundImageFiles.size() + " DICOM image files");

                        if (!foundImageFiles.isEmpty()) {
                            currentImageIndex = 0;
                            loadImage(foundImageFiles.get(0));
                            updateNavigationButtons();
                        }
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                    JOptionPane.showMessageDialog(DicomManagerGui.this,
                            "Error processing selected files: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadImage(File file) {
        try {
            statusLabel.setText("Loading: " + file.getName());

            new SwingWorker<java.awt.image.BufferedImage, Void>() {
                @Override
                protected java.awt.image.BufferedImage doInBackground() throws Exception {
                    return dicomFileManager.loadImage(file);
                }

                @Override
                protected void done() {
                    try {
                        java.awt.image.BufferedImage image = get();
                        if (image != null) {
                            imageRulerPanel.setImage(image);
                            statusLabel.setText("Loaded: " + file.getName() +
                                    " (" + image.getWidth() + "x" + image.getHeight() + ")");
                        } else {
                            imageRulerPanel.setImage(null);
                            statusLabel.setText("Failed to load image: " + file.getName());
                        }
                    } catch (Exception e) {
                        imageRulerPanel.setImage(null);
                        statusLabel.setText("Error loading image: " + e.getMessage());
                        JOptionPane.showMessageDialog(DicomManagerGui.this,
                                "Error loading DICOM image: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error loading DICOM file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateNavigationButtons() {
        List<File> imageFiles = dicomFileManager.getImageFiles();
        prevButton.setEnabled(currentImageIndex > 0);
        nextButton.setEnabled(currentImageIndex < imageFiles.size() - 1);
        infoButton.setEnabled(currentImageIndex >= 0);
        exportButton.setEnabled(currentImageIndex >= 0);
        view3DButton.setEnabled(currentImageIndex >= 0);
        exportBatchButton.setEnabled(!imageFiles.isEmpty() || !batchExportList.isEmpty());
    }

    private void showPreviousImage() {
        if (currentImageIndex > 0) {
            currentImageIndex--;
            List<File> imageFiles = dicomFileManager.getImageFiles();
            loadImage(imageFiles.get(currentImageIndex));
            updateNavigationButtons();
        }
    }

    private void showNextImage() {
        List<File> imageFiles = dicomFileManager.getImageFiles();
        if (currentImageIndex < imageFiles.size() - 1) {
            currentImageIndex++;
            loadImage(imageFiles.get(currentImageIndex));
            updateNavigationButtons();
        }
    }

    private void showDicomInfo() {
        String dicomInfo = dicomFileManager.getDicomInfo(currentImageIndex);
        if (dicomInfo != null && !dicomInfo.isEmpty()) {
            JTextArea textArea = new JTextArea(dicomInfo);
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(500, 400));

            JOptionPane.showMessageDialog(this,
                    scrollPane,
                    "DICOM Metadata",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "No DICOM metadata available",
                    "DICOM Metadata",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void exportImage() {
        java.awt.image.BufferedImage currentImage = imageRulerPanel.getCurrentImage();
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this,
                    "No image to export",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Image");
        fileChooser.setSelectedFile(new File("export.png"));

        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG Images", "png");
        FileNameExtensionFilter jpgFilter = new FileNameExtensionFilter("JPEG Images", "jpg", "jpeg");
        FileNameExtensionFilter bmpFilter = new FileNameExtensionFilter("BMP Images", "bmp");

        fileChooser.addChoosableFileFilter(pngFilter);
        fileChooser.addChoosableFileFilter(jpgFilter);
        fileChooser.addChoosableFileFilter(bmpFilter);
        fileChooser.setFileFilter(pngFilter);

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            String format = "png";

            if (fileChooser.getFileFilter() == jpgFilter) {
                format = "jpg";
                if (!outputFile.getName().toLowerCase().endsWith(".jpg") &&
                        !outputFile.getName().toLowerCase().endsWith(".jpeg")) {
                    outputFile = new File(outputFile.getParent(), outputFile.getName() + ".jpg");
                }
            } else if (fileChooser.getFileFilter() == bmpFilter) {
                format = "bmp";
                if (!outputFile.getName().toLowerCase().endsWith(".bmp")) {
                    outputFile = new File(outputFile.getParent(), outputFile.getName() + ".bmp");
                }
            } else {
                if (!outputFile.getName().toLowerCase().endsWith(".png")) {
                    outputFile = new File(outputFile.getParent(), outputFile.getName() + ".png");
                }
            }

            final File finalOutputFile = outputFile;
            final String finalFormat = format;

            try {
                statusLabel.setText("Exporting image...");

                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        dicomFileManager.exportImage(currentImage, finalFormat, finalOutputFile);
                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                            statusLabel.setText("Exported: " + finalOutputFile.getName());
                            JOptionPane.showMessageDialog(DicomManagerGui.this,
                                    "Image exported successfully to:\n" + finalOutputFile.getAbsolutePath(),
                                    "Export Complete",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } catch (Exception e) {
                            statusLabel.setText("Export failed: " + e.getMessage());
                            JOptionPane.showMessageDialog(DicomManagerGui.this,
                                    "Error exporting image: " + e.getMessage(),
                                    "Export Error",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }.execute();
            } catch (Exception e) {
                statusLabel.setText("Export failed: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Error exporting image: " + e.getMessage(),
                        "Export Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void show3DView() {
        java.awt.image.BufferedImage currentImage = imageRulerPanel.getCurrentImage();
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this,
                    "No image loaded for 3D view",
                    "3D View Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<File> seriesImages = dicomFileManager.findImagesInSameSeries(currentImageIndex);

        if (seriesImages.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    "Need at least 2 images in the same series to create 3D view",
                    "3D View Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        dicon3DFeatures.show3DView(this, seriesImages, dicomFileManager);
    }

    private void showBatchExportDialog() {
        List<File> imageFiles = dicomFileManager.getImageFiles();
        if ((imageFiles == null || imageFiles.isEmpty()) && batchExportList.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No DICOM files loaded. Please open DICOM files first.",
                    "Batch Export Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        DicomBatchExporter.showExporter(this, dicomFileManager, batchExportList);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            DicomManagerGui manager = new DicomManagerGui();
            manager.setVisible(true);
        });
    }
}
