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

package com.cropscope.batch.cli;

import com.cropscope.batch.core.*;

import java.io.File;

public class BatchMain {
    public static void main(String[] args) {
        File metaRoot = null, sink = null, source = null;
        int threads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors()-1, 8));
        boolean dry=false, respect=false, force=false;

        for (int i=0;i<args.length;i++){
            String a=args[i];
            if ("--meta-root".equals(a) && i+1<args.length) metaRoot = new File(args[++i]);
            else if ("--sink".equals(a) && i+1<args.length) sink = new File(args[++i]);
            else if ("--source".equals(a) && i+1<args.length) source = new File(args[++i]);
            else if ("--threads".equals(a) && i+1<args.length) { try { threads=Integer.parseInt(args[++i]); } catch(Exception ignore){} }
            else if ("--dry-run".equals(a)) dry=true;
            else if ("--respect-savedAs".equals(a)) respect=true;
            else if ("--force".equals(a)) force=true;
        }
        if (metaRoot==null) {
            System.out.println("Usage: --meta-root <dir> [--source <dir>] [--sink <dir>] [--threads N] [--dry-run] [--respect-savedAs] [--force]");
            System.exit(2);
        }

        BatchConfig cfg = new BatchConfig.Builder()
                .metaRoot(metaRoot).sourceFallback(source).sinkFallback(sink)
                .threads(threads).dryRun(dry).respectSavedAs(respect).force(force)
                .build();

        BatchProcessor proc = new BatchProcessor();
        BatchListener log = new BatchListener() {
            public void onStart(BatchProgress p){ System.out.println("Start. manifests="+p.manifestsQueued); }
            public void onManifestStart(java.io.File mf,int idx,int tot){ System.out.println("Manifest "+idx+"/"+tot+": "+mf); }
            public void onProgress(BatchProgress p){ System.out.println("Progress: cropsDone="+p.cropsDone+" failed="+p.failedCrops); }
            public void onComplete(BatchResult r){ System.out.println(r); }
        };
        BatchResult r = proc.run(cfg, log);
        if (r.failedCrops>0 || r.failedManifests>0) System.exit(1);
    }
}
