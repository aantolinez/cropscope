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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
public class ImageDataAugmenter {
    public enum FlipType {
        HORIZONTAL("Flipped horizontal"),
        VERTICAL("Flipped vertical"),
        BOTH("Flipped both");
        private final String description;
        FlipType(String description) {
            this.description = description;
        }
        public String getDescription() {
            return description;
        }
    }

    public BufferedImage flipHorizontal(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage flipped = new BufferedImage(width, height, image.getType());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flipped.setRGB(width - 1 - x, y, image.getRGB(x, y));
            }
        }
        return flipped;
    }

    public BufferedImage flipVertical(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage flipped = new BufferedImage(width, height, image.getType());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flipped.setRGB(x, height - 1 - y, image.getRGB(x, y));
            }
        }
        return flipped;
    }

    public BufferedImage flipBoth(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage flipped = new BufferedImage(width, height, image.getType());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flipped.setRGB(width - 1 - x, height - 1 - y, image.getRGB(x, y));
            }
        }
        return flipped;
    }

    public void saveAugmentedImage(BufferedImage image, File baseDir, String angle, FlipType flipType,
                                   String originalFileName, int width, int height, int cropIndex) throws IOException {
        File augmentedDir = new File(baseDir, "cropped" + File.separator + "augmented_data");
        File angleDir = new File(augmentedDir, angle);
        File flipTypeDir = new File(angleDir, flipType.getDescription());
        File sizeDir = new File(flipTypeDir, width + "x" + height);
        if (!sizeDir.exists()) {
            sizeDir.mkdirs();
        }
        String fileName = String.format("%s_crop_%d.png", originalFileName, cropIndex);
        File outputFile = new File(sizeDir, fileName);
        ImageIO.write(image, "png", outputFile);
    }

    public void augmentSingleCrop(BufferedImage cropImage, File baseDir, String angle,
                                  String originalFileName, int width, int height, int cropIndex) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            executor.submit(() -> {
                try {
                    BufferedImage flipped = flipHorizontal(cropImage);
                    saveAugmentedImage(flipped, baseDir, angle, FlipType.HORIZONTAL, originalFileName, width, height, cropIndex);
                } catch (IOException e) {
                    System.err.println("Error saving horizontally flipped image: " + e.getMessage());
                }
            });
            executor.submit(() -> {
                try {
                    BufferedImage flipped = flipVertical(cropImage);
                    saveAugmentedImage(flipped, baseDir, angle, FlipType.VERTICAL, originalFileName, width, height, cropIndex);
                } catch (IOException e) {
                    System.err.println("Error saving vertically flipped image: " + e.getMessage());
                }
            });
            executor.submit(() -> {
                try {
                    BufferedImage flipped = flipBoth(cropImage);
                    saveAugmentedImage(flipped, baseDir, angle, FlipType.BOTH, originalFileName, width, height, cropIndex);
                } catch (IOException e) {
                    System.err.println("Error saving both-flipped image: " + e.getMessage());
                }
            });
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            System.err.println("Data augmentation interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}