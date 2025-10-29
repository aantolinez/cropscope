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

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;

public class ImageVerifyTool extends JFrame {

    private JTextField tfFolder;
    private JTextArea log;
    private JLabel status;

    private JComboBox<Integer> cmbDhash;
    private JComboBox<Double> cmbPsnr;
    private JComboBox<Double> cmbSsim;

    private JRadioButton rbLoose, rbMedium, rbStrict, rbCustom;
    private ButtonGroup presetGroup;

    private boolean updatingFromPreset = false;

    private enum Metric {DHASH, PSNR, SSIM}

    private enum Preset {LOOSE, MEDIUM, STRICT, CUSTOM}

    private static final int DHASH_LOOSE = 8;
    private static final int DHASH_MEDIUM = 5;
    private static final int DHASH_STRICT = 3;

    private static final double PSNR_LOOSE = 35.0;
    private static final double PSNR_MEDIUM = 40.0;
    private static final double PSNR_STRICT = 45.0;

    private static final double SSIM_LOOSE = 0.95;
    private static final double SSIM_MEDIUM = 0.98;
    private static final double SSIM_STRICT = 0.99;

    private CurationControlsPanel curationPanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ImageVerifyTool app = new ImageVerifyTool();
            app.setVisible(true);
        });
    }

    public ImageVerifyTool() {
        super("Image Verify Tool");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(900, 720);
        setLocationRelativeTo(null);

        setJMenuBar(buildMenuBar());

        JPanel north = new JPanel(new GridBagLayout());
        north.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 0;
        gc.gridx = 0;
        gc.gridy = 0;
        north.add(new JLabel("Folder"), gc);

        tfFolder = new JTextField(40);
        gc.weightx = 1;
        gc.gridx = 1;
        north.add(tfFolder, gc);

        JButton btnBrowse = new JButton("Browse");
        btnBrowse.addActionListener(this::onBrowseFolder);
        gc.weightx = 0;
        gc.gridx = 2;
        north.add(btnBrowse, gc);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.gridwidth = 3;
        north.add(buildThresholdsPanel(), gc);

        log = new JTextArea();
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(log);

        status = new JLabel("Ready.");
        status.setBorder(new EmptyBorder(4, 8, 4, 8));

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        curationPanel = new CurationControlsPanel();
        GridBagConstraints gc2 = new GridBagConstraints();
        gc2.gridx = 0;
        gc2.gridy = 2;
        gc2.gridwidth = 3;
        gc2.fill = GridBagConstraints.HORIZONTAL;
        gc2.insets = new Insets(4, 4, 4, 4);
        north.add(curationPanel, gc2);
        applyPreset(Preset.MEDIUM);
        rbMedium.setSelected(true);
    }

    private JPanel buildThresholdsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Thresholds & Presets"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 0;
        rbLoose = new JRadioButton("Loose");
        rbMedium = new JRadioButton("Medium (default)");
        rbStrict = new JRadioButton("Strict");
        rbCustom = new JRadioButton("Custom");

        presetGroup = new ButtonGroup();
        presetGroup.add(rbLoose);
        presetGroup.add(rbMedium);
        presetGroup.add(rbStrict);
        presetGroup.add(rbCustom);

        rbLoose.addActionListener(e -> applyPreset(Preset.LOOSE));
        rbMedium.addActionListener(e -> applyPreset(Preset.MEDIUM));
        rbStrict.addActionListener(e -> applyPreset(Preset.STRICT));

        JButton btnReset = new JButton("Reset to default");
        btnReset.setToolTipText("Reset thresholds to Medium preset (dHash≤5, PSNR≥40 dB, SSIM≥0.98)");
        btnReset.addActionListener(e -> {
            applyPreset(Preset.MEDIUM);
            rbMedium.setSelected(true);
        });

        g.gridx = 0;
        g.gridy = 0;
        p.add(new JLabel("Presets:"), g);
        g.gridx = 1;
        p.add(rbLoose, g);
        g.gridx = 2;
        p.add(rbMedium, g);
        g.gridx = 3;
        p.add(rbStrict, g);
        g.gridx = 4;
        p.add(rbCustom, g);
        g.gridx = 5;
        g.weightx = 1.0;
        p.add(Box.createHorizontalGlue(), g);
        g.gridx = 6;
        g.weightx = 0;
        p.add(btnReset, g);
        Integer[] dhashOpts = {2, 3, 4, 5, 8};
        cmbDhash = new JComboBox<>(dhashOpts);
        cmbDhash.setSelectedItem(DHASH_MEDIUM);

        Double[] psnrOpts = {35.0, 40.0, 42.0, 45.0, 50.0};
        cmbPsnr = new JComboBox<>(psnrOpts);
        cmbPsnr.setSelectedItem(PSNR_MEDIUM);

        Double[] ssimOpts = {0.95, 0.97, 0.98, 0.99, 0.995};
        cmbSsim = new JComboBox<>(ssimOpts);
        cmbSsim.setSelectedItem(SSIM_MEDIUM);
        cmbDhash.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) syncPresetFromDropdowns();
        });
        cmbPsnr.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) syncPresetFromDropdowns();
        });
        cmbSsim.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) syncPresetFromDropdowns();
        });

        g.gridy = 1;
        int col = 0;
        g.gridx = col++;
        p.add(new JLabel("dHash ≤"), g);
        g.gridx = col++;
        p.add(cmbDhash, g);
        g.gridx = col++;
        p.add(new JLabel("PSNR ≥ (dB)"), g);
        g.gridx = col++;
        p.add(cmbPsnr, g);
        g.gridx = col++;
        p.add(new JLabel("SSIM ≥"), g);
        g.gridx = col++;
        p.add(cmbSsim, g);

        return p;
    }

    private void applyPreset(Preset preset) {
        updatingFromPreset = true;
        try {
            switch (preset) {
                case LOOSE:
                    cmbDhash.setSelectedItem(DHASH_LOOSE);
                    cmbPsnr.setSelectedItem(PSNR_LOOSE);
                    cmbSsim.setSelectedItem(SSIM_LOOSE);
                    rbLoose.setSelected(true);
                    break;
                case MEDIUM:
                    cmbDhash.setSelectedItem(DHASH_MEDIUM);
                    cmbPsnr.setSelectedItem(PSNR_MEDIUM);
                    cmbSsim.setSelectedItem(SSIM_MEDIUM);
                    rbMedium.setSelected(true);
                    break;
                case STRICT:
                    cmbDhash.setSelectedItem(DHASH_STRICT);
                    cmbPsnr.setSelectedItem(PSNR_STRICT);
                    cmbSsim.setSelectedItem(SSIM_STRICT);
                    rbStrict.setSelected(true);
                    break;
                case CUSTOM:
                    rbCustom.setSelected(true);
                    break;
            }
        } finally {
            updatingFromPreset = false;
        }
    }

    private void syncPresetFromDropdowns() {
        if (updatingFromPreset) return;

        int dh = (Integer) cmbDhash.getSelectedItem();
        double p = (Double) cmbPsnr.getSelectedItem();
        double s = (Double) cmbSsim.getSelectedItem();

        if (dh == DHASH_LOOSE && eq(p, PSNR_LOOSE) && eq(s, SSIM_LOOSE)) {
            applyPreset(Preset.LOOSE);
        } else if (dh == DHASH_MEDIUM && eq(p, PSNR_MEDIUM) && eq(s, SSIM_MEDIUM)) {
            applyPreset(Preset.MEDIUM);
        } else if (dh == DHASH_STRICT && eq(p, PSNR_STRICT) && eq(s, SSIM_STRICT)) {
            applyPreset(Preset.STRICT);
        } else {
            applyPreset(Preset.CUSTOM);
        }
    }

    private static boolean eq(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem miDhash = new JMenuItem("Perceptual hash (dHash)");
        miDhash.addActionListener(e -> runMetric(Metric.DHASH));
        JMenuItem miPsnr = new JMenuItem("PSNR");
        miPsnr.addActionListener(e -> runMetric(Metric.PSNR));
        JMenuItem miSsim = new JMenuItem("SSIM");
        miSsim.addActionListener(e -> runMetric(Metric.SSIM));
        JMenuItem miExit = new JMenuItem("Exit");
        miExit.addActionListener(e -> dispose());
        file.add(miDhash);
        file.add(miPsnr);
        file.add(miSsim);
        file.addSeparator();
        file.add(miExit);
        JMenu tools = new JMenu("Tools");
        JMenuItem miCuration = new JMenuItem("Apply curation rules (blur/luma)...");
        miCuration.addActionListener(e -> applyCuration());
        tools.add(miCuration);
        JMenu help = new JMenu("Help");
        JMenuItem miOverview = new JMenuItem("Metrics overview");
        miOverview.addActionListener(e -> MetricsHelp.showMetricsOverview(this));
        help.add(miOverview);

        mb.add(file);
        mb.add(tools);
        mb.add(help);
        return mb;
    }

    private void onBrowseFolder(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Folder with Images");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(true);
        int res = fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            tfFolder.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void runMetric(Metric metric) {
        File dir = new File(tfFolder.getText().trim());
        if (!dir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Please choose a valid folder.", "No folder", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<File> images = listImages(dir);
        if (images.size() < 2) {
            JOptionPane.showMessageDialog(this, "Need at least two images in the folder.", "Not enough images", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final int dhashMax = (Integer) cmbDhash.getSelectedItem();
        final double psnrMin = (Double) cmbPsnr.getSelectedItem();
        final double ssimMin = (Double) cmbSsim.getSelectedItem();

        log.setText("");
        DecimalFormat df2 = new DecimalFormat("0.00");
        append("Metric: " + metric);
        append("Preset: " + currentPresetName());
        append("Thresholds: dHash ≤ " + dhashMax + ", PSNR ≥ " + df2.format(psnrMin) + " dB, SSIM ≥ " + df2.format(ssimMin));
        append("Folder: " + dir.getAbsolutePath());
        append("Files found: " + images.size());
        append("Comparing consecutive images by sorted name...\n");

        new SwingWorker<Void, String>() {
            int nearDupCount = 0;
            long startTime;

            @Override
            protected Void doInBackground() {
                startTime = System.currentTimeMillis();
                for (int i = 0; i < images.size() - 1; i++) {
                    if (isCancelled()) break;
                    File a = images.get(i);
                    File b = images.get(i + 1);
                    try {
                        Result r = comparePair(a, b, metric, dhashMax, psnrMin, ssimMin);
                        if (r.nearDuplicate) nearDupCount++;
                        publish(String.format("%s  --vs--  %s  =>  %s  %s",
                                a.getName(), b.getName(), r.valueString,
                                r.nearDuplicate ? "[NEAR-DUP]" : ""));
                    } catch (IOException ex) {
                        publish(String.format("%s  --vs--  %s  =>  ERROR: %s",
                                a.getName(), b.getName(), ex.getMessage()));
                    }
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) append(s);
                status.setText("Running " + metric + " ...");
            }

            @Override
            protected void done() {
                long ms = System.currentTimeMillis() - startTime;
                append("\nFinished. Near-duplicates detected: " + nearDupCount);
                status.setText("Done (" + ms + " ms).");
            }
        }.execute();
    }

    private String currentPresetName() {
        if (rbLoose.isSelected()) return "Loose";
        if (rbMedium.isSelected()) return "Medium";
        if (rbStrict.isSelected()) return "Strict";
        return "Custom";
    }

    private static class Result {
        final boolean nearDuplicate;
        final String valueString;

        Result(boolean nearDuplicate, String valueString) {
            this.nearDuplicate = nearDuplicate;
            this.valueString = valueString;
        }
    }

    private Result comparePair(File fa, File fb, Metric metric,
                               int dhashMax, double psnrMin, double ssimMin) throws IOException {
        switch (metric) {
            case DHASH: {
                int dist = dHashDistance(fa, fb);
                boolean near = dist <= dhashMax;
                return new Result(near, "dHash dist=" + dist + " (≤" + dhashMax + "?)");
            }
            case PSNR: {
                double psnr = psnrRgb(ImageIO.read(fa), ImageIO.read(fb));
                boolean near = psnr >= psnrMin || Double.isInfinite(psnr);
                return new Result(near, "PSNR=" + (Double.isInfinite(psnr) ? "∞" : format2(psnr) + " dB") + " (≥" + format2(psnrMin) + " dB?)");
            }
            case SSIM: {
                double ssim = ssimGray(ImageIO.read(fa), ImageIO.read(fb));
                boolean near = ssim >= ssimMin;
                return new Result(near, "SSIM=" + format4(ssim) + " (≥" + format4(ssimMin) + "?)");
            }
        }
        throw new IllegalArgumentException("Unknown metric");
    }

    private List<File> listImages(File dir) {
        String[] exts = {"png", "jpg", "jpeg", "bmp", "gif"};
        FilenameFilter filter = (d, name) -> {
            String n = name.toLowerCase();
            for (String e : exts) if (n.endsWith("." + e)) return true;
            return false;
        };
        File[] arr = dir.listFiles((f, n) -> filter.accept(f, n));
        if (arr == null) return new ArrayList<>();
        Arrays.sort(arr, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return Arrays.asList(arr);
    }

    private void append(String s) {
        log.append(s);
        log.append("\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    public static long dHash64(BufferedImage img) {
        BufferedImage small = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, 9, 8, null);
        } finally {
            g.dispose();
        }

        long hash = 0L;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = small.getRaster().getSample(x, y, 0);
                int right = small.getRaster().getSample(x + 1, y, 0);
                hash <<= 1;
                if (left > right) hash |= 1L;
            }
        }
        return hash;
    }

    public static int hamming64(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    public static int dHashDistance(File a, File b) throws IOException {
        BufferedImage ia = ImageIO.read(a);
        BufferedImage ib = ImageIO.read(b);
        return hamming64(dHash64(ia), dHash64(ib));
    }

    public static BufferedImage resizeTo(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    public static double psnrRgb(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        if (w <= 0 || h <= 0) return Double.NaN;

        BufferedImage A = resizeTo(a, w, h);
        BufferedImage B = resizeTo(b, w, h);

        long sumSq = 0L;
        long n = (long) w * h * 3L;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ca = A.getRGB(x, y);
                int cb = B.getRGB(x, y);
                int ra = (ca >> 16) & 0xFF, ga = (ca >> 8) & 0xFF, ba = ca & 0xFF;
                int rb = (cb >> 16) & 0xFF, gb = (cb >> 8) & 0xFF, bb = cb & 0xFF;

                int dr = ra - rb, dg = ga - gb, db = ba - bb;
                sumSq += (long) dr * dr + (long) dg * dg + (long) db * db;
            }
        }

        if (sumSq == 0) return Double.POSITIVE_INFINITY;
        double mse = (double) sumSq / (double) n;
        double maxI = 255.0;
        return 10.0 * Math.log10((maxI * maxI) / mse);
    }

    public static double ssimGray(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        if (w <= 0 || h <= 0) return Double.NaN;

        BufferedImage A = resizeTo(toGray(a), w, h);
        BufferedImage B = resizeTo(toGray(b), w, h);

        int N = w * h;
        double meanA = 0, meanB = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int va = A.getRaster().getSample(x, y, 0);
                int vb = B.getRaster().getSample(x, y, 0);
                meanA += va;
                meanB += vb;
            }
        }
        meanA /= N;
        meanB /= N;
        double varA = 0, varB = 0, covAB = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int va = A.getRaster().getSample(x, y, 0);
                int vb = B.getRaster().getSample(x, y, 0);
                double da = va - meanA;
                double db = vb - meanB;
                varA += da * da;
                varB += db * db;
                covAB += da * db;
            }
        }
        varA /= (N - 1);
        varB /= (N - 1);
        covAB /= (N - 1);
        double L = 255.0;
        double K1 = 0.01, K2 = 0.03;
        double C1 = (K1 * L) * (K1 * L);
        double C2 = (K2 * L) * (K2 * L);

        double num = (2 * meanA * meanB + C1) * (2 * covAB + C2);
        double den = (meanA * meanA + meanB * meanB + C1) * (varA + varB + C2);
        return den == 0 ? 1.0 : (num / den);
    }

    public static BufferedImage toGray(BufferedImage src) {
        BufferedImage g = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D gg = g.createGraphics();
        try {
            gg.drawImage(src, 0, 0, null);
        } finally {
            gg.dispose();
        }
        return g;
    }

    private static String format2(double v) {
        return new DecimalFormat("0.00").format(v);
    }

    private static String format4(double v) {
        return new DecimalFormat("0.0000").format(v);
    }

    private void applyCuration() {
        File dir = new File(tfFolder.getText().trim());
        if (!dir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Please choose a valid folder.", "No folder", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ImageCurationRunner.Settings s = new ImageCurationRunner.Settings(
                curationPanel.isBlurEnabled(), curationPanel.getBlurThreshold(),
                curationPanel.isLumaEnabled(), curationPanel.getMinLuma(),
                curationPanel.isMoveEnabled(), curationPanel.getAcceptedSubdir(), curationPanel.getRejectedSubdir(),
                curationPanel.isManifestEnabled(), curationPanel.getManifestFilename()
        );

        log.setText("");
        append("Curation started: " + dir.getAbsolutePath());
        new SwingWorker<Void, String>() {
            int kept = 0, dropped = 0;
            long t0;

            @Override
            protected Void doInBackground() {
                t0 = System.currentTimeMillis();
                try {
                    ImageCurationRunner.run(dir, s, new ImageCurationRunner.Listener() {
                        @Override
                        public void onStart(int total) {
                            publish("Total images: " + total);
                        }

                        @Override
                        public void onProgress(int index, File file, String message) {
                            publish(String.format("%s  %s", file.getName(), message));
                        }

                        @Override
                        public void onDone(int k, int d) {
                            kept = k;
                            dropped = d;
                        }
                    });
                } catch (Exception ex) {
                    publish("ERROR: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) append(s);
            }

            @Override
            protected void done() {
                long ms = System.currentTimeMillis() - t0;
                append(String.format("Curation finished. kept=%d dropped=%d (%.1f s)", kept, dropped, ms / 1000.0));
            }
        }.execute();
    }

}

