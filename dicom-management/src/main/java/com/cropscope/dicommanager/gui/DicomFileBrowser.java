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
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class DicomFileBrowser {
    private DicomFileManager dicomFileManager;
    private DicomManagerGui parentGui;

    private JTree dicomFileTree;
    private DefaultTreeModel treeModel;
    private JScrollPane treeScrollPane;

    private List<File> imageFiles;
    private int currentImageIndex = -1;
    private File rootFolder;

    private Map<File, Boolean> fileHasImageMap = new HashMap<>();

    private List<File> batchExportList = new ArrayList<>();

    private JPopupMenu contextMenu;
    private JMenuItem addToBatchExportItem;

    public interface FileSelectionListener {
        void onFileSelected(File file, int index);

        void onFilesLoaded(List<File> files);

        void onStatusUpdate(String message);
    }

    private FileSelectionListener listener;

    public DicomFileBrowser(DicomFileManager dicomFileManager, DicomManagerGui parentGui) {
        this.dicomFileManager = dicomFileManager;
        this.parentGui = parentGui;

        initializeComponents();
    }

    private void initializeComponents() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("DICOM Files");
        treeModel = new DefaultTreeModel(root);
        dicomFileTree = new JTree(treeModel);
        dicomFileTree.setCellRenderer(new DicomTreeCellRenderer());
        dicomFileTree.setRootVisible(false);
        dicomFileTree.setShowsRootHandles(true);
        dicomFileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        treeScrollPane = new JScrollPane(dicomFileTree);
        treeScrollPane.setPreferredSize(new Dimension(300, 0));
        createContextMenu();
        dicomFileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = dicomFileTree.getRowForLocation(e.getX(), e.getY());
                    if (row != -1) {
                        dicomFileTree.setSelectionRow(row);
                        TreePath path = dicomFileTree.getPathForRow(row);
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

                        if (node.getUserObject() instanceof FileWrapper) {
                            FileWrapper wrapper = (FileWrapper) node.getUserObject();
                            File file = wrapper.getFile();
                            if (file.isDirectory()) {
                                addToBatchExportItem.setText("Add directory to batch export");
                            } else {
                                addToBatchExportItem.setText("Add file to batch export");
                            }
                            contextMenu.show(dicomFileTree, e.getX(), e.getY());
                        }
                    }
                }
            }
        });
        dicomFileTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) dicomFileTree.getLastSelectedPathComponent();
                if (selectedNode != null && selectedNode.getUserObject() instanceof FileWrapper) {
                    File selectedFile = ((FileWrapper) selectedNode.getUserObject()).getFile();
                    if (imageFiles.contains(selectedFile)) {
                        currentImageIndex = imageFiles.indexOf(selectedFile);
                        if (listener != null) {
                            listener.onFileSelected(selectedFile, currentImageIndex);
                        }
                    }
                }
            }
        });
    }

    private void createContextMenu() {
        contextMenu = new JPopupMenu();
        addToBatchExportItem = new JMenuItem("Add to batch export");
        addToBatchExportItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath selectedPath = dicomFileTree.getSelectionPath();
                if (selectedPath != null) {
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
                    if (selectedNode.getUserObject() instanceof FileWrapper) {
                        FileWrapper wrapper = (FileWrapper) selectedNode.getUserObject();
                        File file = wrapper.getFile();

                        if (file.isDirectory()) {
                            List<File> dicomFiles = dicomFileManager.findAllFilesRecursively(file);
                            int addedCount = 0;

                            for (File dicomFile : dicomFiles) {
                                if (dicomFileManager.isDicomFile(dicomFile) &&
                                        dicomFileManager.hasPixelData(dicomFile) &&
                                        !batchExportList.contains(dicomFile)) {
                                    batchExportList.add(dicomFile);
                                    addedCount++;
                                }
                            }

                            if (listener != null) {
                                listener.onStatusUpdate("Added " + addedCount + " files from directory to batch export list");
                            }
                        } else {
                            if (!batchExportList.contains(file)) {
                                batchExportList.add(file);
                                if (listener != null) {
                                    listener.onStatusUpdate("Added file to batch export list");
                                }
                            }
                        }
                    }
                }
            }
        });

        contextMenu.add(addToBatchExportItem);
        contextMenu.addSeparator();
        JMenuItem showBatchExportItem = new JMenuItem("Show batch export dialog");
        showBatchExportItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!batchExportList.isEmpty()) {
                    DicomBatchExporter.showExporter(parentGui, dicomFileManager, batchExportList);
                } else {
                    JOptionPane.showMessageDialog(parentGui,
                            "No files in batch export list. Right-click on files or directories and select 'Add to batch export'.",
                            "Batch Export Empty",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        contextMenu.add(showBatchExportItem);
    }

    public JScrollPane getComponent() {
        return treeScrollPane;
    }

    public void setFileSelectionListener(FileSelectionListener listener) {
        this.listener = listener;
    }

    public List<File> getBatchExportList() {
        return new ArrayList<>(batchExportList);
    }

    public void clearBatchExportList() {
        batchExportList.clear();
    }

    public void loadDicomFilesFromFolder(File folder) {
        dicomFileManager.clearFiles();
        fileHasImageMap.clear();
        treeModel.setRoot(new DefaultMutableTreeNode("Loading..."));
        currentImageIndex = -1;
        rootFolder = folder;
        if (listener != null) {
            listener.onStatusUpdate("Searching for DICOM files...");
        }
        new SwingWorker<List<File>, String>() {
            @Override
            protected List<File> doInBackground() throws Exception {
                List<File> foundFiles = dicomFileManager.findAllFilesRecursively(folder);

                List<File> imageFiles = new ArrayList<>();
                for (int i = 0; i < foundFiles.size(); i++) {
                    File file = foundFiles.get(i);
                    publish("Checking: " + file.getName());

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
                if (listener != null) {
                    listener.onStatusUpdate(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    List<File> foundImageFiles = get();

                    if (foundImageFiles.isEmpty()) {
                        if (listener != null) {
                            listener.onStatusUpdate("No image files found in the selected folder");
                        }
                        JOptionPane.showMessageDialog(parentGui,
                                "No DICOM image files found in the selected folder and its subfolders.\n" +
                                        "The DICOM files may be Structured Reports (SR) or other non-image types.",
                                "No Images", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        dicomFileManager.addFiles(foundImageFiles);
                        imageFiles = dicomFileManager.getImageFiles();
                        buildFileTree(folder, foundImageFiles);

                        if (listener != null) {
                            listener.onStatusUpdate("Found " + foundImageFiles.size() + " DICOM image files");
                            listener.onFilesLoaded(foundImageFiles);
                        }
                        if (!foundImageFiles.isEmpty()) {
                            currentImageIndex = 0;
                            if (listener != null) {
                                listener.onFileSelected(foundImageFiles.get(0), 0);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onStatusUpdate("Error: " + e.getMessage());
                    }
                    JOptionPane.showMessageDialog(parentGui,
                            "Error scanning for DICOM files: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    public void loadSelectedDicomFiles(File[] selectedFiles) {
        dicomFileManager.clearFiles();
        fileHasImageMap.clear();
        treeModel.setRoot(new DefaultMutableTreeNode("DICOM Files"));
        currentImageIndex = -1;
        if (listener != null) {
            listener.onStatusUpdate("Checking selected files...");
        }
        new SwingWorker<List<File>, String>() {
            @Override
            protected List<File> doInBackground() throws Exception {
                List<File> imageFiles = new ArrayList<>();
                for (int i = 0; i < selectedFiles.length; i++) {
                    File file = selectedFiles[i];
                    publish("Checking: " + file.getName());

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
                if (listener != null) {
                    listener.onStatusUpdate(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    List<File> foundImageFiles = get();

                    if (foundImageFiles.isEmpty()) {
                        if (listener != null) {
                            listener.onStatusUpdate("No valid DICOM image files found");
                        }
                        JOptionPane.showMessageDialog(parentGui,
                                "No valid DICOM image files found in the selection.",
                                "No Images", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        dicomFileManager.addFiles(foundImageFiles);
                        imageFiles = dicomFileManager.getImageFiles();
                        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Selected Files");
                        treeModel.setRoot(rootNode);
                        Map<File, DefaultMutableTreeNode> directoryNodes = new HashMap<>();

                        for (File file : foundImageFiles) {
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
                        expandAllNodes(dicomFileTree);

                        if (listener != null) {
                            listener.onStatusUpdate("Found " + foundImageFiles.size() + " DICOM image files");
                            listener.onFilesLoaded(foundImageFiles);
                        }
                        if (!foundImageFiles.isEmpty()) {
                            currentImageIndex = 0;
                            if (listener != null) {
                                listener.onFileSelected(foundImageFiles.get(0), 0);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onStatusUpdate("Error: " + e.getMessage());
                    }
                    JOptionPane.showMessageDialog(parentGui,
                            "Error processing selected files: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void buildFileTree(File folder, List<File> imageFiles) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new FileWrapper(folder, folder.getName()));
        buildTreeNode(rootNode, folder, imageFiles);
        treeModel.setRoot(rootNode);
        treeModel.reload();
        expandAllNodes(dicomFileTree);
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

    private void expandAllNodes(JTree tree) {
        int row = 0;
        while (row < tree.getRowCount()) {
            tree.expandRow(row);
            row++;
        }
    }

    public List<File> getImageFiles() {
        return imageFiles;
    }

    public int getCurrentImageIndex() {
        return currentImageIndex;
    }

    public void setCurrentImageIndex(int index) {
        if (index >= 0 && index < imageFiles.size()) {
            currentImageIndex = index;
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
            findAndSelectFileNode(root, imageFiles.get(index));
        }
    }

    private boolean findAndSelectFileNode(TreeNode node, File file) {
        if (node instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode dmNode = (DefaultMutableTreeNode) node;
            Object userObject = dmNode.getUserObject();

            if (userObject instanceof FileWrapper) {
                FileWrapper wrapper = (FileWrapper) userObject;
                if (wrapper.getFile().equals(file)) {
                    TreePath path = new TreePath(treeModel.getPathToRoot(dmNode));
                    dicomFileTree.setSelectionPath(path);
                    dicomFileTree.scrollPathToVisible(path);
                    return true;
                }
            }
            for (int i = 0; i < dmNode.getChildCount(); i++) {
                if (findAndSelectFileNode(dmNode.getChildAt(i), file)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void showBatchExportDialog() {
        if (!batchExportList.isEmpty()) {
            DicomBatchExporter.showExporter(parentGui, dicomFileManager, batchExportList);
        } else {
            JOptionPane.showMessageDialog(parentGui,
                    "No files in batch export list. Right-click on files or directories and select 'Add to batch export'.",
                    "Batch Export Empty",
                    JOptionPane.INFORMATION_MESSAGE);
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

            if (userObject instanceof FileWrapper) {
                File file = ((FileWrapper) userObject).getFile();
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

    private static class FileWrapper {
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