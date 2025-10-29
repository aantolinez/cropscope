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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class ImagePreviewWindow extends JFrame {
    private DicomFileManager dicomFileManager;
    private List<File> imageFiles;
    private int currentIndex;

    private JLabel imageLabel;
    private JLabel infoLabel;
    private JButton prevButton;
    private JButton nextButton;
    private JButton firstButton;
    private JButton lastButton;
    private JButton exportButton;
    private JSlider zoomSlider;
    private JCheckBox fitToWindowCheckBox;

    private double zoomFactor = 1.0;
    private boolean fitToWindow = true;
    private BufferedImage currentImage;

    public ImagePreviewWindow(JFrame parent, DicomFileManager dicomFileManager, List<File> imageFiles, int startIndex) {
        super("Image Preview");
        this.dicomFileManager = dicomFileManager;
        this.imageFiles = imageFiles;
        this.currentIndex = startIndex;

        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        loadImage(currentIndex);
        setSize(800, 600);
        setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(400, 300));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                currentImage = null;
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (fitToWindow) {
                    updateImageDisplay();
                }
            }
        });
    }

    private void initializeComponents() {
        imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setBorder(BorderFactory.createEtchedBorder());
        imageLabel.setBackground(Color.BLACK);
        imageLabel.setOpaque(true);
        infoLabel = new JLabel(" ", SwingConstants.CENTER);
        infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        prevButton = new JButton("Previous");
        nextButton = new JButton("Next");
        firstButton = new JButton("First");
        lastButton = new JButton("Last");
        exportButton = new JButton("Export");
        zoomSlider = new JSlider(JSlider.HORIZONTAL, 25, 400, 100);
        zoomSlider.setMajorTickSpacing(100);
        zoomSlider.setMinorTickSpacing(25);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setPaintLabels(true);

        fitToWindowCheckBox = new JCheckBox("Fit to Window");
        fitToWindowCheckBox.setSelected(true);
        boolean hasMultipleImages = imageFiles.size() > 1;
        prevButton.setEnabled(hasMultipleImages);
        nextButton.setEnabled(hasMultipleImages);
        firstButton.setEnabled(hasMultipleImages);
        lastButton.setEnabled(hasMultipleImages);
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        JScrollPane imageScrollPane = new JScrollPane(imageLabel);
        mainPanel.add(imageScrollPane, BorderLayout.CENTER);
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(infoLabel, BorderLayout.CENTER);
        mainPanel.add(infoPanel, BorderLayout.SOUTH);
        JPanel controlPanel = new JPanel(new BorderLayout());
        JPanel navButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        navButtonPanel.add(firstButton);
        navButtonPanel.add(prevButton);
        navButtonPanel.add(nextButton);
        navButtonPanel.add(lastButton);
        navButtonPanel.add(exportButton);
        JPanel zoomPanel = new JPanel(new BorderLayout());
        zoomPanel.add(new JLabel("Zoom:"), BorderLayout.WEST);
        zoomPanel.add(zoomSlider, BorderLayout.CENTER);
        zoomPanel.add(fitToWindowCheckBox, BorderLayout.EAST);
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(navButtonPanel, BorderLayout.NORTH);
        topPanel.add(zoomPanel, BorderLayout.SOUTH);

        controlPanel.add(topPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        add(mainPanel);
    }

    private void setupEventHandlers() {
        firstButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentIndex > 0) {
                    currentIndex = 0;
                    loadImage(currentIndex);
                }
            }
        });
        prevButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentIndex > 0) {
                    currentIndex--;
                    loadImage(currentIndex);
                }
            }
        });
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentIndex < imageFiles.size() - 1) {
                    currentIndex++;
                    loadImage(currentIndex);
                }
            }
        });
        lastButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentIndex < imageFiles.size() - 1) {
                    currentIndex = imageFiles.size() - 1;
                    loadImage(currentIndex);
                }
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentImage != null) {
                    showExportDialog();
                }
            }
        });
        zoomSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (!fitToWindowCheckBox.isSelected()) {
                    zoomFactor = zoomSlider.getValue() / 100.0;
                    updateImageDisplay();
                }
            }
        });
        fitToWindowCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fitToWindow = fitToWindowCheckBox.isSelected();
                zoomSlider.setEnabled(!fitToWindow);
                updateImageDisplay();
            }
        });
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prevImage");
        actionMap.put("prevImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentIndex > 0) {
                    currentIndex--;
                    loadImage(currentIndex);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "nextImage");
        actionMap.put("nextImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentIndex < imageFiles.size() - 1) {
                    currentIndex++;
                    loadImage(currentIndex);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "firstImage");
        actionMap.put("firstImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentIndex > 0) {
                    currentIndex = 0;
                    loadImage(currentIndex);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "lastImage");
        actionMap.put("lastImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentIndex < imageFiles.size() - 1) {
                    currentIndex = imageFiles.size() - 1;
                    loadImage(currentIndex);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "zoomIn");
        actionMap.put("zoomIn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!fitToWindowCheckBox.isSelected()) {
                    int newValue = Math.min(400, zoomSlider.getValue() + 25);
                    zoomSlider.setValue(newValue);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "zoomOut");
        actionMap.put("zoomOut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!fitToWindowCheckBox.isSelected()) {
                    int newValue = Math.max(25, zoomSlider.getValue() - 25);
                    zoomSlider.setValue(newValue);
                }
            }
        });
    }

    private void loadImage(int index) {
        if (index < 0 || index >= imageFiles.size()) {
            return;
        }
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return dicomFileManager.loadImage(imageFiles.get(index));
            }

            @Override
            protected void done() {
                try {
                    currentImage = get();
                    if (currentImage != null) {
                        updateImageDisplay();
                        updateInfoLabel();
                        prevButton.setEnabled(currentIndex > 0);
                        nextButton.setEnabled(currentIndex < imageFiles.size() - 1);
                        firstButton.setEnabled(currentIndex > 0);
                        lastButton.setEnabled(currentIndex < imageFiles.size() - 1);
                    } else {
                        imageLabel.setIcon(null);
                        infoLabel.setText("Failed to load image: " + imageFiles.get(currentIndex).getName());
                    }
                } catch (Exception e) {
                    imageLabel.setIcon(null);
                    infoLabel.setText("Error loading image: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void updateImageDisplay() {
        if (currentImage == null) {
            imageLabel.setIcon(null);
            return;
        }

        if (fitToWindow) {
            Dimension labelSize = imageLabel.getSize();
            if (labelSize.width > 0 && labelSize.height > 0) {
                ImageIcon icon = new ImageIcon(currentImage);
                Image scaledImage = icon.getImage().getScaledInstance(
                        labelSize.width - 10,
                        labelSize.height - 10,
                        Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(scaledImage));
            } else {
                imageLabel.setIcon(new ImageIcon(currentImage));
            }
        } else {
            int width = (int) (currentImage.getWidth() * zoomFactor);
            int height = (int) (currentImage.getHeight() * zoomFactor);
            Image scaledImage = currentImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaledImage));
        }

        imageLabel.revalidate();
        imageLabel.repaint();
    }

    private void updateInfoLabel() {
        if (currentImage != null) {
            String info = String.format("%d/%d: %s (%dx%d)",
                    currentIndex + 1,
                    imageFiles.size(),
                    imageFiles.get(currentIndex).getName(),
                    currentImage.getWidth(),
                    currentImage.getHeight());

            if (!fitToWindow) {
                info += String.format(" - Zoom: %.0f%%", zoomFactor * 100);
            }

            infoLabel.setText(info);
        } else {
            infoLabel.setText(" ");
        }
    }

    private void showExportDialog() {
        if (currentImage == null) {
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Image");
        fileChooser.setSelectedFile(new File(imageFiles.get(currentIndex).getName()));
        FileNameExtensionFilter pngFilter =
                new FileNameExtensionFilter("PNG Images", "png");
        FileNameExtensionFilter jpgFilter =
                new FileNameExtensionFilter("JPEG Images", "jpg", "jpeg");
        FileNameExtensionFilter bmpFilter =
                new FileNameExtensionFilter("BMP Images", "bmp");

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
                        JOptionPane.showMessageDialog(ImagePreviewWindow.this,
                                "Image exported successfully to:\n" + finalOutputFile.getAbsolutePath(),
                                "Export Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(ImagePreviewWindow.this,
                                "Error exporting image: " + e.getMessage(),
                                "Export Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }
}