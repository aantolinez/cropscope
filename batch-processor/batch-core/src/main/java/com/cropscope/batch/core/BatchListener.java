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

package com.cropscope.batch.core;

import java.io.File;

public interface BatchListener {
    default void onStart(BatchProgress p) {}
    default void onManifestQueued(File manifest, int index, int total) {}
    default void onManifestStart(File manifest, int index, int total) {}
    default void onManifestDone(File manifest, boolean ok) {}
    default void onImageStart(String imagePath) {}
    default void onImageDone(String imagePath, int cropsOk, int cropsFail) {}
    default void onCropDone(String imagePath, String outPath) {}
    default void onProgress(BatchProgress p) {}
    default void onError(String where, String message, Throwable t) {}
    default void onComplete(BatchResult result) {}
}
