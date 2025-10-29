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

package com.cropscope.cloudstorage.service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.cropscope.cloudstorage.model.ConnectionProfile;
import com.cropscope.cloudstorage.model.StorageObjectSummary;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class S3Service implements StorageService {
    private AmazonS3 s3Client;
    private final ConnectionProfile profile;
    private volatile boolean connected = false;

    public S3Service(ConnectionProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Connection profile cannot be null");
        }
        this.profile = profile;
    }

    @Override
    public synchronized boolean connect() {
        if (connected) {
            return true;
        }
        try {
            AWSCredentials awsCreds = new BasicAWSCredentials(profile.getAccessKey(), profile.getSecretKey());
            s3Client = new AmazonS3Client(awsCreds);
            s3Client.setEndpoint(profile.getEndpoint());
            s3Client.listBuckets();
            connected = true;
            return true;
        } catch (AmazonClientException e) {
            System.err.println("AWS connection error: " + e.getMessage());
            connected = false;
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && s3Client != null;
    }

    @Override
    public synchronized void disconnect() {
        if (s3Client != null) {
            try {
                s3Client.shutdown();
            } catch (Exception e) {
                System.err.println("Error shutting down: " + e.getMessage());
            } finally {
                s3Client = null;
            }
        }
        connected = false;
    }

    @Override
    public List<String> listBuckets() {
        if (!isConnected()) return Collections.emptyList();
        try {
            return s3Client.listBuckets().stream()
                    .map(Bucket::getName)
                    .collect(Collectors.toList());
        } catch (AmazonClientException e) {
            System.err.println("List buckets failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        if (!isConnected() || bucketName == null || bucketName.trim().isEmpty()) return false;
        try {
            return s3Client.doesBucketExistV2(bucketName.trim());
        } catch (AmazonClientException e) {
            return false;
        }
    }

    @Override
    public List<StorageObjectSummary> listObjects(String bucketName) {
        if (!isConnected() || bucketName == null || bucketName.trim().isEmpty()) return Collections.emptyList();
        try {
            ObjectListing listing = s3Client.listObjects(bucketName.trim());
            return listing.getObjectSummaries().stream()
                    .map(obj -> new StorageObjectSummary(obj.getKey(), obj.getSize(), obj.getLastModified()))
                    .collect(Collectors.toList());
        } catch (AmazonClientException e) {
            System.err.println("List objects failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean createBucket(String bucketName) {
        if (!isConnected() || bucketName == null || bucketName.trim().isEmpty()) return false;
        String name = bucketName.trim();
        try {
            if (s3Client.doesBucketExistV2(name)) return false;
            s3Client.createBucket(name);
            return true;
        } catch (AmazonClientException e) {
            System.err.println("Create bucket failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteBucket(String bucketName) {
        if (!isConnected() || bucketName == null || bucketName.trim().isEmpty()) return false;
        String name = bucketName.trim();
        try {
            ObjectListing listing = s3Client.listObjects(name);
            if (!listing.getObjectSummaries().isEmpty()) return false;
            s3Client.deleteBucket(name);
            return true;
        } catch (AmazonClientException e) {
            System.err.println("Delete bucket failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean uploadFile(String bucketName, File file) {
        return uploadFile(bucketName, file, file.getName());
    }

    @Override
    public boolean uploadFile(String bucketName, File file, String objectKey) {
        if (!isConnected() || bucketName == null || file == null || objectKey == null) return false;
        if (!bucketExists(bucketName)) {
            System.err.println("Bucket does not exist: " + bucketName);
            return false;
        }
        if (!file.exists() || !file.isFile()) {
            System.err.println("File not valid: " + file.getAbsolutePath());
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.length());
            PutObjectRequest req = new PutObjectRequest(bucketName, objectKey, fis, metadata);
            s3Client.putObject(req);
            return true;
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            return false;
        } catch (AmazonClientException e) {
            System.err.println("AWS error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean downloadFile(String bucketName, String objectKey, File targetFile) {
        if (!isConnected() || bucketName == null || objectKey == null || targetFile == null) return false;
        if (!bucketExists(bucketName)) {
            System.err.println("Bucket does not exist: " + bucketName);
            return false;
        }
        try {
            if (!targetFile.getParentFile().exists() && !targetFile.getParentFile().mkdirs()) {
                System.err.println("Failed to create parent dirs: " + targetFile.getParent());
                return false;
            }
            GetObjectRequest req = new GetObjectRequest(bucketName, objectKey);
            S3Object obj = s3Client.getObject(req);
            try (InputStream in = obj.getObjectContent();
                 OutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Download IO error: " + e.getMessage());
            return false;
        } catch (AmazonClientException e) {
            System.err.println("Download AWS error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteObject(String bucketName, String objectKey) {
        if (!isConnected() || bucketName == null || objectKey == null) return false;
        try {
            s3Client.deleteObject(bucketName, objectKey);
            return true;
        } catch (AmazonClientException e) {
            System.err.println("Delete failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean renameObject(String bucketName, String oldKey, String newKey) {
        if (!isConnected() || bucketName == null || oldKey == null || newKey == null ||
                oldKey.trim().isEmpty() || newKey.trim().isEmpty() || oldKey.equals(newKey)) return false;
        try {
            ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, oldKey);
            CopyObjectRequest copyRequest = new CopyObjectRequest(bucketName, oldKey, bucketName, newKey);
            copyRequest.setNewObjectMetadata(metadata);
            s3Client.copyObject(copyRequest);
            s3Client.deleteObject(bucketName, oldKey);
            return true;
        } catch (AmazonClientException e) {
            System.err.println("Rename failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getName() {
        return profile.getName();
    }

    public boolean uploadBytes(String bucketName, String objectKey, byte[] data, String contentType) {
        if (!isConnected() || bucketName == null || objectKey == null || data == null) {
            return false;
        }
        if (!bucketExists(bucketName)) {
            System.err.println("Bucket does not exist: " + bucketName);
            return false;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(data.length);
            if (contentType != null) {
                metadata.setContentType(contentType);
            }
            PutObjectRequest req = new PutObjectRequest(bucketName, objectKey, bais, metadata);
            s3Client.putObject(req);
            return true;
        } catch (AmazonClientException e) {
            System.err.println("AWS error during byte upload: " + e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] downloadBytes(String bucketName, String objectKey) {
        return downloadBytes(bucketName, objectKey, 30);
    }

    @Override
    public byte[] downloadBytes(String bucketName, String objectKey, int timeoutSeconds) {
        if (!isConnected() || bucketName == null || objectKey == null || timeoutSeconds <= 0) {
            return null;
        }
        if (!bucketExists(bucketName)) {
            System.err.println("Bucket does not exist: " + bucketName);
            return null;
        }

        try {
            GetObjectRequest req = new GetObjectRequest(bucketName, objectKey);
            S3Object obj = s3Client.getObject(req);
            try (InputStream in = obj.getObjectContent()) {
                Future<byte[]> future = Executors.newSingleThreadExecutor().submit(() -> {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    try {
                        while ((n = in.read(buffer)) != -1) {
                            baos.write(buffer, 0, n);
                        }
                        return baos.toByteArray();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                return future.get(timeoutSeconds, TimeUnit.SECONDS);

            } catch (TimeoutException e) {
                System.err.println("Download timed out after " + timeoutSeconds + " seconds: " + objectKey);
                return null;
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Download failed: " + e.getMessage());
                return null;
            }
        } catch (AmazonClientException e) {
            System.err.println("AWS error during download: " + e.getMessage());
            return null;
        } catch (IOException e) {
            System.err.println("IO error during stream close: " + e.getMessage());
            return null;
        }
    }
}