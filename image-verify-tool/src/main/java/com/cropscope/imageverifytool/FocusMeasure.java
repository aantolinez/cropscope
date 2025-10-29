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


import java.awt.*;
import java.awt.image.BufferedImage;

public final class FocusMeasure {
    private FocusMeasure() {
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

    public static double varianceOfLaplacian(BufferedImage src) {
        BufferedImage g = (src.getType() == BufferedImage.TYPE_BYTE_GRAY) ? src : toGray(src);
        final int w = g.getWidth(), h = g.getHeight();
        if (w < 3 || h < 3) return 0.0;

        final int[][] pix = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                pix[y][x] = g.getRaster().getSample(x, y, 0);

        long count = 0;
        double sum = 0.0, sum2 = 0.0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int v = (pix[y - 1][x] + pix[y + 1][x] + pix[y][x - 1] + pix[y][x + 1]) - 4 * pix[y][x];
                double d = (double) v;
                sum += d;
                sum2 += d * d;
                count++;
            }
        }
        if (count == 0) return 0.0;
        double mean = sum / (double) count;
        return (sum2 / (double) count) - (mean * mean);
    }

    public static double meanLuma(BufferedImage src) {
        BufferedImage g = (src.getType() == BufferedImage.TYPE_BYTE_GRAY) ? src : toGray(src);
        final int w = g.getWidth(), h = g.getHeight();
        long sum = 0L;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                sum += g.getRaster().getSample(x, y, 0);
        return (w == 0 || h == 0) ? 0.0 : (double) sum / (double) (w * h);
    }

    public enum FocusPreset {
        LOOSE(80.0), MEDIUM(120.0), STRICT(180.0);
        public final double threshold;

        FocusPreset(double t) {
            this.threshold = t;
        }
    }

    public static boolean isSharpEnough(BufferedImage src, double threshold) {
        return varianceOfLaplacian(src) >= threshold;
    }
}

