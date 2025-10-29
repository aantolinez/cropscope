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

package com.cropscope.batch.gui;
import com.cropscope.batch.core.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.concurrent.Future;
/**
 * Enhanced Swing GUI to run BatchProcessor with hierarchical crop support.
 * Java 1.8 compatible.
 */
public class BatchGuiMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignore) {}
                BatchRunnerFrame f = new BatchRunnerFrame();
                f.setVisible(true);
            }
        });
    }
    static class BatchRunnerFrame extends JFrame {
        private final JTextField tfMetaRoot = new JTextField(32);
        private final JTextField tfSource   = new JTextField(28);
        private final JTextField tfSink     = new JTextField(28);
        private final JSpinner spThreads   = new JSpinner(new SpinnerNumberModel(
                Math.max(1, Math.min(Runtime.getRuntime().availableProcessors()-1, 8)), 1, 64, 1));
        private final JCheckBox cbDryRun   = new JCheckBox("Dry run (no files written)");
        private final JCheckBox cbRespect  = new JCheckBox("Respect savedAs (if free)");
        private final JCheckBox cbForce    = new JCheckBox("Force (reprocess .done)");
        private final JCheckBox cbHierarchy = new JCheckBox("Enable hierarchical crops");
        private final JButton btnStart = new JButton("Start");
        private final JButton btnCancel = new JButton("Cancel");
        private final JButton btnClose = new JButton("Close");
        private final JProgressBar progress = new JProgressBar(0, 100);
        private final JLabel lblCounters = new JLabel(" ");
        private final JTextArea logArea = new JTextArea(12, 80);
        private volatile BatchProcessor currentProcessor;
        private volatile Future<BatchResult> currentFuture;
        BatchRunnerFrame() {
            super("Cropscope Batch Processor");
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { requestExit(); }
            });
            setContentPane(buildUI());
            setJMenuBar(buildMenu());
            pack();
            setLocationRelativeTo(null);
            // defaults
            cbRespect.setSelected(true);
            cbHierarchy.setSelected(true);
            btnCancel.setEnabled(false);
            progress.setStringPainted(true);
        }
        private JMenuBar buildMenu() {
            JMenuBar mb = new JMenuBar();
            JMenu file = new JMenu("File");
            JMenuItem miExit = new JMenuItem("Exit");
            miExit.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { requestExit(); }});
            file.add(miExit);
            JMenu help = new JMenu("Help");
            JMenuItem miAbout = new JMenuItem("About");
            miAbout.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JOptionPane.showMessageDialog(BatchRunnerFrame.this,
                            "Cropscope Batch GUI (Enhanced)\n" +
                                    "Runs the JSON-first batch cropper with hierarchical crop support.\n" +
                                    "JSON manifests with crop_operations will be expanded.\n" +
                                    "CLI fallbacks apply only if JSON paths are missing/invalid.",
                            "About", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            help.add(miAbout);
            mb.add(file);
            mb.add(help);
            return mb;
        }
        private JPanel buildUI() {
            JPanel root = new JPanel(new BorderLayout(10,10));
            root.setBorder(new EmptyBorder(12,12,12,12));
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4,4,4,4); gc.gridy=0; gc.anchor=GridBagConstraints.WEST;
            // Row: meta-root
            gc.gridx=0; form.add(new JLabel("Meta root:"), gc);
            gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; form.add(tfMetaRoot, gc);
            gc.gridx=2; gc.fill=GridBagConstraints.NONE; gc.weightx=0;
            JButton bMeta = new JButton("Browse...");
            bMeta.addActionListener(new ActionListener(){ public void actionPerformed(ActionEvent e){ chooseDir(tfMetaRoot); }});
            form.add(bMeta, gc);
            // Row: source fallback
            gc.gridy++; gc.gridx=0; form.add(new JLabel("Source (fallback):"), gc);
            gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; form.add(tfSource, gc);
            gc.gridx=2; gc.fill=GridBagConstraints.NONE; gc.weightx=0;
            JButton bSrc = new JButton("Browse...");
            bSrc.addActionListener(new ActionListener(){ public void actionPerformed(ActionEvent e){ chooseDir(tfSource); }});
            form.add(bSrc, gc);
            // Row: sink fallback
            gc.gridy++; gc.gridx=0; form.add(new JLabel("Sink (fallback):"), gc);
            gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; form.add(tfSink, gc);
            gc.gridx=2; gc.fill=GridBagConstraints.NONE; gc.weightx=0;
            JButton bSink = new JButton("Browse...");
            bSink.addActionListener(new ActionListener(){ public void actionPerformed(ActionEvent e){ chooseDir(tfSink); }});
            form.add(bSink, gc);
            // Row: threads + flags
            gc.gridy++; gc.gridx=0; form.add(new JLabel("Threads:"), gc);
            gc.gridx=1; form.add(spThreads, gc);
            gc.gridx=2;
            JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            flags.add(cbDryRun); flags.add(cbRespect); flags.add(cbForce);
            flags.add(cbHierarchy);
            gc.gridwidth=3; gc.gridx=0; gc.gridy++; form.add(flags, gc);
            gc.gridwidth=1;
            // Buttons
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            btns.add(btnStart);
            btns.add(btnCancel);
            btns.add(btnClose);
            btnStart.addActionListener(new ActionListener(){ public void actionPerformed(ActionEvent e){ startRun(); }});
            btnCancel.addActionListener(new ActionListener(){ public void actionPerformed(ActionEvent e){ cancelRun(); }});
            btnClose.addActionListener(new ActionListener(){ public void actionPerformed(ActionEvent e){ requestExit(); }});
            // Progress / log
            JPanel south = new JPanel(new BorderLayout(6,6));
            south.add(progress, BorderLayout.NORTH);
            south.add(new JScrollPane(logArea,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
            south.add(lblCounters, BorderLayout.SOUTH);
            root.add(form, BorderLayout.NORTH);
            root.add(south, BorderLayout.CENTER);
            root.add(btns, BorderLayout.SOUTH);
            return root;
        }
        private void chooseDir(JTextField target) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String cur = target.getText().trim();
            if (!cur.isEmpty()) fc.setCurrentDirectory(new File(cur));
            int r = fc.showOpenDialog(this);
            if (r == JFileChooser.APPROVE_OPTION) target.setText(fc.getSelectedFile().getAbsolutePath());
        }
        private void startRun() {
            File meta = s2f(tfMetaRoot.getText());
            if (meta == null || !meta.isDirectory()) {
                JOptionPane.showMessageDialog(this, "Please select a valid Meta root directory.", "Invalid input", JOptionPane.WARNING_MESSAGE);
                return;
            }
            File src  = s2f(tfSource.getText());
            File sink = s2f(tfSink.getText());
            int threads = ((Number)spThreads.getValue()).intValue();
            setControlsEnabled(false);
            logArea.setText("");
            progress.setValue(0);
            progress.setIndeterminate(true);
            lblCounters.setText("Starting...");
            final BatchConfig cfg = new BatchConfig.Builder()
                    .metaRoot(meta)
                    .sourceFallback(src)
                    .sinkFallback(sink)
                    .threads(threads)
                    .dryRun(cbDryRun.isSelected())
                    .respectSavedAs(cbRespect.isSelected())
                    .force(cbForce.isSelected())
                    .hierarchyEnabled(cbHierarchy.isSelected())
                    .build();
            final BatchProcessor proc = new BatchProcessor();
            this.currentProcessor = proc;
            BatchListener listener = new BatchListener() {
                public void onStart(final BatchProgress p) {
                    swing(new Runnable(){ public void run(){
                        append("Start. manifestsQueued=" + p.manifestsQueued);
                    }});
                }
                public void onManifestQueued(final File mf, final int idx, final int total) {
                    swing(new Runnable(){ public void run(){
                        append("Queued [" + idx + "/" + total + "]: " + mf.getAbsolutePath());
                    }});
                }
                public void onManifestStart(final File mf, final int idx, final int total) {
                    swing(new Runnable(){ public void run(){
                        append("Processing [" + idx + "/" + total + "]: " + mf.getAbsolutePath());
                        append("Hierarchy mode: " + (cfg.isHierarchyEnabled() ? "ON" : "OFF"));
                    }});
                }
                public void onImageStart(final String imagePath) {
                    swing(new Runnable(){ public void run(){ append("Image: " + imagePath); }});
                }
                public void onImageDone(final String imagePath, final int ok, final int fail) {
                    swing(new Runnable(){ public void run(){ append("  -> crops OK=" + ok + " FAIL=" + fail); }});
                }
                public void onCropDone(final String imagePath, final String outPath) {
                    swing(new Runnable(){ public void run(){ append("    saved: " + outPath); }});
                }
                public void onProgress(final BatchProgress p) {
                    swing(new Runnable(){ public void run(){
                        updateCounters(p, cfg.isHierarchyEnabled());
                    }});
                }
                public void onError(final String where, final String message, final Throwable t) {
                    swing(new Runnable(){ public void run(){
                        append("[ERROR][" + where + "] " + message);
                        if (t != null) {
                            append("  Stack trace: " + t.getMessage());
                        }
                    }});
                }
                public void onComplete(final BatchResult r) {
                    swing(new Runnable(){ public void run(){
                        append("Complete. " + r.toString());
                        progress.setIndeterminate(false);
                        progress.setValue(100);
                        setControlsEnabled(true);
                        currentProcessor = null;
                        currentFuture = null;
                    }});
                }
            };
            // Run off-EDT
            currentFuture = proc.runAsync(cfg, listener);
            btnCancel.setEnabled(true);
        }
        private void cancelRun() {
            if (currentProcessor != null) {
                currentProcessor.cancel();
                append("Cancellation requested…");
                btnCancel.setEnabled(false);
            }
        }
        private void requestExit() {
            if (currentProcessor != null) {
                int c = JOptionPane.showConfirmDialog(this,
                        "A batch is running. Cancel and exit?",
                        "Confirm exit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (c != JOptionPane.YES_OPTION) return;
                cancelRun();
            }
            dispose();
            System.exit(0);
        }
        private void setControlsEnabled(boolean enabled) {
            tfMetaRoot.setEnabled(enabled);
            tfSource.setEnabled(enabled);
            tfSink.setEnabled(enabled);
            spThreads.setEnabled(enabled);
            cbDryRun.setEnabled(enabled);
            cbRespect.setEnabled(enabled);
            cbForce.setEnabled(enabled);
            cbHierarchy.setEnabled(enabled);
            btnStart.setEnabled(enabled);
            btnClose.setEnabled(enabled);
            btnCancel.setEnabled(!enabled); // opposite
        }
        private void updateCounters(BatchProgress p, boolean hierarchyEnabled) {
            // Make progress determinate once we know cropsQueued
            if (p.cropsQueued > 0) {
                progress.setIndeterminate(false);
                int done = p.cropsDone + p.failedCrops;
                int pct = Math.min(100, Math.max(0, (int)Math.round(100.0 * done / p.cropsQueued)));
                progress.setValue(pct);
            } else {
                progress.setIndeterminate(true);
            }
            String hierarchyStatus = hierarchyEnabled ? "(primary + sub-crops)" : "(primary only)";
            lblCounters.setText(
                    "Manifests: queued=" + p.manifestsQueued +
                            " processed=" + p.manifestsProcessed +
                            " skipped=" + p.manifestsSkipped +
                            " failed=" + p.failedManifests +
                            " | Images: " + p.imagesProcessed +
                            " | Crops: done=" + p.cropsDone + " failed=" + p.failedCrops +
                            hierarchyStatus +
                            (p.cropsQueued>0 ? " / total=" + p.cropsQueued : "")
            );
        }
        private void append(String s) {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
        private static File s2f(String s) {
            s = s==null ? "" : s.trim();
            return s.isEmpty() ? null : new File(s);
        }
        private static void swing(Runnable r) { SwingUtilities.invokeLater(r); }
    }
}