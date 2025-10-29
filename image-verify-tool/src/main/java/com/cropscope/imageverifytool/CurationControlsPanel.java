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

package com.cropscope.imageverifytool;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class CurationControlsPanel extends JPanel {

    private final JCheckBox cbBlur = new JCheckBox("Enable blur filter (Variance of Laplacian ≥)");
    private final JComboBox<Double> cmbBlur = new JComboBox<>(new Double[]{80.0, 100.0, 120.0, 150.0, 180.0});

    private final JCheckBox cbLuma = new JCheckBox("Enable min luma (mean Y ≥)");
    private final JComboBox<Integer> cmbLuma = new JComboBox<>(new Integer[]{0, 10, 20, 30, 40, 60});

    private final JCheckBox cbMove = new JCheckBox("Move results to subfolders");
    private final JTextField tfAccepted = new JTextField("accepted", 10);
    private final JTextField tfRejected = new JTextField("rejected", 10);

    private final JCheckBox cbManifest = new JCheckBox("Write manifest CSV");
    private final JTextField tfManifest = new JTextField("manifest.csv", 18);

    public CurationControlsPanel() {
        super(new GridBagLayout());
        setBorder(new TitledBorder("Curation rules & output"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 0;
        int col = 0;
        g.gridx = col++;
        g.gridy = 0;
        add(cbBlur, g);
        g.gridx = col++;
        add(cmbBlur, g);
        cbBlur.setSelected(false);
        cmbBlur.setSelectedItem(120.0);
        col = 0;
        g.gridx = col++;
        g.gridy = 1;
        add(cbLuma, g);
        g.gridx = col++;
        add(cmbLuma, g);
        cbLuma.setSelected(false);
        cmbLuma.setSelectedItem(20);
        JPanel movePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        movePanel.add(cbMove);
        movePanel.add(new JLabel("accepted:"));
        movePanel.add(tfAccepted);
        movePanel.add(new JLabel("rejected:"));
        movePanel.add(tfRejected);
        g.gridx = 0;
        g.gridy = 2;
        g.gridwidth = 2;
        add(movePanel, g);
        cbMove.setSelected(false);
        JPanel manPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        manPanel.add(cbManifest);
        manPanel.add(new JLabel("file:"));
        manPanel.add(tfManifest);
        g.gridx = 0;
        g.gridy = 3;
        g.gridwidth = 2;
        add(manPanel, g);
        cbManifest.setSelected(false);
    }

    public boolean isBlurEnabled() {
        return cbBlur.isSelected();
    }
    public double  getBlurThreshold() { return (Double) cmbBlur.getSelectedItem(); }

    public boolean isLumaEnabled() { return cbLuma.isSelected(); }
    public int     getMinLuma() { return (Integer) cmbLuma.getSelectedItem(); }

    public boolean isMoveEnabled() { return cbMove.isSelected(); }
    public String  getAcceptedSubdir() { return tfAccepted.getText().trim(); }
    public String  getRejectedSubdir() { return tfRejected.getText().trim(); }

    public boolean isManifestEnabled() { return cbManifest.isSelected(); }
    public String  getManifestFilename() { return tfManifest.getText().trim(); }
}

