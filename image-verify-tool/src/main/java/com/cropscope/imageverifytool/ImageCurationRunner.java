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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Comparator;

public final class ImageCurationRunner {

    public static final String METRIC_NAME = "curation";

    public static class Settings {
        public final boolean useBlur;
        public final double  blurThreshold;
        public final boolean useLuma;
        public final int     minLuma;
        public final boolean doMove;
        public final String  acceptedSubdir;
        public final String  rejectedSubdir;
        public final boolean writeManifest;
        public final String  manifestFilename;

        public Settings(boolean useBlur, double blurThreshold,
                        boolean useLuma, int minLuma,
                        boolean doMove, String acceptedSubdir, String rejectedSubdir,
                        boolean writeManifest, String manifestFilename) {
            this.useBlur = useBlur;
            this.blurThreshold = blurThreshold;
            this.useLuma = useLuma;
            this.minLuma = minLuma;
            this.doMove = doMove;
            this.acceptedSubdir = acceptedSubdir;
            this.rejectedSubdir = rejectedSubdir;
            this.writeManifest = writeManifest;
            this.manifestFilename = manifestFilename;
        }
    }

    public interface Listener {
        void onStart(int total);
        void onProgress(int index, File file, String message);
        void onDone(int kept, int dropped);
    }

    public static void run(File folder, Settings s, Listener listener) throws IOException {
        String[] exts = {"png", "jpg", "jpeg", "bmp", "gif"};
        File[] files = folder.listFiles(f -> {
            if (f.isDirectory()) return false;
            String n = f.getName().toLowerCase();
            for (String e : exts) if (n.endsWith("." + e)) return true;
            return false;
        });
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        if (listener != null) listener.onStart(files.length);

        Path acceptDir = null, rejectDir = null;
        if (s.doMove) {
            acceptDir = folder.toPath().resolve(s.acceptedSubdir);
            rejectDir = folder.toPath().resolve(s.rejectedSubdir);
            Files.createDirectories(acceptDir);
            Files.createDirectories(rejectDir);
        }

        ManifestWriter mw = null;
        try {
            if (s.writeManifest) {
                File csv = new File(folder, s.manifestFilename);
                mw = new ManifestWriter(csv);
            }
            int kept = 0, dropped = 0;

            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                BufferedImage img;
                try {
                    img = ImageIO.read(f);
                    if (img == null) throw new IOException("Unsupported/empty image");
                } catch (IOException e) {
                    if (listener != null) listener.onProgress(i, f, "SKIP (read error): " + e.getMessage());
                    continue;
                }

                double varLap = FocusMeasure.varianceOfLaplacian(img);
                double meanY = FocusMeasure.meanLuma(img);

                boolean pass = true;
                StringBuilder reason = new StringBuilder();
                if (s.useBlur && varLap < s.blurThreshold) {
                    pass = false;
                    reason.append("blurry ");
                }
                if (s.useLuma && meanY < s.minLuma) {
                    pass = false;
                    reason.append("dark ");
                }
                if (s.doMove) {
                    Path target = (pass ? acceptDir : rejectDir).resolve(f.getName());
                    try {
                        Files.move(f.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException moveEx) {
                        Files.copy(f.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                        Files.delete(f.toPath());
                    }
                }
                if (mw != null) {
                    String sha = ManifestWriter.sha256Hex(new File(s.doMove
                            ? (pass ? acceptDir : rejectDir).resolve(f.getName()).toString()
                            : f.getAbsolutePath()));
                    String[] row = ManifestWriter.defaultRow(
                            f.getName(), img.getWidth(), img.getHeight(), f.length(), sha, null,
                            varLap, meanY, METRIC_NAME,
                            String.format("varLap=%.1f,meanY=%.1f", varLap, meanY),
                            pass, "", null, pass ? "keep" : "drop", pass ? "" : reason.toString().trim()
                    );
                    mw.writeRow(row);
                }

                if (pass) kept++;
                else dropped++;
                if (listener != null) {
                    listener.onProgress(i, f,
                            (pass ? "KEEP  " : "DROP  ") +
                                    String.format("varLap=%.1f meanY=%.1f %s",
                                            varLap, meanY, pass ? "" : ("(" + reason.toString().trim() + ")")));
                }
            }
            if (listener != null) listener.onDone(kept, dropped);
        } finally {
            if (mw != null) try {
                mw.close();
            } catch (IOException ignore) {
            }
        }
    }
}

