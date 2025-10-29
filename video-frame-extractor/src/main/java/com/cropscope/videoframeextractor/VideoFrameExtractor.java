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

import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

public class VideoFrameExtractor extends JDialog {
    private JTextField tfSource;
    private JTextField tfSink;
    private JCheckBox cbDropDuplicates;
    private ExtractionFilterPanel filterPanel;
    private JTextField tfStart;
    private JTextField tfEnd;
    private JTextField tfPrefix;
    private JButton btnAdd, btnRemove;
    private DefaultListModel<Segment> model;
    private JList<Segment> list;
    private JProgressBar progressBar;
    private JLabel lblPercent;
    private JLabel lblDropped;
    private FrameExtractionWorker worker;
    private String projectSetting = "Generic annotation";
    private String userSetting = "Generic user";
    private BatchProcessor batchProcessor;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame owner = new JFrame("Video Frame Extractor");
            owner.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            VideoFrameExtractor dlg = new VideoFrameExtractor(owner);
            dlg.setVisible(true);
        });
    }

    public VideoFrameExtractor(Frame owner) {
        super(owner, "Extract Frames", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });
        batchProcessor = new BatchProcessor(this);
        setJMenuBar(buildMenuBar());
        setContentPane(buildUI());
        pack();
        setLocationRelativeTo(owner);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem miExit = new JMenuItem("Exit");
        miExit.addActionListener(e -> exitApplication());
        file.add(miExit);
        mb.add(file);
        JMenu settings = new JMenu("Settings");
        JMenuItem miProjectUserSettings = new JMenuItem("Project/User Settings");
        miProjectUserSettings.addActionListener(e -> showProjectUserSettingsDialog());
        settings.add(miProjectUserSettings);
        mb.add(settings);
        JMenu batchMenu = new JMenu("Batch Processing");
        JMenuItem miLoadMetadata = new JMenuItem("Load Metadata File");
        miLoadMetadata.addActionListener(e -> batchProcessor.showMetadataFileChooser());
        batchMenu.add(miLoadMetadata);
        mb.add(batchMenu);
        return mb;
    }

    private void showProjectUserSettingsDialog() {
        JDialog settingsDialog = new JDialog(this, "Project/User Settings", true);
        settingsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        settingsDialog.setSize(400, 200);
        settingsDialog.setLocationRelativeTo(this);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Project:"), gc);
        gc.gridx = 1;
        gc.gridy = 0;
        gc.weightx = 1.0;
        JTextField projectField = new JTextField(projectSetting, 20);
        panel.add(projectField, gc);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.weightx = 0;
        gc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("User:"), gc);
        gc.gridx = 1;
        gc.gridy = 1;
        gc.weightx = 1.0;
        JTextField userField = new JTextField(userSetting, 20);
        panel.add(userField, gc);
        gc.gridx = 0;
        gc.gridy = 2;
        gc.gridwidth = 2;
        gc.weightx = 0;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton btnSave = new JButton("Save");
        btnSave.addActionListener(e -> {
            projectSetting = projectField.getText().trim();
            if (projectSetting.isEmpty()) projectSetting = "Generic annotation";
            userSetting = userField.getText().trim();
            if (userSetting.isEmpty()) userSetting = "Generic user";
            settingsDialog.dispose();
        });
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> settingsDialog.dispose());
        buttonPanel.add(btnSave);
        buttonPanel.add(btnCancel);
        panel.add(buttonPanel, gc);
        settingsDialog.setContentPane(panel);
        settingsDialog.setVisible(true);
    }

    private void exitApplication() {
        if (worker != null && !worker.isDone()) {
            int c = JOptionPane.showConfirmDialog(
                    this,
                    "An extraction is running. Stop it and exit?",
                    "Exit",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (c != JOptionPane.YES_OPTION) return;
            try {
                worker.requestStopAndDestroy();
            } catch (Exception ignore) {
            }
        }
        for (Window w : Window.getWindows()) {
            try {
                w.dispose();
            } catch (Exception ignore) {
            }
        }
        System.exit(0);
    }

    private JPanel buildUI() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 1;
        gc.weightx = 0;
        root.add(new JLabel("Source"), gc);
        tfSource = new JTextField(38);
        gc.gridx = 1;
        gc.gridwidth = 1;
        gc.weightx = 1.0;
        root.add(tfSource, gc);
        JButton btnBrowseSrc = new JButton("Browse");
        btnBrowseSrc.addActionListener(this::onBrowseSource);
        gc.gridx = 2;
        gc.gridwidth = 1;
        gc.weightx = 0;
        root.add(btnBrowseSrc, gc);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.gridwidth = 1;
        gc.weightx = 0;
        root.add(new JLabel("Sink"), gc);
        tfSink = new JTextField(38);
        gc.gridx = 1;
        gc.gridwidth = 1;
        gc.weightx = 1.0;
        root.add(tfSink, gc);
        JButton btnBrowseSink = new JButton("Browse");
        btnBrowseSink.addActionListener(this::onBrowseSink);
        gc.gridx = 2;
        gc.gridwidth = 1;
        gc.weightx = 0;
        root.add(btnBrowseSink, gc);
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        opts.setBorder(BorderFactory.createTitledBorder("Options"));
        cbDropDuplicates = new JCheckBox("Drop duplicate frames");
        opts.add(cbDropDuplicates);
        gc.gridx = 0;
        gc.gridy = 2;
        gc.gridwidth = 3;
        gc.weightx = 1.0;
        root.add(opts, gc);
        gc.gridy = 3;
        filterPanel = new ExtractionFilterPanel();
        root.add(filterPanel, gc);
        gc.gridy = 4;
        JSeparator sep = new JSeparator();
        gc.fill = GridBagConstraints.HORIZONTAL;
        root.add(sep, gc);
        gc.gridy = 5;
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addRow.add(new JLabel("Start"));
        tfStart = fixedNumericField(4);
        addRow.add(tfStart);
        addRow.add(new JLabel("End"));
        tfEnd = fixedNumericField(4);
        addRow.add(tfEnd);
        addRow.add(new JLabel("Prefix"));
        tfPrefix = new JTextField("Frame_", 12);
        addRow.add(tfPrefix);
        btnAdd = new JButton("Add");
        btnAdd.addActionListener(e -> onAddSegment());
        addRow.add(btnAdd);
        btnRemove = new JButton("Remove");
        btnRemove.addActionListener(e -> onRemoveSelected());
        addRow.add(btnRemove);
        root.add(addRow, gc);
        gc.gridy = 6;
        gc.gridwidth = 3;
        gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        model = new DefaultListModel<Segment>();
        list = new JList<Segment>(model);
        list.setVisibleRowCount(10);
        root.add(new JScrollPane(list), gc);
        gc.weighty = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridy = 7;
        JPanel prog = new JPanel(new BorderLayout(8, 0));
        progressBar = new JProgressBar(0, 100);
        lblPercent = new JLabel("0%");
        lblDropped = new JLabel("Dropped: 0");
        prog.add(progressBar, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(lblDropped);
        right.add(lblPercent);
        prog.add(right, BorderLayout.EAST);
        root.add(prog, gc);
        gc.gridy = 8;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        JButton btnAccept = new JButton("Accept");
        btnAccept.addActionListener(e -> onAccept());
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> onCancel());
        actions.add(btnAccept);
        actions.add(btnCancel);
        root.add(actions, gc);
        return root;
    }

    private JTextField fixedNumericField(int maxLen) {
        JTextField tf = new JTextField(4);
        ((AbstractDocument) tf.getDocument()).setDocumentFilter(new FixedNumericFilter(maxLen));
        return tf;
    }

    private void onBrowseSource(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Video");
        fc.setFileFilter(new FileNameExtensionFilter(
                "Videos (mp4, mov, mkv, avi, webm, mpg, mpeg)",
                "mp4", "mov", "mkv", "avi", "webm", "mpg", "mpeg"));
        int res = fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            tfSource.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onBrowseSink(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Sink Folder");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int res = fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            tfSink.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onAddSegment() {
        String s1 = tfStart.getText().trim();
        String s2 = tfEnd.getText().trim();
        String px = tfPrefix.getText().trim();
        if (px.isEmpty()) px = "Frame_";
        if (s1.isEmpty() || s2.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter Start and End seconds (4 digits).", "Missing fields", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int start = Integer.parseInt(s1);
        int end = Integer.parseInt(s2);
        if (end < start) {
            JOptionPane.showMessageDialog(this, "End must be >= Start.", "Invalid range", JOptionPane.WARNING_MESSAGE);
            return;
        }
        model.addElement(new Segment(start, end, px));
        tfStart.setText("");
        tfEnd.setText("");
    }

    private void onRemoveSelected() {
        List<Segment> sel = list.getSelectedValuesList();
        for (Segment s : sel) model.removeElement(s);
    }

    private void onAccept() {
        File in = new File(tfSource.getText().trim());
        if (!in.isFile()) {
            JOptionPane.showMessageDialog(this, "Choose a valid source video.", "No source", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File outDir = new File(tfSink.getText().trim());
        if (!outDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Choose a valid sink folder.", "No sink", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (worker != null && !worker.isDone()) {
            JOptionPane.showMessageDialog(this, "An extraction is already running.", "Busy", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Segment> segs = new ArrayList<Segment>();
        if (model.isEmpty()) {
            segs.add(Segment.full(tfPrefix.getText().trim().isEmpty() ? "Frame_" : tfPrefix.getText().trim()));
        } else {
            for (int i = 0; i < model.size(); i++) segs.add(model.get(i));
        }
        setControlsEnabled(false);
        progressBar.setIndeterminate(true);
        lblPercent.setText("0%");
        lblDropped.setText("Dropped: 0");
        worker = new FrameExtractionWorker(in, outDir, segs, projectSetting, userSetting,
                progressBar, lblPercent, lblDropped);
        ExtractionOptions options = collectExtractionOptions();
        worker.setExtractionOptions(options);
        FfmpegFilterBuilder fb = (filterPanel != null)
                ? filterPanel.toFilterBuilder()
                : new FfmpegFilterBuilder().setDropDuplicates(cbDropDuplicates.isSelected());
        String filterString = fb.build();
        worker.setFilterString(filterString);
        worker.addPropertyChangeListener(evt -> {
            if ("state".equals(evt.getPropertyName()) && SwingWorker.StateValue.DONE == evt.getNewValue()) {
                try {
                    worker.get();
                    showResultsDialog(worker);
                } catch (InterruptedException e) {
                    JOptionPane.showMessageDialog(VideoFrameExtractor.this,
                            "Extraction was interrupted.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    JOptionPane.showMessageDialog(VideoFrameExtractor.this,
                            "Extraction failed: " + (cause != null ? cause.getMessage() : "Unknown error"),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setControlsEnabled(true);
                    progressBar.setIndeterminate(false);
                    if (progressBar.getValue() == 0) lblPercent.setText("0%");
                }
            }
        });
        worker.execute();
    }

    private void onCancel() {
        if (worker != null) worker.cancel(true);
        setControlsEnabled(true);
        progressBar.setIndeterminate(false);
    }

    private void setControlsEnabled(boolean en) {
        tfSource.setEnabled(en);
        tfSink.setEnabled(en);
        cbDropDuplicates.setEnabled(en);
        filterPanel.setEnabled(en);
        tfStart.setEnabled(en);
        tfEnd.setEnabled(en);
        tfPrefix.setEnabled(en);
        btnAdd.setEnabled(en);
        btnRemove.setEnabled(en);
    }

    private void showResultsDialog(FrameExtractionWorker worker) {
        String msg;
        if (worker.getSegReports().isEmpty()) {
            msg = "Done.";
        } else {
            StringBuilder sb = new StringBuilder("Done.\n\nSegment summary:\n");
            for (String r : worker.getSegReports()) sb.append("• ").append(r).append('\n');
            msg = sb.toString();
        }
        if (worker.getVideoDuration() > 0) {
            double extractionTimeSeconds = (worker.getExtractionEndTime() - worker.getExtractionStartTime()) / 1000.0;
            double fps = worker.getTotalFramesExtracted() / worker.getVideoDuration();
            double fpm = fps * 60;
            double processingRate = (worker.getTotalFramesExtracted() * 1000.0) /
                    (worker.getExtractionEndTime() - worker.getExtractionStartTime());
            double duplicateRate = (worker.getDroppedTotal() * 100.0) /
                    (worker.getTotalFramesExtracted() + worker.getDroppedTotal());
            msg += String.format("\n\nMetrics Summary:\n" +
                            "• Total frames extracted: %d\n" +
                            "• Video duration: %.2f seconds\n" +
                            "• Extraction time: %.2f seconds\n" +
                            "• Frames per second: %.2f\n" +
                            "• Frames per minute: %.2f\n" +
                            "• Processing rate: %.2f frames/sec\n" +
                            "• Duplicate frame rate: %.2f%%\n" +
                            "• Dropped frames: %d",
                    worker.getTotalFramesExtracted(), worker.getVideoDuration(), extractionTimeSeconds,
                    fps, fpm, processingRate, duplicateRate, worker.getDroppedTotal());
        }
        JOptionPane.showMessageDialog(VideoFrameExtractor.this, msg, "Extraction", JOptionPane.INFORMATION_MESSAGE);
    }

    private ExtractionOptions collectExtractionOptions() {
        ExtractionOptions options = new ExtractionOptions();
        options.setDropDuplicates(cbDropDuplicates.isSelected());
        if (filterPanel != null) {
            options.setAdvancedDropDuplicates(filterPanel.getCbDropDup().isSelected());
            options.setUseCustomMpdecimateParams(filterPanel.getCbMpParams().isSelected());
            if (filterPanel.getCbMpParams().isSelected()) {
                options.setMpdecimateHi(parseInt(filterPanel.getTfHi().getText()));
                options.setMpdecimateLo(parseInt(filterPanel.getTfLo().getText()));
                options.setMpdecimateFrac(parseDouble(filterPanel.getTfFrac().getText()));
            }
            options.setUseUniformSampling(filterPanel.getCbFps().isSelected());
            if (filterPanel.getCbFps().isSelected()) {
                options.setUniformSamplingFps(parseDouble(filterPanel.getTfFps().getText()));
            }
            options.setUseSceneChanges(filterPanel.getCbScene().isSelected());
            if (filterPanel.getCbScene().isSelected()) {
                options.setSceneThreshold(parseDouble(filterPanel.getTfScene().getText()));
            }
            options.setSkipDarkFrames(filterPanel.getCbLuma().isSelected());
            if (filterPanel.getCbLuma().isSelected()) {
                options.setMinLumaYavg(parseDouble(filterPanel.getTfLuma().getText()));
            }
        }
        return options;
    }

    private Integer parseInt(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(String s) {
        try {
            return Double.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static class Segment {
        final int start;
        final int end;
        final String prefix;

        public Segment(int start, int end, String prefix) {
            this.start = start;
            this.end = end;
            this.prefix = (prefix == null ? "Frame_" : prefix);
        }

        public static Segment full(String prefix) {
            return new Segment(-1, -1, prefix == null ? "Frame_" : prefix);
        }

        public boolean isFull() {
            return start < 0 || end < 0;
        }

        @Override
        public String toString() {
            return isFull() ? "[full video]  prefix=\"" + prefix + "\""
                    : "(" + start + ", " + end + ")  prefix=\"" + prefix + "\"";
        }
    }

    private static class FixedNumericFilter extends DocumentFilter {
        private final int maxLen;

        FixedNumericFilter(int maxLen) {
            this.maxLen = maxLen;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) return;
            String cleaned = digitsOnly(string);
            if (fb.getDocument().getLength() + cleaned.length() <= maxLen)
                super.insertString(fb, offset, cleaned, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String cleaned = digitsOnly(text == null ? "" : text);
            if (fb.getDocument().getLength() - length + cleaned.length() <= maxLen)
                super.replace(fb, offset, length, cleaned, attrs);
        }

        private String digitsOnly(String s) {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) sb.append(s.charAt(i));
            return sb.toString();
        }
    }
}