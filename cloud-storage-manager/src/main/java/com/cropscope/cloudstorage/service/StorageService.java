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

import com.cropscope.cloudstorage.model.StorageObjectSummary;

import java.io.File;
import java.util.List;

public interface StorageService {

    boolean connect();

    boolean isConnected();

    void disconnect();

    List<String> listBuckets();

    boolean bucketExists(String bucketName);

    List<StorageObjectSummary> listObjects(String bucketName);

    boolean createBucket(String bucketName);

    boolean deleteBucket(String bucketName);

    boolean uploadFile(String bucketName, File file);

    boolean uploadFile(String bucketName, File file, String objectKey);

    boolean downloadFile(String bucketName, String key, File saveTo);

    boolean deleteObject(String bucketName, String key);

    boolean renameObject(String bucketName, String oldKey, String newKey);

    String getName();

    boolean uploadBytes(String bucketName, String objectKey, byte[] data, String contentType);

    byte[] downloadBytes(String bucketName, String objectKey);

    byte[] downloadBytes(String bucketName, String objectKey, int timeoutSeconds);
}