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

import com.cropscope.cloudbackuptool.ImageCroppingCore;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executor;

import org.json.JSONArray;
import org.json.JSONObject;

public class CropEngine {

    private final File saveDirectory;
    private final Map<String, Integer> sequenceCounters = new HashMap<String, Integer>();

    public CropEngine(File saveDirectory) {
        this.saveDirectory = saveDirectory;
    }

    public ImageCroppingCore.CropResult performPrimaryCrop(
            Point mousePos,
            ImageCroppingCore.ImagePanel.ScaledSnapshot snap,
            BufferedImage originalImage,
            File currentImageFile,
            File sourceRootDir,
            File sinkDirectory,
            int cropWidth,
            int cropHeight,
            String projectName,
            String userName,
            String annotationPrefix
    ) {
        HierarchicalCropResult batch = performHierarchicalCrop(
                mousePos, snap, originalImage, currentImageFile, sourceRootDir, sinkDirectory,
                cropWidth, cropHeight, projectName, userName, annotationPrefix,
                false, false, false
        );
        return (batch != null ? batch.primary : null);
    }

    public HierarchicalCropResult performHierarchicalCrop(
            Point mousePos,
            ImageCroppingCore.ImagePanel.ScaledSnapshot snap,
            BufferedImage originalImage,
            File currentImageFile,
            File sourceRootDir,
            File sinkDirectory,
            int cropWidth,
            int cropHeight,
            String projectName,
            String userName,
            String annotationPrefix,
            boolean doSecondary,
            boolean doTertiary,
            boolean renderSubImages
    ) {
        if (snap == null || mousePos == null || originalImage == null || currentImageFile == null) return null;
        PrimaryRect primary = computePrimaryRect(mousePos, snap,
                originalImage.getWidth(), originalImage.getHeight(), cropWidth, cropHeight);
        if (primary == null) return null;
        String primaryRes = cropWidth + "x" + cropHeight;
        String primaryFilename = formatFilename(annotationPrefix, primaryRes,
                nextSequentialNumber(annotationPrefix, cropWidth, cropHeight));

        ImageCroppingCore.CropMetadata meta = baseMetadata(
                projectName, userName, sourceRootDir, sinkDirectory, currentImageFile,
                originalImage.getWidth(), originalImage.getHeight(),
                primary.rect.x, primary.rect.y, cropWidth, cropHeight, annotationPrefix
        );
        JSONArray cropOps = new JSONArray();

        JSONObject level0 = new JSONObject();
        level0.put("level", 0);
        level0.put("dimensions", primaryRes);
        level0.put("crop_area", rectJson(primary.rect));
        cropOps.put(level0);

        final List<ImageCroppingCore.CropResult> secondary = new ArrayList<ImageCroppingCore.CropResult>();
        final List<ImageCroppingCore.CropResult> tertiary = new ArrayList<ImageCroppingCore.CropResult>();

        if (doSecondary) {
            int cols = 2, rows = 2;
            TileSpec t = tileSpec(primary.rect.width, primary.rect.height, cols, rows);
            JSONArray secAreas = new JSONArray();

            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++) {
                    Rectangle sub = new Rectangle(primary.rect.x + c * t.tileW, primary.rect.y + r * t.tileH, t.tileW, t.tileH);
                    secAreas.put(rectJson(sub));
                    if (renderSubImages) {
                        String res = t.tileW + "x" + t.tileH;
                        String fn = formatFilename(annotationPrefix, res, nextSequentialNumber(annotationPrefix, t.tileW, t.tileH));
                        BufferedImage img = blit(originalImage, sub);
                        ImageCroppingCore.CropMetadata sm = baseMetadata(
                                projectName, userName, sourceRootDir, sinkDirectory, currentImageFile,
                                originalImage.getWidth(), originalImage.getHeight(),
                                sub.x, sub.y, sub.width, sub.height, annotationPrefix
                        );
                        secondary.add(new ImageCroppingCore.CropResult(img, fn, sm));
                    }
                }

            JSONObject level1 = new JSONObject();
            level1.put("level", 1);
            level1.put("dimensions", t.tileW + "x" + t.tileH);
            level1.put("crop_areas", secAreas);
            cropOps.put(level1);
        }

        if (doTertiary) {
            int cols = 4, rows = 4;
            TileSpec t = tileSpec(primary.rect.width, primary.rect.height, cols, rows);
            JSONArray terAreas = new JSONArray();

            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++) {
                    Rectangle sub = new Rectangle(primary.rect.x + c * t.tileW, primary.rect.y + r * t.tileH, t.tileW, t.tileH);
                    terAreas.put(rectJson(sub));
                    if (renderSubImages) {
                        String res = t.tileW + "x" + t.tileH;
                        String fn = formatFilename(annotationPrefix, res, nextSequentialNumber(annotationPrefix, t.tileW, t.tileH));
                        BufferedImage img = blit(originalImage, sub);
                        ImageCroppingCore.CropMetadata sm = baseMetadata(
                                projectName, userName, sourceRootDir, sinkDirectory, currentImageFile,
                                originalImage.getWidth(), originalImage.getHeight(),
                                sub.x, sub.y, sub.width, sub.height, annotationPrefix
                        );
                        tertiary.add(new ImageCroppingCore.CropResult(img, fn, sm));
                    }
                }

            JSONObject level2 = new JSONObject();
            level2.put("level", 2);
            level2.put("dimensions", t.tileW + "x" + t.tileH);
            level2.put("crop_areas", terAreas);
            cropOps.put(level2);
        }
        meta_extra_setCropOperations(meta, cropOps);
        BufferedImage primaryImg = blit(originalImage, primary.rect);
        ImageCroppingCore.CropResult primaryResult =
                new ImageCroppingCore.CropResult(primaryImg, primaryFilename, meta);

        return new HierarchicalCropResult(primaryResult, secondary, tertiary);
    }

    public void performHierarchicalCropAsync(
            final Point mousePos,
            final ImageCroppingCore.ImagePanel.ScaledSnapshot snap,
            final BufferedImage originalImage,
            final File currentImageFile,
            final File sourceRootDir,
            final File sinkDirectory,
            final int cropWidth,
            final int cropHeight,
            final String projectName,
            final String userName,
            final String annotationPrefix,
            final boolean doSecondary,
            final boolean doTertiary,
            final boolean renderSubImages,
            Executor executor,
            final HierarchyCallback callback
    ) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    HierarchicalCropResult r = performHierarchicalCrop(
                            mousePos, snap, originalImage, currentImageFile, sourceRootDir, sinkDirectory,
                            cropWidth, cropHeight, projectName, userName, annotationPrefix,
                            doSecondary, doTertiary, renderSubImages
                    );
                    callback.onDone(r);
                } catch (Throwable t) {
                    callback.onError(t);
                }
            }
        });
    }

    public interface HierarchyCallback {
        void onDone(HierarchicalCropResult result);

        void onError(Throwable error);
    }

    public static class HierarchicalCropResult {
        public final ImageCroppingCore.CropResult primary;
        public final List<ImageCroppingCore.CropResult> secondary;
        public final List<ImageCroppingCore.CropResult> tertiary;

        public HierarchicalCropResult(ImageCroppingCore.CropResult p,
                                      List<ImageCroppingCore.CropResult> s,
                                      List<ImageCroppingCore.CropResult> t) {
            this.primary = p;
            this.secondary = (s == null ? Collections.<ImageCroppingCore.CropResult>emptyList() : s);
            this.tertiary = (t == null ? Collections.<ImageCroppingCore.CropResult>emptyList() : t);
        }
    }

    private static class PrimaryRect {
        final Rectangle rect;

        PrimaryRect(Rectangle r) {
            this.rect = r;
        }
    }

    private static class TileSpec {
        final int tileW, tileH;

        TileSpec(int w, int h) {
            this.tileW = w;
            this.tileH = h;
        }
    }

    private PrimaryRect computePrimaryRect(Point mousePos,
                                           ImageCroppingCore.ImagePanel.ScaledSnapshot snap,
                                           int imageW, int imageH,
                                           int cropW, int cropH) {

        if (mousePos.x < snap.imgX || mousePos.y < snap.imgY ||
                mousePos.x >= snap.imgX + snap.dispW || mousePos.y >= snap.imgY + snap.dispH) {
            return null;
        }

        int relX = mousePos.x - snap.imgX;
        int relY = mousePos.y - snap.imgY;
        double scaleX = (double) imageW / (double) snap.dispW;
        double scaleY = (double) imageH / (double) snap.dispH;

        int origX = (int) Math.floor((relX - (cropW / 2.0)) * scaleX);
        int origY = (int) Math.floor((relY - (cropH / 2.0)) * scaleY);

        if (origX < 0) origX = 0;
        if (origY < 0) origY = 0;
        if (origX > imageW - cropW) origX = imageW - cropW;
        if (origY > imageH - cropH) origY = imageH - cropH;

        return new PrimaryRect(new Rectangle(origX, origY, cropW, cropH));
    }

    private BufferedImage blit(BufferedImage src, Rectangle r) {
        BufferedImage out = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.drawImage(src, 0, 0, r.width, r.height, r.x, r.y, r.x + r.width, r.y + r.height, null);
        } finally {
            g2.dispose();
        }
        return out;
    }

    private TileSpec tileSpec(int primaryW, int primaryH, int cols, int rows) {
        int w = Math.max(1, primaryW / cols);
        int h = Math.max(1, primaryH / rows);
        return new TileSpec(w, h);
    }

    private JSONObject rectJson(Rectangle r) {
        JSONObject o = new JSONObject();
        o.put("x", r.x);
        o.put("y", r.y);
        o.put("width", r.width);
        o.put("height", r.height);
        return o;
    }

    private ImageCroppingCore.CropMetadata baseMetadata(
            String projectName, String userName, File sourceRootDir, File sinkDirectory,
            File currentImageFile, int imageW, int imageH, int x, int y, int w, int h, String annotationPrefix
    ) {
        ImageCroppingCore.CropMetadata m = new ImageCroppingCore.CropMetadata();
        m.project = projectName;
        m.user = userName;
        m.sourceDir = (sourceRootDir != null ? sourceRootDir.getAbsolutePath() : "");
        m.sinkDir = (sinkDirectory != null ? sinkDirectory.getAbsolutePath() : "");
        m.imageName = currentImageFile.getName();
        m.imagePath = currentImageFile.getAbsolutePath();
        m.imageWidth = imageW;
        m.imageHeight = imageH;
        m.cropX1 = x;
        m.cropY1 = y;
        m.cropWidth = w;
        m.cropHeight = h;
        m.cropX2 = x + w - 1;
        m.cropY2 = y + h - 1;
        m.annotation = annotationPrefix;
        m.nx1 = m.cropX1 / (double) imageW;
        m.ny1 = m.cropY1 / (double) imageH;
        m.nx2 = m.cropX2 / (double) imageW;
        m.ny2 = m.cropY2 / (double) imageH;
        m.designated = false;
        return m;
    }

    private void meta_extra_setCropOperations(ImageCroppingCore.CropMetadata meta, JSONArray ops) {
        try {
            Field f = ImageCroppingCore.CropMetadata.class.getDeclaredField("cropOperations");
            f.setAccessible(true);
            f.set(meta, ops);
        } catch (Throwable ignore) {
        }
    }

    private synchronized int nextSequentialNumber(String prefix, int w, int h) {
        final String key = (prefix == null ? "" : prefix) + "|" + w + "x" + h;
        Integer cur = sequenceCounters.get(key);
        if (cur == null) {
            int seeded = scanMaxNumber(prefix, w, h);
            sequenceCounters.put(key, Integer.valueOf(seeded));
            cur = Integer.valueOf(seeded);
        }
        int next = cur.intValue() + 1;
        sequenceCounters.put(key, Integer.valueOf(next));
        return next;
    }

    private int scanMaxNumber(final String prefix, int w, int h) {
        if (saveDirectory == null || !saveDirectory.isDirectory()) return 0;
        final String res = w + "x" + h;
        final String prefixRes = (prefix == null ? "" : prefix) + "_" + res + "_";

        File[] files = saveDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.startsWith(prefixRes.toLowerCase(Locale.ROOT)) && lower.endsWith(".png");
            }
        });

        int max = 0;
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                String base = files[i].getName();
                int us = base.lastIndexOf('_');
                int dot = base.lastIndexOf('.');
                if (us >= 0 && dot > us) {
                    String numStr = base.substring(us + 1, dot).replaceAll("\\D", "");
                    if (!numStr.isEmpty()) {
                        try {
                            int num = Integer.parseInt(numStr);
                            if (num > max) max = num;
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
            }
        }
        return max;
    }

    private String formatFilename(String prefix, String res, int seq) {
        return String.format("%s_%s_%05d.png", prefix, res, Integer.valueOf(seq));
    }
}
