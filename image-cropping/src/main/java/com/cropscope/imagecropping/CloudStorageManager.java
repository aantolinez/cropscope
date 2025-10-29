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

import com.cropscope.cloudstorage.model.ConnectionProfile;
import com.cropscope.cloudstorage.service.StorageService;
import com.cropscope.cloudstorage.service.S3Service;
import com.cropscope.cloudstorage.service.ConnectionProfileManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class CloudStorageManager {
    private boolean useCloudStorage = false;
    private String cloudBucketName = "";
    private String cloudConnectionName = "";
    private ConnectionProfile cloudProfile = null;
    private StorageService cloudStorageService = null;

    private final BlockingQueue<CloudSaveJob> cloudSaveQueue = new ArrayBlockingQueue<CloudSaveJob>(1024);
    private Thread cloudSaverThread = null;
    private final BlockingQueue<CloudMetadataJob> cloudMetadataQueue = new ArrayBlockingQueue<CloudMetadataJob>(10);
    private Thread cloudMetadataThread = null;

    private boolean debugEnabled = true;
    private final ImageCropping imageCropping;

    public CloudStorageManager(ImageCropping imageCropping) {
        this.imageCropping = imageCropping;
    }

    public void setCloudStorageSettings(boolean useCloud, String bucketName, String connectionName) {
        this.useCloudStorage = useCloud;
        this.cloudBucketName = bucketName;
        this.cloudConnectionName = connectionName;

        if (useCloudStorage) {
            ConnectionProfileManager profileManager = new ConnectionProfileManager();
            this.cloudProfile = profileManager.getConnection(connectionName);
            if (this.cloudProfile == null) {
                debugLog("Connection profile not found: " + connectionName);
                return;
            }
        }

        debugLog("Cloud storage settings:");
        debugLog("  Use cloud: " + useCloud);
        debugLog("  Bucket: " + bucketName);
        debugLog("  Connection name: " + connectionName);
        if (cloudProfile != null) {
            debugLog("  Access key: " + (cloudProfile.getAccessKey().isEmpty() ? "NOT SET" : "SET"));
            debugLog("  Secret key: " + (cloudProfile.getSecretKey().isEmpty() ? "NOT SET" : "SET"));
            debugLog("  Region: " + cloudProfile.getRegion());
        }
        if (useCloudStorage && cloudProfile != null) {
            if (cloudSaverThread == null || !cloudSaverThread.isAlive()) {
                debugLog("Starting cloud saver thread...");
                cloudSaverThread = new Thread(new Runnable() {
                    public void run() {
                        cloudSaveWorker();
                    }
                }, "CloudCropSaver");
                cloudSaverThread.setDaemon(true);
                cloudSaverThread.start();
            }
            if (cloudMetadataThread == null || !cloudMetadataThread.isAlive()) {
                debugLog("Starting cloud metadata thread...");
                cloudMetadataThread = new Thread(new Runnable() {
                    public void run() {
                        cloudMetadataWorker();
                    }
                }, "CloudMetadataSaver");
                cloudMetadataThread.setDaemon(true);
                cloudMetadataThread.start();
            }
        }
    }

    public boolean isUseCloudStorage() {
        return useCloudStorage;
    }

    public String getCloudBucketName() {
        return cloudBucketName;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    private void debugLog(String message) {
        if (debugEnabled) {
            System.out.println("[DEBUG] " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " - " + message);
        }
    }

    public void queueCloudSave(BufferedImage img, String filename, ImageCroppingCore.CropMetadata meta, String prefix, String resolution) {
        try {
            debugLog("Adding to cloud save queue: " + filename);
            cloudSaveQueue.put(new CloudSaveJob(img, filename, meta, prefix, resolution));
        } catch (InterruptedException e) {
            debugLog("Error adding to cloud save queue: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public void queueCloudMetadataUpload(File metadataFile, String cloudFilename) {
        try {
            debugLog("Adding metadata (file) to cloud upload queue: " + cloudFilename);
            cloudMetadataQueue.put(new CloudMetadataJob(metadataFile, cloudFilename, null));
        } catch (InterruptedException e) {
            debugLog("Failed to add metadata to cloud queue: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public void queueCloudMetadataUpload(byte[] data, String cloudFilename) {
        try {
            debugLog("Adding metadata (bytes) to cloud upload queue: " + cloudFilename);
            cloudMetadataQueue.put(new CloudMetadataJob(null, cloudFilename, data));
        } catch (InterruptedException e) {
            debugLog("Failed to add metadata(bytes) to cloud queue: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public List<String> listBuckets(String connectionName) {
        ConnectionProfileManager profileManager = new ConnectionProfileManager();
        ConnectionProfile profile = profileManager.getConnection(connectionName);
        if (profile == null) {
            debugLog("Connection profile not found: " + connectionName);
            return null;
        }

        try {
            debugLog("Creating temporary S3 service to list buckets for: " + connectionName);
            StorageService tempService = new S3Service(profile);
            if (tempService.connect()) {
                debugLog("Successfully connected, listing buckets...");
                List<String> buckets = tempService.listBuckets();
                debugLog("Found " + buckets.size() + " buckets");
                tempService.disconnect();
                return buckets;
            } else {
                debugLog("Failed to connect to cloud storage");
            }
        } catch (Exception e) {
            debugLog("Error listing buckets: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private void cloudSaveWorker() {
        debugLog("Cloud save worker started");
        while (true) {
            try {
                final CloudSaveJob job = cloudSaveQueue.take();
                debugLog("Processing cloud save job: " + job.filename);
                if (cloudStorageService == null) {
                    debugLog("Initializing cloud storage service...");
                    if (cloudProfile == null) {
                        debugLog("No cloud profile available");
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                imageCropping.setStatus("No cloud profile available", 2500);
                            }
                        });
                        continue;
                    }
                    cloudStorageService = new S3Service(cloudProfile);

                    debugLog("Connecting to cloud storage...");
                    boolean connected = cloudStorageService.connect();
                    if (!connected) {
                        debugLog("Failed to connect to cloud storage");
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                imageCropping.setStatus("Failed to connect to cloud storage", 2500);
                            }
                        });
                        continue;
                    }
                    debugLog("Successfully connected to cloud storage");
                }
                String resFolder = job.resolution;
                if (job.meta != null && job.meta.cropWidth > 0 && job.meta.cropHeight > 0) {
                    resFolder = job.meta.cropWidth + "x" + job.meta.cropHeight;
                }
                final String cloudKey = job.prefix + "/" + resFolder + "/" + job.filename;
                debugLog("Cloud key: " + cloudKey);
                boolean uploaded = false;
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
                    ImageIO.write(job.img, "PNG", baos);
                    byte[] pngBytes = baos.toByteArray();
                    uploaded = cloudStorageService.uploadBytes(cloudBucketName, cloudKey, pngBytes, "image/png");
                    debugLog("uploadBytes result: " + uploaded);
                } catch (Throwable ex) {
                    debugLog("uploadBytes failed, will fallback. Cause: " + ex.getMessage());
                    uploaded = false;
                }
                if (!uploaded) {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File tempFile = new File(tempDir, job.filename);
                    debugLog("Fallback: writing temp file: " + tempFile.getAbsolutePath());
                    try {
                        boolean written = ImageIO.write(job.img, "PNG", tempFile);
                        if (!written) {
                            debugLog("Failed to write image to temporary file");
                        } else {
                            uploaded = cloudStorageService.uploadFile(cloudBucketName, tempFile, cloudKey);
                            debugLog("uploadFile result: " + uploaded);
                        }
                    } finally {
                        if (tempFile.exists() && !tempFile.delete()) {
                            debugLog("Warning: Failed to delete temporary file: " + tempFile.getAbsolutePath());
                        }
                    }
                }

                if (uploaded) {
                    debugLog("Successfully uploaded to cloud: " + cloudKey);
                    job.meta.savedAs = cloudConnectionName + "://" + cloudBucketName + "/" + cloudKey;
                    imageCropping.addMetadataToQueue(job.meta);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            imageCropping.setStatus("☁️ Uploaded to cloud: " + job.filename, 1000);
                            imageCropping.maybeAutoExport();
                        }
                    });
                } else {
                    debugLog("Failed to upload to cloud: " + cloudKey);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            imageCropping.setStatus("Cloud upload failed: " + job.filename, 2500);
                        }
                    });
                }

            } catch (InterruptedException ie) {
                debugLog("Cloud save worker interrupted");
                return;
            } catch (Exception ex) {
                debugLog("Error in cloud save worker: " + ex.getMessage());
                ex.printStackTrace();
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        imageCropping.setStatus("Cloud save error: " + ex.getMessage(), 2500);
                    }
                });
            }
        }
    }

    private void cloudMetadataWorker() {
        debugLog("Cloud metadata worker started");
        while (true) {
            try {
                final CloudMetadataJob job = cloudMetadataQueue.take();
                debugLog("Processing cloud metadata job: " + job.cloudFilename);
                if (cloudStorageService == null) {
                    debugLog("Initializing cloud storage service for metadata...");
                    if (cloudProfile == null) {
                        debugLog("No cloud profile available");
                        continue;
                    }
                    cloudStorageService = new S3Service(cloudProfile);

                    debugLog("Connecting to cloud storage for metadata...");
                    boolean connected = cloudStorageService.connect();
                    if (!connected) {
                        debugLog("Failed to connect to cloud storage for metadata");
                        continue;
                    }
                    debugLog("Successfully connected to cloud storage for metadata");
                }

                boolean uploaded = false;
                if (job.data != null) {
                    try {
                        uploaded = cloudStorageService.uploadBytes(cloudBucketName, job.cloudFilename, job.data, "application/json");
                        debugLog("Metadata uploadBytes result: " + uploaded);
                    } catch (Throwable bytesEx) {
                        debugLog("Metadata uploadBytes failed: " + bytesEx.getMessage());
                        uploaded = false;
                    }
                }
                if (!uploaded && job.metadataFile != null) {
                    try {
                        uploaded = cloudStorageService.uploadFile(cloudBucketName, job.metadataFile, job.cloudFilename);
                        debugLog("Metadata uploadFile result: " + uploaded);
                    } finally {
                        try {
                            if (job.metadataFile != null && job.metadataFile.exists() && !job.metadataFile.delete()) {
                                debugLog("Warning: Failed to delete temporary metadata file: " + job.metadataFile.getAbsolutePath());
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                }

                if (uploaded) {
                    debugLog("Successfully uploaded metadata to cloud: " + job.cloudFilename);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            imageCropping.setStatus("☁️ Metadata uploaded to cloud: " + job.cloudFilename, 2000);
                        }
                    });
                } else {
                    debugLog("Failed to upload metadata to cloud: " + job.cloudFilename);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            imageCropping.setStatus("Cloud metadata upload failed: " + job.cloudFilename, 2500);
                        }
                    });
                }

            } catch (InterruptedException ie) {
                debugLog("Cloud metadata worker interrupted");
                return;
            } catch (Exception ex) {
                debugLog("Error in cloud metadata worker: " + ex.getMessage());
                ex.printStackTrace();
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        imageCropping.setStatus("Cloud metadata error: " + ex.getMessage(), 2500);
                    }
                });
            }
        }
    }

    public void shutdown() {
        debugLog("Shutting down cloud storage manager...");
        if (cloudSaverThread != null) {
            debugLog("Interrupting cloud saver thread...");
            cloudSaverThread.interrupt();
        }
        if (cloudMetadataThread != null) {
            debugLog("Interrupting cloud metadata thread...");
            cloudMetadataThread.interrupt();
        }
        if (cloudStorageService != null) {
            debugLog("Disconnecting from cloud storage...");
            cloudStorageService.disconnect();
        }
    }

    public static class CloudSaveJob {
        final BufferedImage img;
        final String filename;
        final ImageCroppingCore.CropMetadata meta;
        final String prefix;
        final String resolution;

        CloudSaveJob(BufferedImage img, String filename, ImageCroppingCore.CropMetadata meta, String prefix, String resolution) {
            this.img = img;
            this.filename = filename;
            this.meta = meta;
            this.prefix = prefix;
            this.resolution = resolution;
        }
    }

    public static class CloudMetadataJob {
        final File metadataFile;
        final String cloudFilename;
        final byte[] data;

        CloudMetadataJob(File metadataFile, String cloudFilename, byte[] data) {
            this.metadataFile = metadataFile;
            this.cloudFilename = cloudFilename;
            this.data = data;
        }
    }
}
