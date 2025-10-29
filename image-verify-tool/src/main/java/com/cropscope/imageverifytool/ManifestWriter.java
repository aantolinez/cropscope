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


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;

public final class ManifestWriter implements Closeable, Flushable {
    private final BufferedWriter out;
    private final String[] header;
    private volatile boolean headerWritten = false;

    public static final String[] DEFAULT_HEADER = new String[]{
            "filename", "width", "height", "bytes", "sha256", "dhash", "var_laplacian",
            "mean_luma", "metric", "metric_value", "near_duplicate",
            "source_video", "timecode_sec", "decision", "reason"
    };

    public ManifestWriter(File csvFile) throws IOException {
        this(csvFile, DEFAULT_HEADER, true);
    }

    public ManifestWriter(File csvFile, String[] header, boolean overwrite) throws IOException {
        if (!overwrite && csvFile.exists())
            throw new IOException("Manifest exists: " + csvFile.getAbsolutePath());
        File parent = csvFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        this.out = Files.newBufferedWriter(csvFile.toPath(), StandardCharsets.UTF_8);
        this.header = header != null ? header.clone() : DEFAULT_HEADER;
    }

    private synchronized void ensureHeader() throws IOException {
        if (!headerWritten) {
            writeCsvLine(header);
            headerWritten = true;
        }
    }

    public synchronized void writeRow(String[] values) throws IOException {
        ensureHeader();
        if (values == null || values.length != header.length)
            throw new IllegalArgumentException("Row length mismatch.");
        writeCsvLine(values);
    }

    private void writeCsvLine(String[] cols) throws IOException {
        StringBuilder sb = new StringBuilder(256);
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(cols[i]));
        }
        sb.append('\n');
        out.write(sb.toString());
    }

    public static String csvEscape(String s) {
        if (s == null) return "";
        boolean need = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String t = s.replace("\"", "\"\"");
        return need ? "\"" + t + "\"" : t;
    }

    @Override
    public synchronized void flush() throws IOException {
        out.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        out.flush();
        out.close();
    }

    public static String sha256Hex(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            InputStream in = new BufferedInputStream(new FileInputStream(file));
            try (DigestInputStream dis = new DigestInputStream(in, md)) {
                while (dis.read(buf) != -1) {
                }
            }
            byte[] dig = md.digest();
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("SHA-256 error: " + e.getMessage(), e);
        }
    }

    public static String[] defaultRow(
            String filename, Integer width, Integer height, Long bytes, String sha256, Long dhash,
            Double varLap, Double meanLuma, String metric, String metricValue, Boolean nearDup,
            String sourceVideo, Double timecodeSec, String decision, String reason) {

        String[] row = new String[DEFAULT_HEADER.length];
        row[0] = nz(filename);
        row[1] = nz(width);
        row[2] = nz(height);
        row[3] = nz(bytes);
        row[4] = nz(sha256);
        row[5] = nz(dhash);
        row[6] = nz(varLap);
        row[7] = nz(meanLuma);
        row[8] = nz(metric);
        row[9] = nz(metricValue);
        row[10] = nz(nearDup);
        row[11] = nz(sourceVideo);
        row[12] = nz(timecodeSec);
        row[13] = nz(decision);
        row[14] = nz(reason);
        return row;
    }

    private static String nz(Object o) {
        return (o == null) ? "" : String.valueOf(o);
    }

    @Override
    public String toString() {
        return "ManifestWriter{header=" + Arrays.toString(header) + "}";
    }
}

