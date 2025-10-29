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
import java.awt.event.*;

public class ImageRectangleOverlay {
    private JLabel imageLabel;
    private int rectWidth = 100;
    private int rectHeight = 100;
    private boolean showRectangle = false;
    private Point currentMousePosition;
    private JDialog settingsDialog;
    private JSpinner widthSpinner;
    private JSpinner heightSpinner;

    public ImageRectangleOverlay(JLabel imageLabel) {
        this.imageLabel = imageLabel;
        setupMouseListeners();
    }

    private void setupMouseListeners() {
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                showRectangle = true;
                imageLabel.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                showRectangle = false;
                imageLabel.repaint();
            }
        });

        imageLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                currentMousePosition = e.getPoint();
                imageLabel.repaint();
            }
        });
    }

    public void paintOverlay(Graphics g) {
        if (!showRectangle || currentMousePosition == null) {
            return;
        }
        int x = currentMousePosition.x - rectWidth / 2;
        int y = currentMousePosition.y - rectHeight / 2;
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Color.YELLOW);
        g2d.setStroke(new BasicStroke(0.5f));
        g2d.drawRect(x, y, rectWidth, rectHeight);
        String dimensionsText = rectWidth + " x " + rectHeight;
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(dimensionsText);
        int textHeight = fontMetrics.getHeight();
        int textX = x + (rectWidth - textWidth) / 2;
        int textY = y + rectHeight + textHeight;
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(textX - 2, textY - textHeight + 2, textWidth + 4, textHeight);
        g2d.setColor(Color.WHITE);
        g2d.drawString(dimensionsText, textX, textY);
        g2d.dispose();
    }

    public void showSettingsDialog(JFrame parent) {
        if (settingsDialog == null) {
            createSettingsDialog(parent);
        }
        settingsDialog.setVisible(true);
    }

    private void createSettingsDialog(JFrame parent) {
        settingsDialog = new JDialog(parent, "Cropping Dimensions", true);
        settingsDialog.setSize(300, 150);
        settingsDialog.setLocationRelativeTo(parent);
        settingsDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel dimensionsPanel = new JPanel(new GridLayout(2, 2, 5, 5));

        dimensionsPanel.add(new JLabel("Width:"));
        widthSpinner = new JSpinner(new SpinnerNumberModel(rectWidth, 10, 1000, 10));
        dimensionsPanel.add(widthSpinner);

        dimensionsPanel.add(new JLabel("Height:"));
        heightSpinner = new JSpinner(new SpinnerNumberModel(rectHeight, 10, 1000, 10));
        dimensionsPanel.add(heightSpinner);
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> {
            rectWidth = (Integer) widthSpinner.getValue();
            rectHeight = (Integer) heightSpinner.getValue();
            settingsDialog.setVisible(false);
            imageLabel.repaint();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            widthSpinner.setValue(rectWidth);
            heightSpinner.setValue(rectHeight);
            settingsDialog.setVisible(false);
        });

        buttonsPanel.add(applyButton);
        buttonsPanel.add(cancelButton);

        mainPanel.add(dimensionsPanel, BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        settingsDialog.add(mainPanel);
    }

    public int getRectWidth() {
        return rectWidth;
    }

    public void setRectWidth(int rectWidth) {
        this.rectWidth = rectWidth;
    }

    public int getRectHeight() {
        return rectHeight;
    }

    public void setRectHeight(int rectHeight) {
        this.rectHeight = rectHeight;
    }
}