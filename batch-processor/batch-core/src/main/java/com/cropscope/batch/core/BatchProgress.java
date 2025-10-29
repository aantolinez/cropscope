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

public class BatchProgress {
    public final int manifestsQueued, manifestsProcessed, manifestsSkipped, failedManifests;
    public final int imagesProcessed, cropsQueued, cropsDone, failedCrops;

    public BatchProgress(int mq, int mp, int ms, int fm, int ip, int cq, int cd, int fc) {
        this.manifestsQueued=mq; this.manifestsProcessed=mp; this.manifestsSkipped=ms; this.failedManifests=fm;
        this.imagesProcessed=ip; this.cropsQueued=cq; this.cropsDone=cd; this.failedCrops=fc;
    }
}
