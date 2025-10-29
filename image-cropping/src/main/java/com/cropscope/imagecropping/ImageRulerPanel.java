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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class ImageRulerPanel extends JPanel {
    private final ImageCroppingCore core;
    private final ImageCroppingCore.ImagePanel imagePanel;
    private final JPanel topRuler;
    private final JPanel leftRuler;
    private final JPanel cornerPanel;
    private final int verticalRulerSize = 30;
    private final int horizontalRulerSize = 25;
    private final int rightOffset = 20;
    private final int bottomOffset = 20;
    private final int majorTickSize = 10;
    private final int minorTickSize = 5;
    private final int pixelsPerUnit = 50;
    private final String unitLabel = "px";

    private Point lastMousePos = null;
    private Rectangle imageBounds = new Rectangle();

    public ImageRulerPanel(ImageCroppingCore core) {
        this.core = core;
        this.imagePanel = core.getImagePanel();
        setLayout(new BorderLayout());
        topRuler = createHorizontalRuler();
        leftRuler = createVerticalRuler();
        cornerPanel = createCornerPanel();
        topRuler.setPreferredSize(new Dimension(Integer.MAX_VALUE, horizontalRulerSize));
        leftRuler.setPreferredSize(new Dimension(verticalRulerSize, Integer.MAX_VALUE));
        cornerPanel.setPreferredSize(new Dimension(verticalRulerSize, horizontalRulerSize));
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(leftRuler, BorderLayout.WEST);
        centerPanel.add(imagePanel, BorderLayout.CENTER);
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(cornerPanel, BorderLayout.WEST);
        topPanel.add(topRuler, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateImageBounds();
                repaint();
            }
        });
        imagePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                lastMousePos = e.getPoint();
                updateImageBounds();
                repaint();
            }
        });
        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                lastMousePos = null;
                repaint();
            }
        });
    }

    private JPanel createHorizontalRuler() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));

                int rulerWidth = getWidth();
                int rulerHeight = getHeight();
                g2d.drawLine(0, rulerHeight - 1, rulerWidth, rulerHeight - 1);
                for (int i = 0; i < rulerWidth; i += pixelsPerUnit) {
                    g2d.drawLine(i, rulerHeight - 1 - majorTickSize, i, rulerHeight - 1);
                    String label = String.valueOf(i);
                    int labelWidth = g2d.getFontMetrics().stringWidth(label);
                    g2d.drawString(label, i - labelWidth / 2, rulerHeight - 1 - majorTickSize - 2);
                    for (int j = 1; j < 5; j++) {
                        int minorX = i + (j * pixelsPerUnit / 5);
                        if (minorX < rulerWidth) {
                            g2d.drawLine(minorX, rulerHeight - 1 - minorTickSize, minorX, rulerHeight - 1);
                        }
                    }
                }
                g2d.drawString(unitLabel, rulerWidth - 20, rulerHeight / 2 + 4);
                if (lastMousePos != null && imageBounds.contains(lastMousePos)) {
                    int mouseX = lastMousePos.x;
                    if (mouseX >= 0 && mouseX < rulerWidth) {
                        g2d.setColor(Color.RED);
                        g2d.drawLine(mouseX, 0, mouseX, rulerHeight - 1);
                        String posLabel = String.valueOf(mouseX);
                        int labelWidth = g2d.getFontMetrics().stringWidth(posLabel);
                        g2d.drawString(posLabel, mouseX - labelWidth / 2, 12);
                    }
                }
            }
        };
    }

    private JPanel createVerticalRuler() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));

                int rulerWidth = getWidth();
                int rulerHeight = getHeight();
                g2d.drawLine(rulerWidth - 1, 0, rulerWidth - 1, rulerHeight);
                for (int i = 0; i < rulerHeight; i += pixelsPerUnit) {
                    g2d.drawLine(rulerWidth - 1 - majorTickSize, i, rulerWidth - 1, i);
                    String label = String.valueOf(i);
                    int labelWidth = g2d.getFontMetrics().stringWidth(label);
                    int labelX = rulerWidth - 1 - majorTickSize - labelWidth - 2;
                    if (labelX < 2) labelX = 2;
                    g2d.drawString(label, labelX, i + 4);
                    for (int j = 1; j < 5; j++) {
                        int minorY = i + (j * pixelsPerUnit / 5);
                        if (minorY < rulerHeight) {
                            g2d.drawLine(rulerWidth - 1 - minorTickSize, minorY, rulerWidth - 1, minorY);
                        }
                    }
                }
                Graphics2D rotatedG2d = (Graphics2D) g2d.create();
                rotatedG2d.translate(rulerWidth * 0.7, rulerHeight / 2);
                rotatedG2d.rotate(-Math.PI / 2);
                rotatedG2d.drawString(unitLabel, 0, 0);
                rotatedG2d.dispose();
                if (lastMousePos != null && imageBounds.contains(lastMousePos)) {
                    int mouseY = lastMousePos.y;
                    if (mouseY >= 0 && mouseY < rulerHeight) {
                        g2d.setColor(Color.RED);
                        g2d.drawLine(0, mouseY, rulerWidth - 1, mouseY);
                        String posLabel = String.valueOf(mouseY);
                        int labelWidth = g2d.getFontMetrics().stringWidth(posLabel);
                        g2d.drawString(posLabel, rulerWidth - labelWidth - 5, mouseY - 5);
                    }
                }
            }
        };
    }

    private JPanel createCornerPanel() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                Color cornerBackground = new Color(240, 240, 240);
                g2d.setColor(cornerBackground);
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.setColor(cornerBackground);
                g2d.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2d.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }
        };
    }

    private void updateImageBounds() {
        if (imagePanel != null) {
            Point loc = imagePanel.getLocation();
            imageBounds.setBounds(loc.x, loc.y, imagePanel.getWidth(), imagePanel.getHeight());
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension imagePref = imagePanel.getPreferredSize();
        return new Dimension(
                imagePref.width + verticalRulerSize + rightOffset,
                imagePref.height + horizontalRulerSize + bottomOffset
        );
    }

    @Override
    public void doLayout() {
        super.doLayout();
        updateImageBounds();
    }

    public ImageCroppingCore.ImagePanel getImagePanel() {
        return imagePanel;
    }

    public void setRulersVisible(boolean visible) {
        topRuler.setVisible(visible);
        leftRuler.setVisible(visible);
        cornerPanel.setVisible(visible);
    }
}