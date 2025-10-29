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

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.json.JSONArray;
import org.json.JSONObject;

public class ImageCroppingCore {

    private final CropEngine cropEngine;

    public File getSourceRootDir() {
        return sourceRootDir;
    }

    public File getSaveDirectory() {
        return saveDirectory;
    }

    public int getCropWidth() {
        return cropWidth;
    }

    public int getCropHeight() {
        return cropHeight;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getUserName() {
        return userName;
    }

    public List<File> getImageFiles() {
        return imageFiles;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    public JCheckBox getCbAutoExport() {
        return cbAutoExport;
    }

    public JLabel getStatusLabel() {
        return statusLabel;
    }

    public JPanel getBottomPanel() {
        return buildBottomPanel();
    }

    public ImagePanel getImagePanel() {
        return imagePanel;
    }

    public BufferedImage getOriginalImage() {
        return originalImage;
    }

    public File getCurrentImageFile() {
        return currentImageFile;
    }

    private final ImageCropping mainFrame;
    private File sourceRootDir;
    private File saveDirectory;
    private int cropWidth;
    private int cropHeight;
    private String projectName = "Generic annotation";
    private String userName = "Generic user";

    private List<File> subfolders;
    private List<File> imageFiles;
    private int currentSubfolderIndex = -1;
    private int currentImageIndex = -1;

    private BufferedImage originalImage;
    private File currentImageFile;

    private final List<CropMetadata> cropMetadataQueue = new ArrayList<CropMetadata>();

    private final Map<String, Integer> sequenceCounters = new HashMap<String, Integer>();

    private int skippedImagesCount = 0;

    private boolean debugEnabled = true;

    private ImagePanel imagePanel;
    private JLabel modeLabel;
    private JLabel skippedLabel;
    private JLabel statusLabel;
    private JButton btnPrevFolder, btnPrevImage, btnNextImage, btnNextFolder;
    private JButton btnExport, btnClose;
    private JCheckBox cbAutoExport;

    private Timer statusClearTimer;

    private final ImageCache cache = new ImageCache(3);
    private final ExecutorService preloadExec = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ImagePreloader");
            t.setDaemon(true);
            return t;
        }
    });

    public ImageCroppingCore(ImageCropping mainFrame, File sourceFolder, File saveFolder, int cropWidth, int cropHeight) {
        this.mainFrame = mainFrame;
        this.sourceRootDir = sourceFolder;
        this.saveDirectory = saveFolder;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.cropEngine = new CropEngine(this.saveDirectory);

        if (sourceRootDir == null || !sourceRootDir.isDirectory()) {
            JOptionPane.showMessageDialog(null, "Please select a valid source directory.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        if (saveDirectory != null && !saveDirectory.isDirectory()) {
            try {
                saveDirectory.mkdirs();
            } catch (Throwable ignore) {
            }
        }
        subfolders = new ArrayList<File>();
        File[] files = safeListFiles(sourceRootDir);
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                public int compare(File a, File b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                if (f.isDirectory()) subfolders.add(f);
            }
        }
        if (subfolders.isEmpty()) {
            if (containsAnyImagesRecursively(sourceRootDir)) {
                if (debugEnabled) {
                    System.out.println("[DEBUG][Core] No subfolders under: " + sourceRootDir.getAbsolutePath()
                            + " — treating the root as a single dataset folder.");
                }
                subfolders.add(sourceRootDir);
            } else {
                JOptionPane.showMessageDialog(null,
                        "No subfolders (and no images) found in: " + sourceRootDir.getAbsolutePath(),
                        "No Data", JOptionPane.INFORMATION_MESSAGE);
                System.exit(1);
            }
        }

        currentSubfolderIndex = 0;
        initializeUI();
    }

    public void initializeUI() {
        imagePanel = new ImagePanel();
        imagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        imagePanel.setBackground(Color.GRAY);
        modeLabel = new JLabel("Mode: Immediate", SwingConstants.LEFT);

        skippedLabel = new JLabel("Skipped: 0", SwingConstants.LEFT);

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        Color iconColor = new Color(60, 60, 60);
        btnPrevFolder = new PillButton("", new VectorIcon(VectorIcon.Type.PREV_FOLDER, 18, iconColor), "Previous folder (Up)");
        btnPrevImage = new PillButton("", new VectorIcon(VectorIcon.Type.PREV_IMAGE, 18, iconColor), "Previous image (Left)");
        btnNextImage = new PillButton("", new VectorIcon(VectorIcon.Type.NEXT_IMAGE, 18, iconColor), "Next image (Right)");
        btnNextFolder = new PillButton("", new VectorIcon(VectorIcon.Type.NEXT_FOLDER, 18, iconColor), "Next folder (Down)");

        btnPrevFolder.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showPrevFolder();
            }
        });
        btnPrevImage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showPrevImage();
            }
        });
        btnNextImage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showNextImage();
            }
        });
        btnNextFolder.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showNextFolder();
            }
        });
        btnExport = new PillButton("Export Metadata", null, "Export crop metadata to JSON");
        btnExport.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportMetadataJson();
            }
        });

        cbAutoExport = new JCheckBox();
        cbAutoExport.setOpaque(false);
        cbAutoExport.setToolTipText("Enable automatic export after N crops");

        btnClose = new PillButton("Close", null, "Close");
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mainFrame.requestExit();
            }
        });
    }

    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(new EmptyBorder(10, 12, 12, 12));
        JPanel leftStack = new JPanel();
        leftStack.setOpaque(false);
        leftStack.setLayout(new BoxLayout(leftStack, BoxLayout.Y_AXIS));
        JPanel l1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        l1.setOpaque(false);
        l1.add(modeLabel);
        JPanel l2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        l2.setOpaque(false);
        l2.add(skippedLabel);
        leftStack.add(l1);
        leftStack.add(Box.createVerticalStrut(3));
        leftStack.add(l2);
        bottom.add(leftStack, BorderLayout.WEST);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        nav.setOpaque(false);
        nav.add(btnPrevFolder);
        nav.add(btnPrevImage);
        nav.add(btnNextImage);
        nav.add(btnNextFolder);
        bottom.add(nav, BorderLayout.CENTER);
        JPanel rightGB = new JPanel(new GridBagLayout());
        rightGB.setOpaque(false);
        GridBagConstraints gbcR = new GridBagConstraints();
        gbcR.gridx = 0;
        gbcR.gridy = 0;
        gbcR.insets = new Insets(0, 8, 0, 0);
        gbcR.anchor = GridBagConstraints.WEST;
        gbcR.fill = GridBagConstraints.NONE;
        gbcR.weightx = 0;
        rightGB.add(cbAutoExport, gbcR);
        JPanel buttonsEast = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonsEast.setOpaque(false);
        buttonsEast.add(btnExport);
        buttonsEast.add(btnClose);
        gbcR.gridx = 1;
        gbcR.gridy = 0;
        gbcR.insets = new Insets(0, 8, 0, 0);
        gbcR.anchor = GridBagConstraints.EAST;
        gbcR.fill = GridBagConstraints.HORIZONTAL;
        gbcR.weightx = 1.0;
        rightGB.add(buttonsEast, gbcR);
        gbcR.gridx = 0;
        gbcR.gridy = 1;
        gbcR.insets = new Insets(3, 8, 0, 0);
        gbcR.anchor = GridBagConstraints.WEST;
        gbcR.fill = GridBagConstraints.NONE;
        gbcR.weightx = 0;
        JLabel autoExportLabel = new JLabel("Auto export");
        autoExportLabel.setForeground(Color.DARK_GRAY);
        autoExportLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        autoExportLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cbAutoExport.doClick();
            }
        });
        rightGB.add(autoExportLabel, gbcR);
        gbcR.gridx = 1;
        gbcR.gridy = 1;
        gbcR.insets = new Insets(0, 0, 0, 0);
        gbcR.anchor = GridBagConstraints.EAST;
        gbcR.fill = GridBagConstraints.HORIZONTAL;
        gbcR.weightx = 1.0;
        rightGB.add(Box.createHorizontalStrut(1), gbcR);
        Color infoColor = autoExportLabel.getForeground();
        modeLabel.setForeground(infoColor);
        skippedLabel.setForeground(infoColor);

        bottom.add(rightGB, BorderLayout.EAST);
        return bottom;
    }

    public void setCroppingMode(boolean immediate) {
        modeLabel.setText(immediate ? "Mode: Immediate" : "Mode: Designation");
        setStatus((immediate)
                ? "Cropping mode: Immediate (saves crops)"
                : "Cropping mode: Designation (metadata only)", 1800);
    }

    public void showSettingsDialog() {
        final JDialog d = new JDialog(mainFrame, "Settings", true);
        d.setLayout(new BorderLayout(10, 10));
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        final JTextField tfProject = new JTextField(projectName, 24);
        final JTextField tfUser = new JTextField(userName, 24);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        p.add(new JLabel("Project:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        p.add(tfProject, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        p.add(new JLabel("User:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        p.add(tfUser, gbc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        btns.add(ok);
        btns.add(cancel);

        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String pj = tfProject.getText().trim();
                String us = tfUser.getText().trim();
                if (!pj.isEmpty()) projectName = pj;
                if (!us.isEmpty()) userName = us;
                setStatus("Settings updated: project=" + projectName + ", user=" + userName, 2200);
                d.dispose();
            }
        });
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                d.dispose();
            }
        });

        d.add(p, BorderLayout.CENTER);
        d.add(btns, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(mainFrame);
        d.setVisible(true);
    }

    public void loadCurrentSubfolder() {
        File folder = subfolders.get(currentSubfolderIndex);
        imageFiles = new ArrayList<File>();
        findImagesRecursively(folder);
        Collections.sort(imageFiles, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath());
            }
        });

        currentImageIndex = -1;
        skippedImagesCount = 0;
        updateSkippedLabel();

        if (!imageFiles.isEmpty()) {
            cache.preload(imageFiles.get(0));
            if (imageFiles.size() > 1) cache.preload(imageFiles.get(1));
        }
        setStatus("📂 Now loading from: " + getSavePrefix(), 1500);
    }

    public String getSavePrefix() {
        File folder = subfolders.get(currentSubfolderIndex);
        String prefix = folder.getName();
        return (prefix == null || prefix.trim().isEmpty()) ? "image" : prefix;
    }

    private void findImagesRecursively(File dir) {
        if (saveDirectory != null && isSubdirectoryOf(dir, saveDirectory)) return;
        File[] files = safeListFiles(dir);
        if (files == null) return;
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) findImagesRecursively(f);
            else if (isImageFile(f)) imageFiles.add(f);
        }
    }

    private boolean containsAnyImagesRecursively(File dir) {
        File[] files = safeListFiles(dir);
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (containsAnyImagesRecursively(f)) return true;
            } else {
                if (isImageFile(f)) return true;
            }
        }
        return false;
    }


    private static File[] safeListFiles(File dir) {
        try {
            return dir.listFiles();
        } catch (SecurityException se) {
            return null;
        }
    }

    private boolean isSubdirectoryOf(File child, File parent) {
        try {
            File canonicalChild = child.getCanonicalFile();
            File canonicalParent = parent.getCanonicalFile();
            while (canonicalChild != null) {
                if (canonicalChild.equals(canonicalParent)) return true;
                canonicalChild = canonicalChild.getParentFile();
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".bmp");
    }

    public void showNextFolder() {
        if (subfolders == null || subfolders.isEmpty()) return;
        currentSubfolderIndex = (currentSubfolderIndex + 1) % subfolders.size();
        loadCurrentSubfolder();
        showNextImage();
    }

    public void showPrevFolder() {
        if (subfolders == null || subfolders.isEmpty()) return;
        currentSubfolderIndex = (currentSubfolderIndex - 1 + subfolders.size()) % subfolders.size();
        loadCurrentSubfolder();
        showNextImage();
    }

    public void jumpToImage(int index) {
        if (imageFiles == null || imageFiles.isEmpty()) return;
        if (index < 0) index = 0;
        if (index >= imageFiles.size()) index = imageFiles.size() - 1;
        currentImageIndex = index - 1;
        showNextImage();
    }

    public void showNextImage() {
        showImageWithDirection(+1);
    }

    public void showPrevImage() {
        showImageWithDirection(-1);
    }

    private void showImageWithDirection(int dir) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No images in folder: " + getSavePrefix(), "No Images", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int attempts = 0, size = imageFiles.size();
        while (attempts < size) {
            currentImageIndex = (currentImageIndex + dir + size) % size;
            currentImageFile = imageFiles.get(currentImageIndex);

            BufferedImage img = cache.get(currentImageFile);
            if (img == null) {
                try {
                    img = ImageIO.read(currentImageFile);
                    cache.put(currentImageFile, img);
                } catch (IOException e) {
                    attempts++;
                    continue;
                }
            }
            if (img == null) {
                attempts++;
                continue;
            }
            if (img.getWidth() < cropWidth || img.getHeight() < cropHeight) {
                skippedImagesCount++;
                updateSkippedLabel();
                setStatus("Image skipped (too small): " + currentImageFile.getName(), 1200);
                attempts++;
                continue;
            }

            originalImage = img;
            imagePanel.setImage(currentImageFile, originalImage);
            imagePanel.requestFocusInWindow();

            int nextIdx = (currentImageIndex + 1) % size;
            int prevIdx = (currentImageIndex - 1 + size) % size;
            cache.preload(imageFiles.get(nextIdx));
            cache.preload(imageFiles.get(prevIdx));
            return;
        }
        JOptionPane.showMessageDialog(mainFrame, "No valid images in folder: " + getSavePrefix() +
                "\nSkipped: " + skippedImagesCount + " images", "No Valid Images", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateSkippedLabel() {
        skippedImagesCount = Math.max(0, skippedImagesCount);
        skippedLabel.setText("Skipped: " + skippedImagesCount);
    }

    public void setStatus(String text, int clearAfterMs) {
        statusLabel.setText(text);
        if (statusClearTimer != null && statusClearTimer.isRunning()) statusClearTimer.stop();
        statusClearTimer = new Timer(clearAfterMs, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statusLabel.setText(" ");
            }
        });
        statusClearTimer.setRepeats(false);
        statusClearTimer.start();
    }

    public CropResult performCrop(Point mousePos) {
        ImagePanel.ScaledSnapshot snap = imagePanel.getScaledSnapshot();
        if (snap == null || originalImage == null || currentImageFile == null) return null;

        return cropEngine.performPrimaryCrop(
                mousePos,
                snap,
                originalImage,
                currentImageFile,
                sourceRootDir,
                saveDirectory,
                cropWidth,
                cropHeight,
                projectName,
                userName,
                getSavePrefix()
        );
    }


    public CropEngine.HierarchicalCropResult performHierarchicalCrop(
            Point mousePos, boolean doSecondary, boolean doTertiary, boolean renderSubImages) {

        ImagePanel.ScaledSnapshot snap = imagePanel.getScaledSnapshot();
        if (snap == null || originalImage == null || currentImageFile == null) return null;

        return cropEngine.performHierarchicalCrop(
                mousePos, snap, originalImage, currentImageFile,
                sourceRootDir, saveDirectory,
                cropWidth, cropHeight,
                projectName, userName, getSavePrefix(),
                doSecondary, doTertiary, renderSubImages
        );
    }

    public void exportMetadataJson() {
        mainFrame.exportMetadataJson();
    }

    public void addMetadataToQueue(CropMetadata meta) {
        synchronized (cropMetadataQueue) {
            cropMetadataQueue.add(meta);
        }
    }

    public List<CropMetadata> getCropMetadataQueue() {
        return cropMetadataQueue;
    }

    public void clearCropMetadataQueue() {
        synchronized (cropMetadataQueue) {
            cropMetadataQueue.clear();
        }
    }

    public void handleAutoExportToggle() {
        if (cbAutoExport.isSelected()) {
            Integer v = promptForThreshold(null, 100);
            if (v == null || v <= 0) {
                cbAutoExport.setSelected(false);
                return;
            }
            mainFrame.setAutoExportThreshold(v);
            mainFrame.setAutoExportEnabled(true);
            setStatus("Auto export enabled: every " + v + " crops", 2000);
        } else {
            mainFrame.setAutoExportEnabled(false);
            setStatus("Auto export disabled", 1400);
        }
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

    public static String nowUtcStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static String stampToIso(String stamp) {
        if (stamp == null || stamp.length() != 16) return stamp;
        return stamp.substring(0, 4) + "-" + stamp.substring(4, 6) + "-" + stamp.substring(6, 8) +
                "T" + stamp.substring(9, 11) + ":" + stamp.substring(11, 13) + ":" + stamp.substring(13, 15) + "Z";
    }

    public class ImagePanel extends JPanel {
        private File currentFile;
        private BufferedImage currentImage;

        private BufferedImage scaled;
        private int dispW, dispH;
        private int imgX, imgY;
        private Dimension lastSize;
        private Point mousePos;

        ImagePanel() {
            setLayout(null);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePos = e.getPoint();
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (currentImage != null) {
                        if (e.getClickCount() == 2) {
                        } else {
                            mainFrame.handleCropClick(e.getPoint());
                        }
                    }
                }
            });
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    invalidateScaled();
                    repaint();
                }
            });
        }

        void setImage(File file, BufferedImage img) {
            this.currentFile = file;
            this.currentImage = img;
            invalidateScaled();
            repaint();
        }

        void invalidateScaled() {
            scaled = null;
            lastSize = null;
        }

        ScaledSnapshot getScaledSnapshot() {
            ensureScaled();
            if (scaled == null) return null;
            return new ScaledSnapshot(imgX, imgY, dispW, dispH);
        }

        private void ensureScaled() {
            if (currentImage == null) {
                scaled = null;
                return;
            }
            Dimension sz = getSize();
            if (scaled != null && lastSize != null && lastSize.equals(sz)) return;

            int availableWidth = Math.max(1, getWidth() - 20);
            int availableHeight = Math.max(1, getHeight() - 20);
            double w = currentImage.getWidth(), h = currentImage.getHeight();
            double ratio = Math.min(Math.min(availableWidth / w, availableHeight / h), 1.0);

            dispW = Math.max(1, (int) Math.round(w * ratio));
            dispH = Math.max(1, (int) Math.round(h * ratio));

            BufferedImage scaledBuf = new BufferedImage(dispW, dispH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = scaledBuf.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(currentImage, 0, 0, dispW, dispH, null);
            g2d.dispose();

            scaled = scaledBuf;
            lastSize = sz;
            imgX = (getWidth() - dispW) / 2;
            imgY = (getHeight() - dispH) / 2;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.GRAY);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (currentImage == null) return;
            ensureScaled();
            if (scaled == null) return;
            g.drawImage(scaled, imgX, imgY, this);

            if (mousePos != null &&
                    mousePos.x >= imgX && mousePos.x < imgX + dispW &&
                    mousePos.y >= imgY && mousePos.y < imgY + dispH) {

                int relX = mousePos.x - imgX, relY = mousePos.y - imgY;
                double scaleX = (double) currentImage.getWidth() / dispW;
                double scaleY = (double) currentImage.getHeight() / dispH;
                int dispCropW = (int) Math.round(cropWidth / scaleX);
                int dispCropH = (int) Math.round(cropHeight / scaleY);

                int rectX = Math.max(0, Math.min(relX - dispCropW / 2, dispW - dispCropW));
                int rectY = Math.max(0, Math.min(relY - dispCropH / 2, dispH - dispCropH));

                int screenX = imgX + rectX, screenY = imgY + rectY;

                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setColor(new Color(200, 200, 200, 40));
                g2d.fillRect(screenX, screenY, dispCropW, dispCropH);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(1f));
                g2d.drawRect(screenX, screenY, dispCropW, dispCropH);
                g2d.dispose();
            }
        }

        class ScaledSnapshot {
            final int imgX, imgY, dispW, dispH;

            ScaledSnapshot(int x, int y, int w, int h) {
                imgX = x;
                imgY = y;
                dispW = w;
                dispH = h;
            }
        }
    }

    private class ImageCache {
        private final Map<String, BufferedImage> strong;
        private final Map<String, SoftReference<BufferedImage>> soft = new ConcurrentHashMap<String, SoftReference<BufferedImage>>();
        private final int strongCap;

        ImageCache(int strongCap) {
            this.strongCap = Math.max(1, strongCap);
            this.strong = new LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    boolean evict = size() > ImageCache.this.strongCap;
                    if (evict) soft.put(eldest.getKey(), new SoftReference<BufferedImage>(eldest.getValue()));
                    return evict;
                }
            };
        }

        BufferedImage get(File f) {
            String k = key(f);
            synchronized (strong) {
                BufferedImage v = strong.get(k);
                if (v != null) return v;
            }
            SoftReference<BufferedImage> ref = soft.get(k);
            BufferedImage img = (ref == null) ? null : ref.get();
            if (img != null) {
                put(f, img);
                return img;
            }
            try {
                img = ImageIO.read(f);
                put(f, img);
                return img;
            } catch (IOException e) {
                return null;
            }
        }

        void put(File f, BufferedImage img) {
            if (img == null) return;
            synchronized (strong) {
                strong.put(key(f), img);
            }
        }

        void preload(final File f) {
            if (f == null) return;
            final String k = key(f);
            synchronized (strong) {
                if (strong.containsKey(k)) return;
            }
            SoftReference<BufferedImage> ref = soft.get(k);
            if (ref != null && ref.get() != null) return;
            preloadExec.submit(new Runnable() {
                public void run() {
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) put(f, img);
                    } catch (IOException ignore) {
                    }
                }
            });
        }

        private String key(File f) {
            return f.getAbsolutePath() + "|" + f.lastModified() + "|" + f.length();
        }
    }

    private static class PillButton extends JButton {
        PillButton(String text, Icon icon, String tooltip) {
            super(text, icon);
            setToolTipText(tooltip);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(new Color(50, 50, 50));
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.CENTER);
            setIconTextGap(6);
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = UIManager.getColor("Panel.background");
            if (base == null) base = new Color(240, 240, 240);
            Color normal = lighten(base, 12);
            Color hover = lighten(base, 20);
            Color press = darken(normal, 8);
            Color outline = darken(base, 25);
            int w = getWidth(), h = getHeight();
            boolean pressed = getModel().isArmed() && getModel().isPressed();
            boolean over = getModel().isRollover();
            Color fill = pressed ? press : (over ? hover : normal);
            int r = Math.min(22, Math.max(12, h - 4));
            RoundRectangle2D rr = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, r, r);
            g2.setPaint(fill);
            g2.fill(rr);
            g2.setPaint(outline);
            g2.draw(rr);
            g2.dispose();
            super.paintComponent(g);
        }

        private static Color lighten(Color c, int d) {
            return new Color(Math.min(255, c.getRed() + d), Math.min(255, c.getGreen() + d), Math.min(255, c.getBlue() + d));
        }

        private static Color darken(Color c, int d) {
            return new Color(Math.max(0, c.getRed() - d), Math.max(0, c.getGreen() - d), Math.max(0, c.getBlue() - d));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            int w = d.width + 18;
            int h = Math.max(28, d.height + 6);
            return new Dimension(w, h);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

    }

    private static class VectorIcon implements Icon {
        enum Type {PREV_FOLDER, PREV_IMAGE, NEXT_IMAGE, NEXT_FOLDER}

        private final Type type;
        private final int size;
        private final Color color;

        VectorIcon(Type type, int size, Color color) {
            this.type = type;
            this.size = size;
            this.color = color;
        }

        public int getIconWidth() {
            return size;
        }

        public int getIconHeight() {
            return size;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(Math.max(2f, size / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (type) {
                case PREV_IMAGE:
                    drawChevron(g2, size, true, false);
                    break;
                case NEXT_IMAGE:
                    drawChevron(g2, size, false, false);
                    break;
                case PREV_FOLDER:
                    drawChevron(g2, size, true, true);
                    break;
                case NEXT_FOLDER:
                    drawChevron(g2, size, false, true);
                    break;
            }
            g2.dispose();
        }

        private void drawChevron(Graphics2D g2, int s, boolean left, boolean doubled) {
            float cx = s / 2f, cy = s / 2f, len = s * 0.40f;
            g2.setStroke(new BasicStroke(Math.max(2f, s / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float ch1 = new Path2D.Float();
            if (left) {
                ch1.moveTo(cx + len * 0.5f, cy - len);
                ch1.lineTo(cx - len * 0.5f, cy);
                ch1.lineTo(cx + len * 0.5f, cy + len);
            } else {
                ch1.moveTo(cx - len * 0.5f, cy - len);
                ch1.lineTo(cx + len * 0.5f, cy);
                ch1.lineTo(cx - len * 0.5f, cy + len);
            }
            g2.draw(ch1);
            if (doubled) {
                float gap = s * 0.22f;
                AffineTransform old = g2.getTransform();
                if (left) g2.translate(-gap, 0);
                else g2.translate(gap, 0);
                g2.draw(ch1);
                g2.setTransform(old);
            }
        }
    }

    public static class CropMetadata {
        public boolean isPrimary;

        public JSONArray cropOperations;

        String project, user, sourceDir, sinkDir, imageName, imagePath, annotation, savedAs;
        int imageWidth, imageHeight, cropX1, cropY1, cropX2, cropY2, cropWidth, cropHeight;
        double nx1, ny1, nx2, ny2;
        boolean designated;

        JSONObject toJsonObject() {
            JSONObject o = new JSONObject();
            o.put("project", project);
            o.put("user", user);
            o.put("sourceDir", sourceDir);
            o.put("sinkDir", sinkDir);
            o.put("imageName", imageName);
            o.put("imagePath", imagePath);
            o.put("imageWidth", imageWidth);
            o.put("imageHeight", imageHeight);
            o.put("cropX1", cropX1);
            o.put("cropY1", cropY1);
            o.put("cropX2", cropX2);
            o.put("cropY2", cropY2);
            o.put("cropWidth", cropWidth);
            o.put("cropHeight", cropHeight);
            o.put("annotation", annotation);
            o.put("savedAs", savedAs);
            o.put("nx1", nx1);
            o.put("ny1", ny1);
            o.put("nx2", nx2);
            o.put("ny2", ny2);
            o.put("designated", designated);
            if (cropOperations != null) {
                o.put("crop_operations", cropOperations);
            }
            return o;
        }
    }

    public static class CropResult {
        public final BufferedImage croppedImage;
        public final String filename;
        public final CropMetadata metadata;

        public CropResult(BufferedImage croppedImage, String filename, CropMetadata metadata) {
            this.croppedImage = croppedImage;
            this.filename = filename;
            this.metadata = metadata;
        }
    }

    private synchronized int nextSequentialNumber(String prefix, int w, int h) {
        String key = prefix + "|" + w + "x" + h;
        Integer cur = sequenceCounters.get(key);
        if (cur == null) {
            int seeded = scanMaxNumber(prefix, w, h);
            sequenceCounters.put(key, Integer.valueOf(seeded));
            cur = Integer.valueOf(seeded);
        }
        int next = cur.intValue() + 1;
        sequenceCounters.put(key, Integer.valueOf(next));
        return next;
    }

    private int scanMaxNumber(final String prefix, int w, int h) {
        if (saveDirectory == null || !saveDirectory.isDirectory()) return 0;
        String res = w + "x" + h;
        final String prefixRes = prefix + "_" + res + "_";
        File[] files = saveDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File d, String name) {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.startsWith(prefixRes.toLowerCase(Locale.ROOT)) && lower.endsWith(".png");
            }
        });
        int max = 0;
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                try {
                    String base = files[i].getName();
                    int us = base.lastIndexOf('_');
                    int dot = base.lastIndexOf('.');
                    if (us >= 0 && dot > us) {
                        String numStr = base.substring(us + 1, dot).replaceAll("\\D", "");
                        if (!numStr.isEmpty()) {
                            int num = Integer.parseInt(numStr);
                            if (num > max) max = num;
                        }
                    }
                } catch (Exception ignore) {
                }
            }
        }
        return max;
    }
}
