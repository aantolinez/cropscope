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

package com.cropscope.dataaugmentation.gui;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.Clipboard;
import java.awt.Toolkit;
import com.cropscope.dataaugmentation.rotation.ImageRotationEngine;
import com.cropscope.dataaugmentation.rotation.CropMetadataExporter;
public class ImageRotationGui extends JFrame {
    private BufferedImage originalImage;
    private BufferedImage currentRotatedImage;
    private BufferedImage currentDisplayImage;
    private JLabel imageLabel;
    private JSlider rotationSlider;
    private JLabel angleLabel;
    private JButton cropButton;
    private ImageRotationEngine rotationEngine;
    private boolean showGrid = true;
    private int cropWidth = 256;
    private int cropHeight = 256;
    private JCheckBoxMenuItem showGridMenuItem;
    private String imagePath = null;

    public ImageRotationGui() {
        setTitle("Image Rotator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openImageMenuItem = new JMenuItem("Open Image");
        JMenuItem saveImageMenuItem = new JMenuItem("Save Image");
        JMenuItem saveWithGridMenuItem = new JMenuItem("Save with Grid");
        JMenuItem exitMenuItem = new JMenuItem("Exit");
        openImageMenuItem.addActionListener(e -> openImage());
        saveImageMenuItem.addActionListener(e -> saveImage());
        saveWithGridMenuItem.addActionListener(e -> saveImageWithGrid());
        exitMenuItem.addActionListener(e -> exitApplication());
        fileMenu.add(openImageMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(saveImageMenuItem);
        fileMenu.add(saveWithGridMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);
        JMenu settingsMenu = new JMenu("Settings");
        showGridMenuItem = new JCheckBoxMenuItem("Show Grid", true);
        showGridMenuItem.addActionListener(e -> {
            showGrid = showGridMenuItem.isSelected();
            updateRotation();
        });
        settingsMenu.add(showGridMenuItem);
        JMenuItem setCropDimensionsMenuItem = new JMenuItem("Set Crop Dimensions");
        setCropDimensionsMenuItem.addActionListener(e -> showCropDimensionsDialog());
        settingsMenu.add(setCropDimensionsMenuItem);
        menuBar.add(settingsMenu);
        setJMenuBar(menuBar);
        rotationEngine = new ImageRotationEngine(cropWidth, cropHeight);
        JPanel topPanel = new JPanel();
        cropButton = new JButton("Crop Image");
        cropButton.setEnabled(false);
        topPanel.add(cropButton);
        add(topPanel, BorderLayout.NORTH);
        imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setBorder(BorderFactory.createEtchedBorder());
        add(new JScrollPane(imageLabel), BorderLayout.CENTER);
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showCopyToClipboardMenu(e.getX(), e.getY());
                }
            }
        });
        JPanel bottomPanel = new JPanel(new BorderLayout());
        rotationSlider = new JSlider(0, 9, 0);
        rotationSlider.setMajorTickSpacing(1);
        rotationSlider.setPaintTicks(true);
        rotationSlider.setPaintLabels(true);
        Dictionary<Integer, JComponent> labels = new Hashtable<>();
        labels.put(0, new JLabel("0°"));
        labels.put(1, new JLabel("10°"));
        labels.put(2, new JLabel("20°"));
        labels.put(3, new JLabel("30°"));
        labels.put(4, new JLabel("40°"));
        labels.put(5, new JLabel("50°"));
        labels.put(6, new JLabel("60°"));
        labels.put(7, new JLabel("70°"));
        labels.put(8, new JLabel("80°"));
        labels.put(9, new JLabel("90°"));
        rotationSlider.setLabelTable(labels);
        angleLabel = new JLabel("0°");
        angleLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        bottomPanel.add(rotationSlider, BorderLayout.CENTER);
        bottomPanel.add(angleLabel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
        cropButton.addActionListener(e -> cropImage());
        rotationSlider.addChangeListener(e -> updateRotation());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });
    }
    private void showCropDimensionsDialog() {
        JDialog dialog = new JDialog(this, "Set Crop Dimensions", true);
        dialog.setLayout(new BorderLayout(10, 10));
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 8, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Width:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1;
        JTextField widthField = new JTextField(String.valueOf(cropWidth), 15);
        formPanel.add(widthField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("Height:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        JTextField heightField = new JTextField(String.valueOf(cropHeight), 15);
        formPanel.add(heightField, gbc);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(formPanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setContentPane(mainPanel);
        okButton.addActionListener(e -> {
            try {
                int newWidth = Integer.parseInt(widthField.getText());
                int newHeight = Integer.parseInt(heightField.getText());
                if (newWidth > 0 && newHeight > 0) {
                    cropWidth = newWidth;
                    cropHeight = newHeight;
                    rotationEngine = new ImageRotationEngine(cropWidth, cropHeight);
                    updateRotation();
                    JOptionPane.showMessageDialog(dialog,
                            "Crop dimensions updated to: " + cropWidth + "x" + cropHeight,
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(dialog,
                            "Dimensions must be positive integers",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Please enter valid integers",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancelButton.addActionListener(e -> dialog.dispose());
        dialog.pack();
        dialog.setMinimumSize(new Dimension(320, 130));
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }
    private void exitApplication() {
        int response = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit the application?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (response == JOptionPane.YES_OPTION) {
            if (originalImage != null) {
                originalImage.flush();
            }
            if (currentRotatedImage != null) {
                currentRotatedImage.flush();
            }
            if (currentDisplayImage != null) {
                currentDisplayImage.flush();
            }
            imageLabel.setIcon(null);
            imageLabel.setText("");
            dispose();
            System.exit(0);
        }
    }
    private void showCopyToClipboardMenu(int x, int y) {
        if (currentRotatedImage == null) return;
        JPopupMenu popup = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy Image");
        JMenuItem copyWithGridItem = new JMenuItem("Copy with Grid");
        copyItem.addActionListener(e -> copyImageToClipboard(false));
        copyWithGridItem.addActionListener(e -> copyImageToClipboard(true));
        popup.add(copyItem);
        popup.add(copyWithGridItem);
        popup.show(imageLabel, x, y);
    }
    private void copyImageToClipboard(boolean withGrid) {
        if (currentRotatedImage == null) {
            JOptionPane.showMessageDialog(this, "No image to copy", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        BufferedImage imageToCopy;
        if (withGrid) {
            imageToCopy = createImageWithGrid(
                    currentRotatedImage,
                    rotationSlider.getValue() * 10,
                    originalImage.getWidth(),
                    originalImage.getHeight()
            );
        } else {
            imageToCopy = new BufferedImage(
                    currentRotatedImage.getWidth(),
                    currentRotatedImage.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = imageToCopy.createGraphics();
            g2d.drawImage(currentRotatedImage, 0, 0, null);
            g2d.dispose();
        }
        TransferableImage transferable = new TransferableImage(imageToCopy);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(transferable, null);
        JOptionPane.showMessageDialog(this,
                "Image " + (withGrid ? "with grid" : "") + " copied to clipboard!",
                "Success", JOptionPane.INFORMATION_MESSAGE);
    }
    private BufferedImage createImageWithGrid(BufferedImage rotatedImage, int angle, int originalWidth, int originalHeight) {
        BufferedImage imageWithGrid = new BufferedImage(
                rotatedImage.getWidth(),
                rotatedImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = imageWithGrid.createGraphics();
        g2d.drawImage(rotatedImage, 0, 0, null);
        BufferedImage gridImage = rotationEngine.drawCropGridWithOriginalDimensions(
                rotatedImage, angle, originalWidth, originalHeight);
        g2d.drawImage(gridImage, 0, 0, null);
        g2d.dispose();
        return imageWithGrid;
    }
    private void openImage() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Image Files", "jpg", "jpeg", "png", "gif", "bmp");
        fileChooser.setFileFilter(filter);
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                if (originalImage != null) {
                    originalImage.flush();
                }
                if (currentRotatedImage != null) {
                    currentRotatedImage.flush();
                }
                if (currentDisplayImage != null) {
                    currentDisplayImage.flush();
                }
                originalImage = ImageIO.read(file);
                imagePath = file.getAbsolutePath();
                rotationSlider.setValue(0);
                updateRotation();
                cropButton.setEnabled(true);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error loading image: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    private void updateRotation() {
        if (originalImage == null) return;
        int position = rotationSlider.getValue();
        int angle = position * 10;
        angleLabel.setText(angle + "°");
        currentRotatedImage = rotateImage(originalImage, -angle);
        if (showGrid) {
            currentDisplayImage = rotationEngine.drawCropGridWithOriginalDimensions(
                    currentRotatedImage, angle, originalImage.getWidth(), originalImage.getHeight());
        } else {
            currentDisplayImage = new BufferedImage(
                    currentRotatedImage.getWidth(),
                    currentRotatedImage.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = currentDisplayImage.createGraphics();
            g2d.drawImage(currentRotatedImage, 0, 0, null);
            g2d.dispose();
        }
        Image scaledImage = scaleToFit(currentDisplayImage, imageLabel.getWidth(), imageLabel.getHeight());
        imageLabel.setText("");
        imageLabel.setIcon(new ImageIcon(scaledImage));
    }
    private BufferedImage rotateImage(BufferedImage image, double angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        int width = image.getWidth();
        int height = image.getHeight();
        double sin = Math.abs(Math.sin(angle));
        double cos = Math.abs(Math.cos(angle));
        int newWidth = (int) (width * cos + height * sin);
        int newHeight = (int) (width * sin + height * cos);
        BufferedImage rotated = new BufferedImage(
                newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.translate(newWidth / 2.0, newHeight / 2.0);
        g2d.rotate(angle);
        g2d.drawImage(image, -width / 2, -height / 2, null);
        g2d.dispose();
        return rotated;
    }
    private void cropImage() {
        if (currentRotatedImage == null) {
            JOptionPane.showMessageDialog(this, "No image to crop", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select destination directory for cropped images");
        int returnValue = chooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File destinationDir = chooser.getSelectedFile();
            JDialog metadataDialog = new JDialog(this, "Export Metadata", true);
            metadataDialog.setLayout(new BorderLayout(10, 10));
            metadataDialog.setSize(400, 200);
            metadataDialog.setLocationRelativeTo(this);
            JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            JPanel radioPanel = new JPanel(new GridLayout(2, 1, 0, 5));
            JRadioButton currentAngleRadio = new JRadioButton("Current angle only", true);
            JRadioButton allAnglesRadio = new JRadioButton("All angles (0°, 10°, ..., 90°)");
            ButtonGroup radioGroup = new ButtonGroup();
            radioGroup.add(currentAngleRadio);
            radioGroup.add(allAnglesRadio);
            radioPanel.add(currentAngleRadio);
            radioPanel.add(allAnglesRadio);
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            JButton exportButton = new JButton("Export Metadata");
            JButton cancelButton = new JButton("Cancel");
            buttonPanel.add(exportButton);
            buttonPanel.add(cancelButton);
            mainPanel.add(radioPanel, BorderLayout.CENTER);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);
            metadataDialog.setContentPane(mainPanel);
            exportButton.addActionListener(exportEvent -> {
                boolean includeAllAngles = allAnglesRadio.isSelected();
                try {
                    int angle = rotationSlider.getValue() * 10;
                    BufferedImage transparentImage = rotationEngine.createTransparentImage(currentRotatedImage);
                    rotationEngine.saveCrops(
                            destinationDir.getAbsolutePath(),
                            transparentImage,
                            angle,
                            originalImage.getWidth(),
                            originalImage.getHeight()
                    );
                    try {
                        String path = imagePath != null ? imagePath : "Unknown";
                        CropMetadataExporter exporter = new CropMetadataExporter(
                                originalImage.getWidth(),
                                originalImage.getHeight(),
                                rotationEngine.getCropWidth(),
                                rotationEngine.getCropHeight(),
                                includeAllAngles,
                                path
                        );
                        if (!includeAllAngles) {
                            exporter.setCurrentAngle(angle);
                        }
                        String metadataPath = destinationDir.getAbsolutePath() + File.separator + "crop_metadata.json";
                        exporter.exportToJson(metadataPath);
                        System.out.println("Metadata exported to: " + metadataPath);
                        System.out.println("Total crop regions: " + exporter.getTotalCropRegions());
                        System.out.println("Angles included: " + exporter.getAngles());
                        JOptionPane.showMessageDialog(metadataDialog,
                                "Metadata exported successfully!",
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception metadataEx) {
                        System.err.println("Error exporting metadata: " + metadataEx.getMessage());
                        JOptionPane.showMessageDialog(metadataDialog,
                                "Error exporting metadata: " + metadataEx.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    metadataDialog.dispose();
                } catch (IOException cropEx) {
                    JOptionPane.showMessageDialog(metadataDialog,
                            "Error during cropping: " + cropEx.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            cancelButton.addActionListener(cancelEvent -> {
                try {
                    int angle = rotationSlider.getValue() * 10;
                    BufferedImage transparentImage = rotationEngine.createTransparentImage(currentRotatedImage);
                    rotationEngine.saveCrops(
                            destinationDir.getAbsolutePath(),
                            transparentImage,
                            angle,
                            originalImage.getWidth(),
                            originalImage.getHeight()
                    );
                    JOptionPane.showMessageDialog(this,
                            "Cropping completed successfully!",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    metadataDialog.dispose();
                } catch (IOException cancelEx) {
                    JOptionPane.showMessageDialog(this,
                            "Error during cropping: " + cancelEx.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    metadataDialog.dispose();
                }
            });
            metadataDialog.setVisible(true);
        }
    }
    private void saveImage() {
        if (currentRotatedImage == null) {
            JOptionPane.showMessageDialog(this, "No image to save", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Image");
        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG files (*.png)", "png");
        FileNameExtensionFilter jpgFilter = new FileNameExtensionFilter("JPEG files (*.jpg)", "jpg");
        FileNameExtensionFilter bmpFilter = new FileNameExtensionFilter("BMP files (*.bmp)", "bmp");
        fileChooser.addChoosableFileFilter(pngFilter);
        fileChooser.addChoosableFileFilter(jpgFilter);
        fileChooser.addChoosableFileFilter(bmpFilter);
        fileChooser.setFileFilter(pngFilter);
        int returnValue = fileChooser.showSaveDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String format = null;
            if (fileChooser.getFileFilter() == pngFilter) {
                format = "png";
            } else if (fileChooser.getFileFilter() == jpgFilter) {
                format = "jpg";
            } else if (fileChooser.getFileFilter() == bmpFilter) {
                format = "bmp";
            }
            if (format == null) {
                format = "png";
            }
            if (!file.getName().toLowerCase().endsWith("." + format)) {
                file = new File(file.getParent(), file.getName() + "." + format);
            }
            try {
                ImageIO.write(currentRotatedImage, format, file);
                JOptionPane.showMessageDialog(this,
                        "Image saved successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Error saving image: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    private void saveImageWithGrid() {
        if (currentRotatedImage == null) {
            JOptionPane.showMessageDialog(this, "No image to save", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Image with Grid");
        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG files (*.png)", "png");
        FileNameExtensionFilter jpgFilter = new FileNameExtensionFilter("JPEG files (*.jpg)", "jpg");
        FileNameExtensionFilter bmpFilter = new FileNameExtensionFilter("BMP files (*.bmp)", "bmp");
        fileChooser.addChoosableFileFilter(pngFilter);
        fileChooser.addChoosableFileFilter(jpgFilter);
        fileChooser.addChoosableFileFilter(bmpFilter);
        fileChooser.setFileFilter(pngFilter);
        int returnValue = fileChooser.showSaveDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String format = null;
            if (fileChooser.getFileFilter() == pngFilter) {
                format = "png";
            } else if (fileChooser.getFileFilter() == jpgFilter) {
                format = "jpg";
            } else if (fileChooser.getFileFilter() == bmpFilter) {
                format = "bmp";
            }
            if (format == null) {
                format = "png";
            }
            if (!file.getName().toLowerCase().endsWith("." + format)) {
                file = new File(file.getParent(), file.getName() + "." + format);
            }
            try {
                BufferedImage imageWithGrid = createImageWithGrid(
                        currentRotatedImage,
                        rotationSlider.getValue() * 10,
                        originalImage.getWidth(),
                        originalImage.getHeight()
                );
                ImageIO.write(imageWithGrid, format, file);
                JOptionPane.showMessageDialog(this,
                        "Image with grid saved successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Error saving image: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    private Image scaleToFit(BufferedImage image, int maxWidth, int maxHeight) {
        if (image == null) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        double ratio = Math.min(
                maxWidth / (double) width,
                maxHeight / (double) height
        );
        if (ratio > 1) ratio = 1;
        int newWidth = (int) (width * ratio);
        int newHeight = (int) (height * ratio);
        return image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    }

    private class TransferableImage implements Transferable {
        private final Image image;

        public TransferableImage(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (DataFlavor.imageFlavor.equals(flavor)) {
                return image;
            }
            throw new UnsupportedOperationException("Not supported");
        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ImageRotationGui().setVisible(true);
        });
    }
}