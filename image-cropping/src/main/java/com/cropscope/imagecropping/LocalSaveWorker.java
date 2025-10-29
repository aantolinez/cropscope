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

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class LocalSaveWorker implements Runnable {
    private final ImageCroppingCore core;
    private final Runnable onSavedCallback;
    private final BlockingQueue<SaveJob> queue = new ArrayBlockingQueue<SaveJob>(1024);
    private final Thread worker;
    private volatile boolean running = true;

    public LocalSaveWorker(ImageCroppingCore core, Runnable onSavedCallback) {
        this.core = core;
        this.onSavedCallback = (onSavedCallback != null) ? onSavedCallback : new Runnable() {
            public void run() {
            }
        };
        this.worker = new Thread(this, "LocalSaveWorker");
        this.worker.setDaemon(true);
    }

    public void start() {
        worker.start();
    }

    public void stop() {
        running = false;
        worker.interrupt();
    }

    public void enqueue(BufferedImage img, String filename, ImageCroppingCore.CropMetadata meta) throws InterruptedException {
        queue.put(new SaveJob(img, filename, meta));
    }

    @Override
    public void run() {
        while (running) {
            try {
                final SaveJob job = queue.take();
                if (job == null) continue;
                final String prefix = (job.meta.annotation == null || job.meta.annotation.trim().isEmpty())
                        ? "image" : job.meta.annotation;
                final String resolution = job.meta.cropWidth + "x" + job.meta.cropHeight;

                final File saveDir = new File(core.getSaveDirectory(), prefix + "/" + resolution);
                if (!saveDir.exists()) saveDir.mkdirs();

                final File outputFile = new File(saveDir, job.filename);

                try {
                    ImageIO.write(job.img, "PNG", outputFile);
                    job.meta.savedAs = outputFile.getAbsolutePath();
                    core.addMetadataToQueue(job.meta);
                    SwingUtilities.invokeLater(onSavedCallback);
                } catch (IOException io) {
                    final String msg = "Save failed: " + io.getMessage();
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            core.setStatus(msg, 2500);
                        }
                    });
                }
            } catch (InterruptedException ie) {
                if (!running) break;
            } catch (Throwable t) {
            }
        }
    }

    private static final class SaveJob {
        final BufferedImage img;
        final String filename;
        final ImageCroppingCore.CropMetadata meta;

        SaveJob(BufferedImage img, String filename, ImageCroppingCore.CropMetadata meta) {
            this.img = img;
            this.filename = filename;
            this.meta = meta;
        }
    }
}
