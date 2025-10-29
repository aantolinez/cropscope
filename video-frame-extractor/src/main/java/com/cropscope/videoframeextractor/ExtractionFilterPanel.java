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

package com.cropscope.videoframeextractor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class ExtractionFilterPanel extends JPanel {
    private final JCheckBox cbDropDup = new JCheckBox("Drop duplicate frames (mpdecimate)");

    private final JCheckBox cbMpParams = new JCheckBox("Custom mpdecimate parameters:");
    private final JTextField tfHi = new JTextField("512", 5);
    private final JTextField tfLo = new JTextField("192", 5);
    private final JTextField tfFrac = new JTextField("0.30", 5);

    private final JCheckBox cbFps = new JCheckBox("Uniform sampling (fps):");
    private final JTextField tfFps = new JTextField("", 6);

    private final JCheckBox cbScene = new JCheckBox("Only scene changes (threshold 0.01–0.10):");
    private final JTextField tfScene = new JTextField("", 6);

    private final JCheckBox cbLuma = new JCheckBox("Skip dark frames (min mean Y 0..255):");
    private final JTextField tfLuma = new JTextField("", 6);

    public ExtractionFilterPanel() {
        super(new GridBagLayout());
        setBorder(new TitledBorder("Advanced extraction filters"));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1.0;

        int row = 0;
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 3;
        add(cbDropDup, g);
        JPanel mpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        mpPanel.add(cbMpParams);
        mpPanel.add(new JLabel("hi:"));
        mpPanel.add(tfHi);
        mpPanel.add(new JLabel("lo:"));
        mpPanel.add(tfLo);
        mpPanel.add(new JLabel("frac:"));
        mpPanel.add(tfFrac);
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 3;
        add(mpPanel, g);
        cbMpParams.setSelected(false);
        JPanel fpsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fpsPanel.add(cbFps);
        fpsPanel.add(tfFps);
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 3;
        add(fpsPanel, g);
        JPanel scPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        scPanel.add(cbScene);
        scPanel.add(tfScene);
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 3;
        add(scPanel, g);
        JPanel lmPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lmPanel.add(cbLuma);
        lmPanel.add(tfLuma);
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 3;
        add(lmPanel, g);
    }

    public FfmpegFilterBuilder toFilterBuilder() {
        Double fps = (cbFps.isSelected() && nonEmpty(tfFps)) ? parseDouble(tfFps.getText()) : null;
        Double scene = (cbScene.isSelected() && nonEmpty(tfScene)) ? parseDouble(tfScene.getText()) : null;
        Double luma = (cbLuma.isSelected() && nonEmpty(tfLuma)) ? parseDouble(tfLuma.getText()) : null;

        FfmpegFilterBuilder fb = new FfmpegFilterBuilder()
                .setDropDuplicates(cbDropDup.isSelected())
                .setFps(fps)
                .setSceneThreshold(scene)
                .setMinLumaYavg(luma);

        if (cbMpParams.isSelected()) {
            Integer hi = parseInt(tfHi.getText());
            Integer lo = parseInt(tfLo.getText());
            Double fr = parseDouble(tfFrac.getText());
            fb.setMpdecimateParams(hi, lo, fr);
        }
        return fb;
    }

    public JCheckBox getCbDropDup() {
        return cbDropDup;
    }

    public JCheckBox getCbMpParams() {
        return cbMpParams;
    }

    public JTextField getTfHi() {
        return tfHi;
    }

    public JTextField getTfLo() {
        return tfLo;
    }

    public JTextField getTfFrac() {
        return tfFrac;
    }

    public JCheckBox getCbFps() {
        return cbFps;
    }

    public JTextField getTfFps() {
        return tfFps;
    }

    public JCheckBox getCbScene() {
        return cbScene;
    }

    public JTextField getTfScene() {
        return tfScene;
    }

    public JCheckBox getCbLuma() {
        return cbLuma;
    }

    public JTextField getTfLuma() {
        return tfLuma;
    }

    private static boolean nonEmpty(JTextField tf) {
        return tf.getText() != null && tf.getText().trim().length() > 0;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDouble(String s) {
        try {
            return Double.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
