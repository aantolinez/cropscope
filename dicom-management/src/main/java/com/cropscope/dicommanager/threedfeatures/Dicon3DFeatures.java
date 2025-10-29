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

package com.cropscope.dicommanager.threedfeatures;

import com.cropscope.dicommanager.dicomfilemanagement.DicomFileManager;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public class Dicon3DFeatures {
    public class VolumeRenderer {
        private List<File> imageFiles;
        private VolumeViewPanel viewPanel;
        private DicomFileManager dicomFileManager;
        private int renderingTechnique = 0;
        private int rotation = 0;
        private int elevation = 0;
        private boolean interpolationEnabled = true;
        private TransferFunction transferFunction = new TransferFunction();
        private int cacheSizeBytes = 256 * 1024 * 1024;
        private Map<Integer, BufferedImage> imageCache = new LinkedHashMap<Integer, BufferedImage>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest) {
                return size() > cacheSizeBytes / (1024 * 1024);
            }
        };
        private ExecutorService executorService = Executors.newFixedThreadPool(4);
        private List<Future<BufferedImage>> loadingFutures = new ArrayList<>();

        public VolumeRenderer(List<File> imageFiles, VolumeViewPanel viewPanel, DicomFileManager dicomFileManager) {
            this.imageFiles = imageFiles;
            this.viewPanel = viewPanel;
            this.dicomFileManager = dicomFileManager;
            viewPanel.setVolumeRenderer(this);
        }

        public void loadImages() {
            imageFiles.sort((f1, f2) -> {
                try {
                    double pos1 = dicomFileManager.getSlicePosition(f1);
                    double pos2 = dicomFileManager.getSlicePosition(f2);
                    return Double.compare(pos1, pos2);
                } catch (Exception e) {
                    return f1.getName().compareTo(f2.getName());
                }
            });
            int preloadCount = Math.min(5, imageFiles.size());
            for (int i = 0; i < preloadCount; i++) {
                final int index = i;
                loadingFutures.add(executorService.submit(() -> loadImage(index)));
            }

            for (int i = imageFiles.size() - preloadCount; i < imageFiles.size(); i++) {
                final int index = i;
                loadingFutures.add(executorService.submit(() -> loadImage(index)));
            }
        }

        private BufferedImage loadImage(int index) {
            if (imageCache.containsKey(index)) {
                return imageCache.get(index);
            }

            try {
                File file = imageFiles.get(index);
                BufferedImage image = dicomFileManager.loadImage(file);
                if (image != null) {
                    imageCache.put(index, image);
                }

                return image;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        public void render(Graphics g) {
            if (imageFiles.isEmpty()) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, g.getClipBounds().width, g.getClipBounds().height);
                g.setColor(Color.WHITE);
                g.drawString("Loading 3D view...", 20, 20);
                return;
            }
            int width = g.getClipBounds().width;
            int height = g.getClipBounds().height;
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
            double rotRad = Math.toRadians(rotation);
            double elevRad = Math.toRadians(elevation);
            int centerX = width / 2;
            int centerY = height / 2;
            BufferedImage sampleImage = loadImage(0);
            if (sampleImage == null) {
                g.setColor(Color.WHITE);
                g.drawString("Error loading images", 20, 20);
                return;
            }

            int imageWidth = sampleImage.getWidth();
            int imageHeight = sampleImage.getHeight();
            int depth = imageFiles.size();
            double scale = Math.min(width, height) * 0.8 / Math.max(imageWidth, Math.max(imageHeight, depth));
            switch (renderingTechnique) {
                case 0:
                    renderSimpleProjection(g, centerX, centerY, rotRad, elevRad, scale, imageWidth, imageHeight, depth);
                    break;
                case 1:
                    renderMIP(g, centerX, centerY, rotRad, elevRad, scale, imageWidth, imageHeight, depth);
                    break;
                case 2:
                    renderAIP(g, centerX, centerY, rotRad, elevRad, scale, imageWidth, imageHeight, depth);
                    break;
                case 3:
                    renderRayCasting(g, centerX, centerY, rotRad, elevRad, scale, imageWidth, imageHeight, depth);
                    break;
            }
            g.setColor(Color.WHITE);
            g.drawString("Rotation: " + rotation + "°", 10, 20);
            g.drawString("Elevation: " + elevation + "°", 10, 40);
            g.drawString("Slices: " + depth, 10, 60);
            g.drawString("Technique: " + getTechniqueName(), 10, 80);
        }

        private String getTechniqueName() {
            switch (renderingTechnique) {
                case 0:
                    return "Simple Projection";
                case 1:
                    return "Maximum Intensity Projection (MIP)";
                case 2:
                    return "Average Intensity Projection (AIP)";
                case 3:
                    return "Ray Casting";
                default:
                    return "Unknown";
            }
        }

        private void renderSimpleProjection(Graphics g, int centerX, int centerY,
                                            double rotRad, double elevRad, double scale,
                                            int imageWidth, int imageHeight, int depth) {
            for (int z = 0; z < depth; z++) {
                BufferedImage slice = loadImage(z);
                if (slice == null) continue;
                double zOffset = (z - depth / 2.0) * scale;
                double xOffset = zOffset * Math.sin(rotRad) * Math.cos(elevRad);
                double yOffset = zOffset * Math.sin(elevRad);
                int x = (int) (centerX + xOffset - imageWidth * scale / 2);
                int y = (int) (centerY + yOffset - imageHeight * scale / 2);
                float alpha = 0.7f * (1.0f - (float) z / depth);
                BufferedImage transparentSlice = new BufferedImage(
                        imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);

                Graphics2D g2d = transparentSlice.createGraphics();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2d.drawImage(slice, 0, 0, null);
                g2d.dispose();
                applyTransferFunction(transparentSlice);
                g.drawImage(transparentSlice, x, y,
                        (int) (imageWidth * scale),
                        (int) (imageHeight * scale), null);
            }
        }

        private void renderMIP(Graphics g, int centerX, int centerY,
                               double rotRad, double elevRad, double scale,
                               int imageWidth, int imageHeight, int depth) {
            BufferedImage mipImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            int[] maxR = new int[imageWidth * imageHeight];
            int[] maxG = new int[imageWidth * imageHeight];
            int[] maxB = new int[imageWidth * imageHeight];
            Arrays.fill(maxR, 0);
            Arrays.fill(maxG, 0);
            Arrays.fill(maxB, 0);
            for (int z = 0; z < depth; z++) {
                BufferedImage slice = loadImage(z);
                if (slice == null) continue;
                int[] pixels = new int[imageWidth * imageHeight];
                slice.getRaster().getPixels(0, 0, imageWidth, imageHeight, pixels);
                for (int i = 0; i < pixels.length; i++) {
                    int idx = i * 3;
                    if (idx + 2 < pixels.length) {
                        int r = pixels[idx];
                        int greenVal = pixels[idx + 1];
                        int b = pixels[idx + 2];

                        if (r > maxR[i]) maxR[i] = r;
                        if (greenVal > maxG[i]) maxG[i] = greenVal;
                        if (b > maxB[i]) maxB[i] = b;
                    }
                }
            }
            for (int y = 0; y < imageHeight; y++) {
                for (int x = 0; x < imageWidth; x++) {
                    int idx = y * imageWidth + x;
                    int rgb = (maxR[idx] << 16) | (maxG[idx] << 8) | maxB[idx];
                    mipImage.setRGB(x, y, rgb);
                }
            }
            applyTransferFunction(mipImage);
            double xOffset = (depth / 2.0) * scale * Math.sin(rotRad) * Math.cos(elevRad);
            double yOffset = (depth / 2.0) * scale * Math.sin(elevRad);

            int x = (int) (centerX + xOffset - imageWidth * scale / 2);
            int y = (int) (centerY + yOffset - imageHeight * scale / 2);
            g.drawImage(mipImage, x, y,
                    (int) (imageWidth * scale),
                    (int) (imageHeight * scale), null);
        }

        private void renderAIP(Graphics g, int centerX, int centerY,
                               double rotRad, double elevRad, double scale,
                               int imageWidth, int imageHeight, int depth) {
            BufferedImage aipImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            int[] sumR = new int[imageWidth * imageHeight];
            int[] sumG = new int[imageWidth * imageHeight];
            int[] sumB = new int[imageWidth * imageHeight];
            Arrays.fill(sumR, 0);
            Arrays.fill(sumG, 0);
            Arrays.fill(sumB, 0);
            for (int z = 0; z < depth; z++) {
                BufferedImage slice = loadImage(z);
                if (slice == null) continue;
                int[] pixels = new int[imageWidth * imageHeight];
                slice.getRaster().getPixels(0, 0, imageWidth, imageHeight, pixels);
                for (int i = 0; i < pixels.length; i++) {
                    int idx = i * 3;
                    if (idx + 2 < pixels.length) {
                        sumR[i] += pixels[idx];
                        sumG[i] += pixels[idx + 1];
                        sumB[i] += pixels[idx + 2];
                    }
                }
            }
            for (int y = 0; y < imageHeight; y++) {
                for (int x = 0; x < imageWidth; x++) {
                    int idx = y * imageWidth + x;
                    int r = sumR[idx] / depth;
                    int greenVal = sumG[idx] / depth;
                    int b = sumB[idx] / depth;
                    int rgb = (r << 16) | (greenVal << 8) | b;
                    aipImage.setRGB(x, y, rgb);
                }
            }
            applyTransferFunction(aipImage);
            double xOffset = (depth / 2.0) * scale * Math.sin(rotRad) * Math.cos(elevRad);
            double yOffset = (depth / 2.0) * scale * Math.sin(elevRad);

            int x = (int) (centerX + xOffset - imageWidth * scale / 2);
            int y = (int) (centerY + yOffset - imageHeight * scale / 2);
            g.drawImage(aipImage, x, y,
                    (int) (imageWidth * scale),
                    (int) (imageHeight * scale), null);
        }

        private void renderRayCasting(Graphics g, int centerX, int centerY,
                                      double rotRad, double elevRad, double scale,
                                      int imageWidth, int imageHeight, int depth) {
            BufferedImage rayImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            double[] viewDir = {
                    Math.sin(rotRad) * Math.cos(elevRad),
                    Math.sin(elevRad),
                    Math.cos(rotRad) * Math.cos(elevRad)
            };
            double[] right = {
                    Math.cos(rotRad),
                    0,
                    -Math.sin(rotRad)
            };

            double[] up = {
                    -Math.sin(rotRad) * Math.sin(elevRad),
                    Math.cos(elevRad),
                    -Math.cos(rotRad) * Math.sin(elevRad)
            };
            for (int y = 0; y < imageHeight; y++) {
                for (int x = 0; x < imageWidth; x++) {
                    double u = (x - imageWidth / 2.0) / imageWidth;
                    double v = (y - imageHeight / 2.0) / imageHeight;

                    double[] rayDir = {
                            viewDir[0] + u * right[0] + v * up[0],
                            viewDir[1] + u * right[1] + v * up[1],
                            viewDir[2] + u * right[2] + v * up[2]
                    };
                    double length = Math.sqrt(rayDir[0] * rayDir[0] + rayDir[1] * rayDir[1] + rayDir[2] * rayDir[2]);
                    rayDir[0] /= length;
                    rayDir[1] /= length;
                    rayDir[2] /= length;
                    int[] color = castRay(rayDir, depth);
                    int rgb = (color[0] << 16) | (color[1] << 8) | color[2];
                    rayImage.setRGB(x, y, rgb);
                }
            }
            applyTransferFunction(rayImage);
            double xOffset = (depth / 2.0) * scale * Math.sin(rotRad) * Math.cos(elevRad);
            double yOffset = (depth / 2.0) * scale * Math.sin(elevRad);

            int x = (int) (centerX + xOffset - imageWidth * scale / 2);
            int y = (int) (centerY + yOffset - imageHeight * scale / 2);
            g.drawImage(rayImage, x, y,
                    (int) (imageWidth * scale),
                    (int) (imageHeight * scale), null);
        }

        private int[] castRay(double[] rayDir, int depth) {

            int[] color = new int[3];
            double[] accumulator = new double[3];
            double stepSize = 1.0 / depth;
            for (double t = 0; t < 1.0; t += stepSize) {
                double z = t * depth;
                int zIdx = (int) z;

                if (zIdx >= 0 && zIdx < depth) {
                    BufferedImage slice = loadImage(zIdx);
                    if (slice == null) continue;
                    int x = slice.getWidth() / 2;
                    int y = slice.getHeight() / 2;
                    int rgb = slice.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int greenVal = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    float[] tfColor = transferFunction.map(r / 255.0f);
                    accumulator[0] += tfColor[0] * stepSize;
                    accumulator[1] += tfColor[1] * stepSize;
                    accumulator[2] += tfColor[2] * stepSize;
                }
            }
            color[0] = (int) Math.min(255, accumulator[0] * 255);
            color[1] = (int) Math.min(255, accumulator[1] * 255);
            color[2] = (int) Math.min(255, accumulator[2] * 255);

            return color;
        }

        private void applyTransferFunction(BufferedImage image) {
            int width = image.getWidth();
            int height = image.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int greenVal = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    float gray = (r + greenVal + b) / (3.0f * 255.0f);
                    float[] tfColor = transferFunction.map(gray);
                    r = (int) (tfColor[0] * 255);
                    greenVal = (int) (tfColor[1] * 255);
                    b = (int) (tfColor[2] * 255);

                    rgb = (r << 16) | (greenVal << 8) | b;
                    image.setRGB(x, y, rgb);
                }
            }
        }

        public void setRenderingTechnique(int technique) {
            this.renderingTechnique = technique;
        }

        public void setRotation(int rotation) {
            this.rotation = rotation;
        }

        public void setElevation(int elevation) {
            this.elevation = elevation;
        }

        public void setInterpolationEnabled(boolean enabled) {
            this.interpolationEnabled = enabled;
        }

        public void setTransferFunction(TransferFunction transferFunction) {
            this.transferFunction = transferFunction;
        }

        public TransferFunction getTransferFunction() {
            return transferFunction;
        }

        public void setCacheSize(int bytes) {
            this.cacheSizeBytes = bytes;
        }

        public void reset() {
            rotation = 0;
            elevation = 0;
            renderingTechnique = 0;
            interpolationEnabled = true;
            transferFunction = new TransferFunction();
        }
    }

    public class VolumeViewPanel extends JPanel {
        private VolumeRenderer volumeRenderer;

        public VolumeViewPanel() {
            setBackground(Color.BLACK);
        }

        public void setVolumeRenderer(VolumeRenderer volumeRenderer) {
            this.volumeRenderer = volumeRenderer;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (volumeRenderer != null) {
                volumeRenderer.render(g);
            }
        }
    }

    public class TransferFunction {
        private List<Point2D.Float> controlPoints = new ArrayList<>();

        public TransferFunction() {
            controlPoints.add(new Point2D.Float(0.0f, 0.0f));
            controlPoints.add(new Point2D.Float(1.0f, 1.0f));
        }

        public float[] map(float value) {
            value = Math.max(0.0f, Math.min(1.0f, value));
            int i = 0;
            while (i < controlPoints.size() - 1 && controlPoints.get(i + 1).x < value) {
                i++;
            }

            if (i >= controlPoints.size() - 1) {
                Point2D.Float p = controlPoints.get(controlPoints.size() - 1);
                return new float[]{p.y, p.y, p.y};
            }
            Point2D.Float p1 = controlPoints.get(i);
            Point2D.Float p2 = controlPoints.get(i + 1);

            float t = (value - p1.x) / (p2.x - p1.x);
            float y = p1.y + t * (p2.y - p1.y);

            return new float[]{y, y, y};
        }

        public List<Point2D.Float> getControlPoints() {
            return controlPoints;
        }

        public void setControlPoints(List<Point2D.Float> controlPoints) {
            this.controlPoints = controlPoints;
        }
    }

    public class TransferFunctionPanel extends JPanel {
        private TransferFunction transferFunction;
        private List<Point2D.Float> controlPoints;
        private int selectedPoint = -1;

        public TransferFunctionPanel(TransferFunction transferFunction) {
            this.transferFunction = transferFunction;
            this.controlPoints = new ArrayList<>(transferFunction.getControlPoints());

            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(400, 300));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int x = e.getX();
                    int y = e.getY();
                    for (int i = 0; i < controlPoints.size(); i++) {
                        Point2D.Float p = controlPoints.get(i);
                        int px = (int) (p.x * getWidth());
                        int py = (int) ((1.0f - p.y) * getHeight());

                        if (Math.abs(x - px) < 5 && Math.abs(y - py) < 5) {
                            selectedPoint = i;
                            return;
                        }
                    }
                    if (x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight()) {
                        float fx = (float) x / getWidth();
                        float fy = 1.0f - ((float) y / getHeight());
                        int i = 0;
                        while (i < controlPoints.size() && controlPoints.get(i).x < fx) {
                            i++;
                        }

                        controlPoints.add(i, new Point2D.Float(fx, fy));
                        selectedPoint = i;
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    selectedPoint = -1;
                }
            });

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (selectedPoint >= 0) {
                        int x = e.getX();
                        int y = e.getY();
                        x = Math.max(0, Math.min(getWidth(), x));
                        y = Math.max(0, Math.min(getHeight(), y));
                        Point2D.Float p = controlPoints.get(selectedPoint);
                        p.x = (float) x / getWidth();
                        p.y = 1.0f - ((float) y / getHeight());
                        if (selectedPoint > 0) {
                            p.x = Math.max(controlPoints.get(selectedPoint - 1).x, p.x);
                        }
                        if (selectedPoint < controlPoints.size() - 1) {
                            p.x = Math.min(controlPoints.get(selectedPoint + 1).x, p.x);
                        }

                        repaint();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.LIGHT_GRAY);
            for (int i = 0; i <= 10; i++) {
                int x = i * getWidth() / 10;
                int y = i * getHeight() / 10;
                g.drawLine(x, 0, x, getHeight());
                g.drawLine(0, y, getWidth(), y);
            }
            g.setColor(Color.BLUE);
            for (int i = 0; i < controlPoints.size() - 1; i++) {
                Point2D.Float p1 = controlPoints.get(i);
                Point2D.Float p2 = controlPoints.get(i + 1);

                int x1 = (int) (p1.x * getWidth());
                int y1 = (int) ((1.0f - p1.y) * getHeight());
                int x2 = (int) (p2.x * getWidth());
                int y2 = (int) ((1.0f - p2.y) * getHeight());

                g.drawLine(x1, y1, x2, y2);
            }
            g.setColor(Color.RED);
            for (Point2D.Float p : controlPoints) {
                int x = (int) (p.x * getWidth());
                int y = (int) ((1.0f - p.y) * getHeight());
                g.fillOval(x - 5, y - 5, 10, 10);
            }
        }

        public TransferFunction getTransferFunction() {
            TransferFunction tf = new TransferFunction();
            tf.setControlPoints(new ArrayList<>(controlPoints));
            return tf;
        }
    }

    public void show3DView(JFrame parent, List<File> seriesImages, DicomFileManager dicomFileManager) {
        JFrame frame = new JFrame("Advanced 3D DICOM Viewer");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1000, 800);
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout());
        JLabel techniqueLabel = new JLabel("Rendering Technique:");
        String[] techniques = {"Simple Projection", "Maximum Intensity Projection (MIP)",
                "Average Intensity Projection (AIP)", "Ray Casting"};
        JComboBox<String> techniqueCombo = new JComboBox<>(techniques);
        JLabel rotationLabel = new JLabel("Rotation:");
        JSlider rotationSlider = new JSlider(0, 360, 0);
        rotationSlider.setMajorTickSpacing(90);
        rotationSlider.setPaintTicks(true);
        rotationSlider.setPaintLabels(true);

        JLabel elevationLabel = new JLabel("Elevation:");
        JSlider elevationSlider = new JSlider(-90, 90, 0);
        elevationSlider.setMajorTickSpacing(45);
        elevationSlider.setPaintTicks(true);
        elevationSlider.setPaintLabels(true);
        JCheckBox interpolationCheck = new JCheckBox("Enable Interpolation");
        interpolationCheck.setSelected(true);
        JLabel transferFunctionLabel = new JLabel("Transfer Function:");
        JButton transferFunctionButton = new JButton("Configure");
        JLabel cacheSizeLabel = new JLabel("Cache Size (MB):");
        JSpinner cacheSizeSpinner = new JSpinner(new SpinnerNumberModel(256, 64, 1024, 64));
        JButton resetButton = new JButton("Reset View");
        JButton exportButton = new JButton("Export 3D");
        controlPanel.add(techniqueLabel);
        controlPanel.add(techniqueCombo);
        controlPanel.add(rotationLabel);
        controlPanel.add(rotationSlider);
        controlPanel.add(elevationLabel);
        controlPanel.add(elevationSlider);
        controlPanel.add(interpolationCheck);
        controlPanel.add(transferFunctionLabel);
        controlPanel.add(transferFunctionButton);
        controlPanel.add(cacheSizeLabel);
        controlPanel.add(cacheSizeSpinner);
        controlPanel.add(resetButton);
        controlPanel.add(exportButton);
        VolumeViewPanel viewPanel = new VolumeViewPanel();
        viewPanel.setPreferredSize(new Dimension(1000, 700));
        frame.add(controlPanel, BorderLayout.NORTH);
        frame.add(viewPanel, BorderLayout.CENTER);
        VolumeRenderer volumeRenderer = new VolumeRenderer(seriesImages, viewPanel, dicomFileManager);
        techniqueCombo.addActionListener(e -> {
            volumeRenderer.setRenderingTechnique(techniqueCombo.getSelectedIndex());
            viewPanel.repaint();
        });

        rotationSlider.addChangeListener(e -> {
            volumeRenderer.setRotation(rotationSlider.getValue());
            viewPanel.repaint();
        });

        elevationSlider.addChangeListener(e -> {
            volumeRenderer.setElevation(elevationSlider.getValue());
            viewPanel.repaint();
        });

        interpolationCheck.addActionListener(e -> {
            volumeRenderer.setInterpolationEnabled(interpolationCheck.isSelected());
            viewPanel.repaint();
        });

        transferFunctionButton.addActionListener(e -> {
            showTransferFunctionDialog(frame, volumeRenderer);
            viewPanel.repaint();
        });

        cacheSizeSpinner.addChangeListener(e -> {
            int cacheSizeMB = (Integer) cacheSizeSpinner.getValue();
            volumeRenderer.setCacheSize(cacheSizeMB * 1024 * 1024);
        });

        resetButton.addActionListener(e -> {
            rotationSlider.setValue(0);
            elevationSlider.setValue(0);
            techniqueCombo.setSelectedIndex(0);
            interpolationCheck.setSelected(true);
            volumeRenderer.reset();
            viewPanel.repaint();
        });

        exportButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export 3D View");
            fileChooser.setSelectedFile(new File("3d_view.png"));

            int result = fileChooser.showSaveDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File outputFile = fileChooser.getSelectedFile();
                if (!outputFile.getName().toLowerCase().endsWith(".png")) {
                    outputFile = new File(outputFile.getParent(), outputFile.getName() + ".png");
                }

                BufferedImage image = new BufferedImage(viewPanel.getWidth(), viewPanel.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = image.createGraphics();
                viewPanel.paint(g2d);
                g2d.dispose();

                try {
                    ImageIO.write(image, "png", outputFile);
                    JOptionPane.showMessageDialog(frame,
                            "3D view exported successfully to:\n" + outputFile.getAbsolutePath(),
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Error exporting 3D view: " + ex.getMessage(),
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        frame.setVisible(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                volumeRenderer.loadImages();
                return null;
            }

            @Override
            protected void done() {
                viewPanel.repaint();
            }
        }.execute();
    }

    private void showTransferFunctionDialog(JFrame parent, VolumeRenderer volumeRenderer) {
        JDialog dialog = new JDialog(parent, "Transfer Function", true);
        dialog.setSize(500, 400);
        dialog.setLayout(new BorderLayout());
        TransferFunctionPanel tfPanel = new TransferFunctionPanel(volumeRenderer.getTransferFunction());
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(e -> {
            volumeRenderer.setTransferFunction(tfPanel.getTransferFunction());
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> {
            dialog.dispose();
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        dialog.add(tfPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }
}