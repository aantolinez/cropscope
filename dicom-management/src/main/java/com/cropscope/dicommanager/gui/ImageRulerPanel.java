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

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageRulerPanel extends JPanel {
    private JLabel imageLabel;
    private BufferedImage currentImage;
    private boolean showRulers = true;
    private int horizontalRulerSize = 20;
    private int verticalRulerSize = 25;
    private int majorTickSize = 10;
    private int minorTickSize = 5;
    private int pixelsPerUnit = 50;
    private String unitLabel = "cm";

    public ImageRulerPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setBackground(Color.BLACK);
        imageLabel.setOpaque(true);
        JPanel topRuler = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (showRulers) {
                    drawHorizontalRuler(g, getWidth(), getHeight());
                }
            }
        };

        JPanel leftRuler = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (showRulers) {
                    drawVerticalRuler(g, getWidth(), getHeight());
                }
            }
        };
        topRuler.setPreferredSize(new Dimension(getWidth(), horizontalRulerSize));
        leftRuler.setPreferredSize(new Dimension(verticalRulerSize, getHeight()));
        topRuler.setBackground(new Color(240, 240, 240));
        leftRuler.setBackground(new Color(240, 240, 240));
        JPanel cornerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (showRulers) {
                    g.setColor(new Color(240, 240, 240));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(Color.BLACK);
                    g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                    g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                }
            }
        };
        cornerPanel.setPreferredSize(new Dimension(verticalRulerSize, horizontalRulerSize));
        cornerPanel.setBackground(new Color(240, 240, 240));
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(topRuler, BorderLayout.CENTER);
        topPanel.add(cornerPanel, BorderLayout.EAST);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(leftRuler, BorderLayout.WEST);
        centerPanel.add(imageLabel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void drawHorizontalRuler(Graphics g, int width, int height) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2d.drawLine(verticalRulerSize, height - 1, width - verticalRulerSize, height - 1);
        for (int i = verticalRulerSize; i < width - verticalRulerSize; i += pixelsPerUnit) {
            g2d.drawLine(i, height - 1 - majorTickSize, i, height - 1);
            String label = String.valueOf(i / pixelsPerUnit);
            int labelWidth = g2d.getFontMetrics().stringWidth(label);
            g2d.drawString(label, i - labelWidth / 2, height - 1 - majorTickSize - 2);
            for (int j = 1; j < 5; j++) {
                int minorX = i + (j * pixelsPerUnit / 5);
                if (minorX < width - verticalRulerSize) {
                    g2d.drawLine(minorX, height - 1 - minorTickSize, minorX, height - 1);
                }
            }
        }
        g2d.drawString(unitLabel, width - verticalRulerSize - 3, height / 2 + 4);

        g2d.dispose();
    }

    private void drawVerticalRuler(Graphics g, int width, int height) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2d.drawLine(width - 1, 0, width - 1, height - horizontalRulerSize);
        for (int i = 0; i < height - horizontalRulerSize; i += pixelsPerUnit) {
            g2d.drawLine(width - 1 - majorTickSize, i, width - 1, i);
            String label = String.valueOf(i / pixelsPerUnit);
            int labelWidth = g2d.getFontMetrics().stringWidth(label);
            int labelX = width - 1 - majorTickSize - labelWidth - 2;
            if (labelX < 0) {
                labelX = 2;
            }
            g2d.drawString(label, labelX, i + 4);
            for (int j = 1; j < 5; j++) {
                int minorY = i + (j * pixelsPerUnit / 5);
                if (minorY < height - horizontalRulerSize) {
                    g2d.drawLine(width - 1 - minorTickSize, minorY, width - 1, minorY);
                }
            }
        }
        Graphics2D rotatedG2d = (Graphics2D) g2d.create();
        rotatedG2d.translate(verticalRulerSize * 0.7, height - horizontalRulerSize / 2 + 4);
        rotatedG2d.rotate(-Math.PI / 2);
        rotatedG2d.drawString(unitLabel, 0, 0);
        rotatedG2d.dispose();

        g2d.dispose();
    }

    public void setImage(BufferedImage image) {
        this.currentImage = image;
        if (image != null) {
            ImageIcon icon = new ImageIcon(image);
            Image scaledImage = icon.getImage().getScaledInstance(
                    imageLabel.getWidth() - 20,
                    imageLabel.getHeight() - 20,
                    Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaledImage));
        } else {
            imageLabel.setIcon(null);
        }
        repaint();
    }

    public BufferedImage getCurrentImage() {
        return currentImage;
    }

    public void setShowRulers(boolean showRulers) {
        this.showRulers = showRulers;
        repaint();
    }

    public boolean isShowRulers() {
        return showRulers;
    }

    public void setPixelsPerUnit(int pixelsPerUnit) {
        this.pixelsPerUnit = pixelsPerUnit;
        repaint();
    }

    public int getPixelsPerUnit() {
        return pixelsPerUnit;
    }

    public void setUnitLabel(String unitLabel) {
        this.unitLabel = unitLabel;
        repaint();
    }

    public String getUnitLabel() {
        return unitLabel;
    }
}