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

package com.cropscope.dataaugmentation.rotation;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.ArrayList;
public class ImageRotationEngine {
    private int cropWidth = 256;
    private int cropHeight = 256;
    private List<Integer> angles;
    public ImageRotationEngine(int cropWidth, int cropHeight) {
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.angles = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            this.angles.add(i * 10);
        }
    }
    public ImageRotationEngine() {
        this(256, 256);
    }

    public void setCropDimensions(int width, int height) {
        this.cropWidth = width;
        this.cropHeight = height;
    }

    public int getCropWidth() {
        return cropWidth;
    }

    public int getCropHeight() {
        return cropHeight;
    }
    public BufferedImage rotateImage(BufferedImage image, double angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        int width = image.getWidth();
        int height = image.getHeight();
        double sin = Math.abs(Math.sin(angle));
        double cos = Math.abs(Math.cos(angle));
        int newWidth = (int) (width * cos + height * sin);
        int newHeight = (int) (width * sin + height * cos);
        BufferedImage rotated = new BufferedImage(
                newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.translate(newWidth / 2.0, newHeight / 2.0);
        g2d.rotate(angle);
        g2d.drawImage(image, -width / 2, -height / 2, null);
        g2d.dispose();
        return rotated;
    }
    public BufferedImage createTransparentImage(BufferedImage rotatedImage) {
        int width = rotatedImage.getWidth();
        int height = rotatedImage.getHeight();
        BufferedImage rgbaImage;
        if (rotatedImage.getType() != BufferedImage.TYPE_INT_ARGB) {
            rgbaImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rgbaImage.createGraphics();
            g.drawImage(rotatedImage, 0, 0, null);
            g.dispose();
        } else {
            rgbaImage = rotatedImage;
        }
        int[] pixels = ((DataBufferInt) rgbaImage.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            if (r == 0 && g == 0 && b == 0) {
                pixels[i] = 0;
            }
        }
        return rgbaImage;
    }
    public BufferedImage drawCropGridWithOriginalDimensions(BufferedImage rotatedImage, int angle, int originalWidth, int originalHeight) {
        BufferedImage imageWithGrid = new BufferedImage(
                rotatedImage.getWidth(),
                rotatedImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = imageWithGrid.createGraphics();
        g2d.drawImage(rotatedImage, 0, 0, null);
        g2d.dispose();
        Map<String, Double> params = calculateCropParameters(
                angle, cropWidth, cropHeight,
                originalWidth, originalHeight);
        g2d = imageWithGrid.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (angle <= 45) {
            drawCropGridForAngleLe45(g2d, params, angle);
        } else {
            drawCropGridForAngleGt45(g2d, params, angle);
        }
        g2d.dispose();
        return imageWithGrid;
    }
    public Map<String, Double> calculateCropParameters(
            double angle, int cropWidth, int cropHeight,
            int originalWidth, int originalHeight) {
        Map<String, Double> params = new HashMap<>();
        double angleRad = Math.toRadians(angle);
        if (angle <= 45) {
            params.put("h", cropHeight * Math.cos(angleRad));
            params.put("h1", params.get("h") * Math.cos(angleRad));
            params.put("Ya", originalWidth * Math.sin(angleRad));
            params.put("x0", cropWidth * Math.tan(angleRad));
            params.put("x1", params.get("h") * Math.sin(angleRad));
            params.put("delta_y", params.get("x1") * Math.tan(angleRad));
            params.put("y1", params.get("Ya") - params.get("delta_y"));
            params.put("xb", originalWidth * Math.cos(angleRad));
            params.put("x2", params.get("xb") - params.get("h1"));
            params.put("y2", params.get("x1"));
            params.put("xmax", params.get("xb") - params.get("delta_y"));
            params.put("x3", (originalWidth * Math.cos(angleRad) +
                    originalHeight * Math.sin(angleRad)) - params.get("x1") - cropWidth);
            params.put("Yc", originalHeight * Math.cos(angleRad));
            params.put("y3", params.get("Yc") - params.get("h1"));
            params.put("Xd", originalHeight * Math.sin(angleRad));
            params.put("Yd", (originalWidth * Math.sin(angleRad) +
                    originalHeight * Math.cos(angleRad)));
            params.put("x4", params.get("Xd") - params.get("delta_y"));
            params.put("y4", params.get("Yd") - params.get("x1") - cropHeight);
            params.put("ymax", params.get("Yd") - params.get("x1"));
        } else {
            params.put("Ya", originalWidth * Math.sin(angleRad));
            params.put("h", cropHeight * Math.sin(angleRad));
            params.put("h1", params.get("h") * Math.sin(angleRad));
            params.put("x1", params.get("h") * Math.cos(angleRad));
            params.put("y1", params.get("Ya") - params.get("h1"));
            params.put("x0", cropHeight / Math.tan(angleRad));
            params.put("delta_y", cropHeight - params.get("h1"));
            params.put("Xb", originalWidth * Math.cos(angleRad));
            params.put("x2", params.get("Xb") - params.get("delta_y"));
            params.put("y2", params.get("x1"));
            params.put("xmax", params.get("x2") + cropWidth);
            params.put("Yc", originalHeight * Math.cos(angleRad));
            params.put("x3", (originalWidth * Math.cos(angleRad) +
                    originalHeight * Math.sin(angleRad)) - params.get("x1") - cropWidth);
            params.put("y3", params.get("Yc") - params.get("delta_y"));
            params.put("Yd", (originalWidth * Math.sin(angleRad) +
                    originalHeight * Math.cos(angleRad)));
            params.put("ymax", params.get("Yd") - params.get("x1"));
            params.put("Xd", originalHeight * Math.sin(angleRad));
            params.put("x4", params.get("Xd") - params.get("h1"));
            params.put("y4", params.get("Yd") - params.get("x1") - cropHeight);
        }
        return params;
    }
    private void drawCropGridForAngleLe45(Graphics2D g2d, Map<String, Double> params, int angle) {
        double l = params.get("xmax") - params.get("x1");
        int numberHorizontalCrops = (int) (l / cropWidth);
        double h = params.get("ymax") - params.get("y1");
        int numberVerticalCrops = (int) (h / cropWidth);
        for (int j = 0; j < numberVerticalCrops; j++) {
            for (int i = 0; i < numberHorizontalCrops; i++) {
                double x = (params.get("x1") + j * params.get("x0")) + i * cropWidth;
                double y = (params.get("y1") + j * cropHeight) - i * params.get("x0");
                drawCropRectangle(g2d, x, y, cropWidth, cropHeight, 0);
                if (h / cropWidth != 0) {
                    x = (params.get("x2") + j * params.get("x0")) - i * cropWidth;
                    y = (params.get("y2") + j * cropHeight) + i * params.get("x0");
                    drawCropRectangle(g2d, x, y, cropWidth, cropHeight, 1);
                }
                if (h / cropWidth != 0) {
                    x = (params.get("x3") - j * params.get("x0")) - i * cropWidth;
                    y = (params.get("y3") - j * cropHeight) + i * params.get("x0");
                    drawCropRectangle(g2d, x, y, cropWidth, cropHeight, 2);
                }
                if (h / cropWidth != 0) {
                    x = (params.get("x4") - j * params.get("x0")) + i * cropWidth;
                    y = (params.get("y4") - j * cropHeight) - i * params.get("x0");
                    drawCropRectangle(g2d, x, y, cropWidth, cropHeight, 3);
                }
            }
        }
    }
    private void drawCropGridForAngleGt45(Graphics2D g2d, Map<String, Double> params, int angle) {
        double l = params.get("y1") + cropHeight - params.get("y2");
        int numberHorizontalCrops = (int) (l / cropHeight);
        double v = params.get("x4") + cropHeight - params.get("x1");
        int numberVerticalCrops = (int) (v / cropWidth);
        for (int j = 0; j < numberVerticalCrops; j++) {
            for (int i = 0; i < numberHorizontalCrops; i++) {
                double x = params.get("x1") + j * cropWidth + i * params.get("x0");
                double y = params.get("y1") + j * params.get("x0") - i * cropHeight;
                drawCropRectangle(g2d, x, y, cropWidth, cropHeight, 0);
                if (l / cropHeight != 0) {
                    x = params.get("x2") + j * cropWidth - i * params.get("x0");
                    y = params.get("y2") + j * params.get("x0") + i * cropHeight;
                    drawCropRectangle(g2d, x, y, cropWidth, cropHeight, 1);
                }
                if (v / cropWidth != 0) {
                    x = params.get("x3") - j * cropWidth - i * params.get("x0");
                    y = params.get("y3") - j * params.get("x0") + i * cropHeight;
                    drawCropRectangle(g2d, x, y, cropWidth, cropHeight, 2);
                }
                if (v / cropWidth != 0) {
                    x = params.get("x4") - j * cropWidth + i * params.get("x0");
                    y = params.get("y4") - j * params.get("x0") - i * cropHeight;
                    drawCropRectangle(g2d, x, y, cropWidth, cropHeight, 3);
                }
            }
        }
    }
    private void drawCropRectangle(Graphics2D g2d, double x, double y, int width, int height, int region) {
        Rectangle rect = new Rectangle((int) x, (int) y, width, height);
        switch (region) {
            case 0:
                g2d.setColor(Color.BLACK);
                break;
            case 1:
                g2d.setColor(Color.BLUE);
                break;
            case 2:
                g2d.setColor(Color.YELLOW);
                break;
            case 3:
                g2d.setColor(Color.GREEN);
                break;
            default:
                g2d.setColor(Color.RED);
        }
        g2d.draw(rect);
    }
    public void saveCrops(
            String basePath,
            BufferedImage rotatedImage,
            int angle,
            int originalWidth,
            int originalHeight) throws IOException {
        Map<String, Double> params = calculateCropParameters(
                angle, cropWidth, cropHeight, originalWidth, originalHeight);
        Path outputDir = Paths.get(basePath, "cropped", "angle", String.valueOf(angle));
        Files.createDirectories(outputDir);
        if (angle <= 45) {
            double l = params.get("xmax") - params.get("x1");
            int numberHorizontalCrops = (int) (l / cropWidth);
            double h = params.get("ymax") - params.get("y1");
            int numberVerticalCrops = (int) (h / cropWidth);
            int cropCount = 0;
            for (int j = 0; j < numberVerticalCrops; j++) {
                for (int i = 0; i < numberHorizontalCrops; i++) {
                    double x = (params.get("x1") + j * params.get("x0")) + i * cropWidth;
                    double y = (params.get("y1") + j * cropHeight) - i * params.get("x0");
                    saveCroppedRegion(rotatedImage, x, y, cropWidth, cropHeight,
                            basePath, angle, j, i, cropCount++);
                    if (h / cropWidth != 0) {
                        x = (params.get("x2") + j * params.get("x0")) - i * cropWidth;
                        y = (params.get("y2") + j * cropHeight) + i * params.get("x0");
                        saveCroppedRegion(rotatedImage, x, y, cropWidth, cropHeight,
                                basePath, angle, j, i, cropCount++);
                    }
                    if (h / cropWidth != 0) {
                        x = (params.get("x3") - j * params.get("x0")) - i * cropWidth;
                        y = (params.get("y3") - j * cropHeight) + i * params.get("x0");
                        saveCroppedRegion(rotatedImage, x, y, cropWidth, cropHeight,
                                basePath, angle, j, i, cropCount++);
                        x = (params.get("x4") - j * params.get("x0")) + i * cropWidth;
                        y = (params.get("y4") - j * cropHeight) - i * params.get("x0");
                        saveCroppedRegion(rotatedImage, x, y, cropWidth, cropHeight,
                                basePath, angle, j, i, cropCount++);
                    }
                }
            }
        } else {
            double l = params.get("y1") + cropHeight - params.get("y2");
            int numberHorizontalCrops = (int) (l / cropHeight);
            double v = params.get("x4") + cropHeight - params.get("x1");
            int numberVerticalCrops = (int) (v / cropWidth);
            int cropCount = 0;
            for (int j = 0; j < numberVerticalCrops; j++) {
                for (int i = 0; i < numberHorizontalCrops; i++) {
                    double x = params.get("x1") + j * cropWidth + i * params.get("x0");
                    double y = params.get("y1") + j * params.get("x0") - i * cropHeight;
                    saveCroppedRegion(rotatedImage, x, y, cropWidth, cropHeight,
                            basePath, angle, j, i, cropCount++);
                    if (l / cropHeight != 0) {
                        x = params.get("x2") + j * cropWidth - i * params.get("x0");
                        y = params.get("y2") + j * params.get("x0") + i * cropHeight;
                        saveCroppedRegion(rotatedImage, x, y, cropWidth, cropHeight,
                                basePath, angle, j, i, cropCount++);
                    }
                    if (v / cropWidth != 0) {
                        x = params.get("x3") - j * cropWidth - i * params.get("x0");
                        y = params.get("y3") - j * params.get("x0") + i * cropHeight;
                        saveCroppedRegion(rotatedImage, x, y, cropWidth, cropHeight,
                                basePath, angle, j, i, cropCount++);
                        x = params.get("x4") - j * cropWidth + i * params.get("x0");
                        y = params.get("y4") - j * params.get("x0") - i * cropHeight;
                        saveCroppedRegion(rotatedImage, x, y, cropWidth, cropHeight,
                                basePath, angle, j, i, cropCount++);
                    }
                }
            }
        }
    }
    private void saveCroppedRegion(
            BufferedImage rotatedImage,
            double x,
            double y,
            int width,
            int height,
            String basePath,
            int angle,
            int j,
            int i,
            int cropCount) throws IOException {
        Path cropDir = Paths.get(basePath, "cropped", "angle", String.valueOf(angle),
                width + "_x_" + height);
        Files.createDirectories(cropDir);
        BufferedImage cropped = rotatedImage.getSubimage(
                (int) x, (int) y, width, height);
        if (cropped.getType() == BufferedImage.TYPE_INT_ARGB) {
            BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(cropped, 0, 0, null);
            g.dispose();
            cropped = rgb;
        }
        String filename = String.format("%d_%d_%d_%d.jpg", angle, j, i, cropCount);
        Path outputPath = cropDir.resolve(filename);
        ImageIO.write(cropped, "jpg", outputPath.toFile());
        System.out.println("Saved: " + outputPath);
    }
}