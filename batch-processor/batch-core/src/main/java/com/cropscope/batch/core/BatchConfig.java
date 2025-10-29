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
public class BatchConfig {
    private final File metaRoot;
    private final File sourceFallback;
    private final File sinkFallback;
    private final int threads;
    private final boolean dryRun;
    private final boolean respectSavedAs;
    private final boolean force;
    private final boolean hierarchyEnabled;
    private BatchConfig(Builder b) {
        this.metaRoot = b.metaRoot;
        this.sourceFallback = b.sourceFallback;
        this.sinkFallback = b.sinkFallback;
        this.threads = b.threads;
        this.dryRun = b.dryRun;
        this.respectSavedAs = b.respectSavedAs;
        this.force = b.force;
        this.hierarchyEnabled = b.hierarchyEnabled;
    }
    public File getMetaRoot() { return metaRoot; }
    public File getSourceFallback() { return sourceFallback; }
    public File getSinkFallback() { return sinkFallback; }
    public int getThreads() { return threads; }
    public boolean isDryRun() { return dryRun; }
    public boolean isRespectSavedAs() { return respectSavedAs; }
    public boolean isForce() { return force; }
    public boolean isHierarchyEnabled() { return hierarchyEnabled; }
    public static class Builder {
        private File metaRoot, sourceFallback, sinkFallback;
        private int threads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() - 1, 8));
        private boolean dryRun = false, respectSavedAs = false, force = false, hierarchyEnabled = false;
        public Builder metaRoot(File f){ this.metaRoot=f; return this; }
        public Builder sourceFallback(File f){ this.sourceFallback=f; return this; }
        public Builder sinkFallback(File f){ this.sinkFallback=f; return this; }
        public Builder threads(int n){ this.threads=Math.max(1,n); return this; }
        public Builder dryRun(boolean b){ this.dryRun=b; return this; }
        public Builder respectSavedAs(boolean b){ this.respectSavedAs=b; return this; }
        public Builder force(boolean b){ this.force=b; return this; }
        public Builder hierarchyEnabled(boolean b){ this.hierarchyEnabled=b; return this; }
        public BatchConfig build() {
            if (metaRoot == null) throw new IllegalArgumentException("metaRoot required");
            return new BatchConfig(this);
        }
    }
}