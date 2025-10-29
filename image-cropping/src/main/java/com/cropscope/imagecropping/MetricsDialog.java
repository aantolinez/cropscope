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
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class MetricsDialog extends JDialog {

    private final JTextArea metricsArea;
    private final Performance performance;
    private final ImageCroppingCore core;
    private final CloudStorageManager cloud;

    public MetricsDialog(JFrame parent,
                         Performance performance,
                         ImageCroppingCore core,
                         CloudStorageManager cloud,
                         String metricsReportText) {
        super(parent, "Metrics Window", true);
        this.performance = performance;
        this.core = core;
        this.cloud = cloud;

        setLayout(new BorderLayout());
        metricsArea = new JTextArea(metricsReportText);
        metricsArea.setEditable(false);
        metricsArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        metricsArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton exportBtn = new JButton("Export Data");
        exportBtn.addActionListener(this::handleExport);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(exportBtn);
        buttonPanel.add(closeButton);

        add(new JScrollPane(metricsArea), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(450, 260);
        setLocationRelativeTo(getParent());
    }

    private void handleExport(ActionEvent e) {
        if (performance == null || !performance.isSessionActiveOrFinished()) {
            JOptionPane.showMessageDialog(this, "No performance session to export.", "Nothing to Export",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Performance.Snapshot snap = performance.getSnapshot();
        JSONObject json = buildJson(snap);
        final String sessionStamp = snap.sessionStartUtcStamp;
        final String sessionFolder = "session_" + sessionStamp;
        final String filename = "performance_session_" + sessionStamp + ".json";

        if (cloud != null && cloud.isUseCloudStorage()) {
            String cloudKey = sessionFolder + "/" + filename;
            byte[] data = json.toString(2).getBytes(StandardCharsets.UTF_8);
            cloud.queueCloudMetadataUpload(data, cloudKey);
            JOptionPane.showMessageDialog(this,
                    "Performance data queued for cloud upload:\n" +
                            cloud.getCloudBucketName() + "/" + cloudKey,
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } else {
            File sessionDir = new File(core.getSaveDirectory(), sessionFolder);
            sessionDir.mkdirs();
            File out = new File(sessionDir, filename);
            try {
                Files.write(out.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                JOptionPane.showMessageDialog(this,
                        "Performance data saved:\n" + out.getAbsolutePath(),
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private JSONObject buildJson(Performance.Snapshot s) {
        DecimalFormat df = new DecimalFormat("0.###");

        JSONObject root = new JSONObject();
        String createdStamp = ExportUtils.nowUtcStamp();
        root.put("createdAtUtc", ExportUtils.stampToIso(createdStamp));

        JSONObject session = new JSONObject();
        session.put("startUtc", ExportUtils.stampToIso(s.sessionStartUtcStamp));
        session.put("durationSeconds", s.elapsedSeconds);
        session.put("durationMinutes", Double.valueOf(df.format(s.elapsedSeconds / 60.0)));
        root.put("session", session);
        JSONObject bySize = new JSONObject();
        for (Map.Entry<String, Integer> e : s.countsBySize.entrySet()) {
            String size = e.getKey();
            int count = e.getValue();
            double perSec = (s.elapsedSeconds > 0 ? (count / (double) s.elapsedSeconds) : 0.0);
            double perMin = perSec * 60.0;

            JSONObject one = new JSONObject();
            one.put("total", count);
            one.put("perSecond", Double.valueOf(df.format(perSec)));
            one.put("perMinute", Double.valueOf(df.format(perMin)));
            bySize.put(size, one);
        }
        root.put("by_size", bySize);
        JSONObject totals = new JSONObject();
        totals.put("overall", s.totalCrops);
        root.put("totals", totals);
        root.put("project", core.getProjectName());
        root.put("user", core.getUserName());

        return root;
    }
}
