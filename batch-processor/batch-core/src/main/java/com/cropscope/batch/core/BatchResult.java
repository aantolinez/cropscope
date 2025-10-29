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

public class BatchResult extends BatchProgress {
    public final long startedAtMs, endedAtMs;

    public BatchResult(BatchProgress p, long startedAtMs, long endedAtMs) {
        super(p.manifestsQueued, p.manifestsProcessed, p.manifestsSkipped, p.failedManifests,
                p.imagesProcessed, p.cropsQueued, p.cropsDone, p.failedCrops);
        this.startedAtMs = startedAtMs; this.endedAtMs = endedAtMs;
    }

    @Override public String toString() {
        long sec = Math.max(1L, (endedAtMs - startedAtMs)/1000L);
        return "BatchResult{manifestsQueued="+manifestsQueued+
                ", processed="+manifestsProcessed+
                ", skipped="+manifestsSkipped+
                ", failedManifests="+failedManifests+
                ", imagesProcessed="+imagesProcessed+
                ", cropsQueued="+cropsQueued+
                ", cropsDone="+cropsDone+
                ", failedCrops="+failedCrops+
                ", elapsedSec="+sec+"}";
    }
}
