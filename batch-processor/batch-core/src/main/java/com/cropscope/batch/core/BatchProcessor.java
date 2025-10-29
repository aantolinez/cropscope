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
import org.json.JSONArray;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/** JSON-first, cancelable, parallel batch cropper (no UI). */
public class BatchProcessor {
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    // ==== Public API ====
    public Future<BatchResult> runAsync(final BatchConfig cfg, final BatchListener listener) {
        ExecutorService single = Executors.newSingleThreadExecutor();
        return single.submit(new Callable<BatchResult>() {
            public BatchResult call() throws Exception {
                try { return run(cfg, listener); }
                finally { single.shutdown(); }
            }
        });
    }
    public void cancel() { cancel.set(true); }
    public BatchResult run(BatchConfig cfg, BatchListener listener) {
        long start = System.currentTimeMillis();
        ImageIO.setUseCache(false);
        // Discover manifests
        List<File> manifests = discoverManifests(cfg.getMetaRoot());
        final Counters c = new Counters();
        c.manifestsQueued.set(manifests.size());
        if (listener != null) {
            listener.onStart(c.snapshot());
            for (int i=0;i<manifests.size();i++) listener.onManifestQueued(manifests.get(i), i+1, manifests.size());
        }
        ExecutorService pool = Executors.newFixedThreadPool(cfg.getThreads());
        try {
            for (int i=0;i<manifests.size();i++) {
                if (cancel.get()) break;
                File mf = manifests.get(i);
                if (!cfg.isForce() && isDoneMarkerPresent(mf)) {
                    c.manifestsSkipped.incrementAndGet();
                    continue;
                }
                if (listener != null) listener.onManifestStart(mf, i+1, manifests.size());
                boolean ok = true;
                try {
                    Manifest m = parseManifest(mf); // JSON-first
                    m.resolveRoots(cfg.getSourceFallback(), cfg.getSinkFallback());
                    c.cropsQueued.addAndGet(m.totalCrops());
                    // group by image
                    Map<String, List<Crop>> perImg = m.groupByImagePath();
                    List<Future<?>> futures = new ArrayList<Future<?>>();
                    for (final Map.Entry<String, List<Crop>> e : perImg.entrySet()) {
                        final String imgPath = e.getKey();
                        final List<Crop> crops = e.getValue();
                        futures.add(pool.submit(new Runnable() {
                            public void run() { processOneImage(cfg, m, imgPath, crops, c, listener); }
                        }));
                    }
                    waitAll(futures);
                    if (!cfg.isDryRun()) writeDoneMarker(mf);
                    c.manifestsProcessed.incrementAndGet();
                } catch (BadManifest ex) {
                    ok = false; c.failedManifests.incrementAndGet();
                    if (listener != null) listener.onError("manifest", mf + ": " + ex.getMessage(), ex);
                } catch (Exception ex) {
                    ok = false; c.failedManifests.incrementAndGet();
                    if (listener != null) listener.onError("manifest", mf + ": " + ex, ex);
                }
                if (listener != null) listener.onManifestDone(mf, ok);
                if (listener != null) listener.onProgress(c.snapshot());
            }
        } finally {
            pool.shutdown();
            try { pool.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignore) {}
            pool.shutdownNow();
        }
        BatchResult result = new BatchResult(c.snapshot(), start, System.currentTimeMillis());
        if (listener != null) listener.onComplete(result);
        return result;
    }
    // ==== Processing ====
    private void processOneImage(BatchConfig cfg, Manifest m, String imgPath, List<Crop> crops, Counters c, BatchListener listener) {
        if (cancel.get()) return;
        if (listener != null) listener.onImageStart(imgPath);
        File img = preferredImagePath(imgPath, m.resolvedSource);
        if (img == null || !img.isFile() || !img.canRead()) {
            c.failedCrops.addAndGet(crops.size());
            if (listener != null) listener.onError("image", "Cannot read image: " + imgPath, null);
            return;
        }
        BufferedImage src = null;
        try { src = ImageIO.read(img); } catch (Exception ignore) {}
        if (src == null) {
            c.failedCrops.addAndGet(crops.size());
            if (listener != null) listener.onError("image", "Cannot decode image: " + imgPath, null);
            return;
        }
        int ok=0, fail=0;
        for (Crop cr : crops) {
            if (cancel.get()) break;
            if (!boundsOk(src, cr)) { fail++; c.failedCrops.incrementAndGet(); continue; }
            File sink = resolveSinkDir(cr, m, cfg);
            if (sink == null) { fail++; c.failedCrops.incrementAndGet(); continue; }
            if (!cfg.isDryRun()) {
                BufferedImage out = crop(src, cr);
                try {
                    File outFile = resolveOutputFile(sink, cr, cfg.isRespectSavedAs());
                    atomicWritePng(out, outFile);
                    if (listener != null) listener.onCropDone(imgPath, outFile.getAbsolutePath());
                } catch (Exception ex) {
                    fail++; c.failedCrops.incrementAndGet();
                    if (listener != null) listener.onError("crop", "Write failed: " + ex.getMessage(), ex);
                    continue;
                }
            }
            ok++; c.cropsDone.incrementAndGet();
        }
        c.imagesProcessed.incrementAndGet();
        if (listener != null) listener.onImageDone(imgPath, ok, fail);
        if (listener != null) listener.onProgress(c.snapshot());
    }
    // ==== Model ====
    private static class Manifest {
        final String sourceDir;   // may be null/invalid
        final String sinkDir;     // may be null/invalid
        final int defaultW;
        final int defaultH;
        final JSONArray crops;
        File resolvedSource, resolvedSink;
        Manifest(JSONObject root){
            this.sourceDir = root.optString("sourceDir", null);
            this.sinkDir = root.optString("sinkDir", null);
            JSONObject dcs = root.optJSONObject("defaultCropSize");
            this.defaultW = dcs!=null ? dcs.optInt("w",0) : 0;
            this.defaultH = dcs!=null ? dcs.optInt("h",0) : 0;
            this.crops = root.getJSONArray("crops");
        }
        void resolveRoots(File sourceFallback, File sinkFallback) {
            this.resolvedSource = bestSource(sourceDir, sourceFallback);
            this.resolvedSink = bestSink(sinkDir, sinkFallback);
        }
        int totalCrops(){ return crops.length(); }
        Map<String, List<Crop>> groupByImagePath(){
            Map<String, List<Crop>> map = new LinkedHashMap<String, List<Crop>>();
            for (int i=0;i<crops.length();i++){
                JSONObject o = crops.getJSONObject(i);
                String imgPath = o.optString("imagePath", null);
                if (imgPath == null || imgPath.isEmpty()) continue;
                Crop c = Crop.fromJson(o, defaultW, defaultH);
                List<Crop> list = map.get(imgPath);
                if (list==null){ list=new ArrayList<Crop>(); map.put(imgPath, list); }
                list.add(c);
            }
            return map;
        }
    }
    private static class Crop {
        final String annotation; final String savedAs; final String sinkOverride;
        final int x1,y1,w,h;
        private Crop(String ann, String saved, String sink, int x1, int y1, int w, int h) {
            this.annotation=sanitize(ann); this.savedAs=saved; this.sinkOverride=sink;
            this.x1=x1; this.y1=y1; this.w=w; this.h=h;
        }
        static Crop fromJson(JSONObject o, int defaultW, int defaultH) {
            String ann = o.optString("annotation","Crop");
            String saved = o.optString("savedAs", null);
            String sink = o.optString("sinkDir", null);
            Integer x1 = getInt(o, "x1"), y1 = getInt(o, "y1"), w = getInt(o,"w"), h = getInt(o,"h");
            // alternative schema: cropTopLeft + cropBottomRight
            JSONObject tl = o.optJSONObject("cropTopLeft");
            JSONObject br = o.optJSONObject("cropBottomRight");
            if (x1==null && tl!=null) x1 = getInt(tl,"x");
            if (y1==null && tl!=null) y1 = getInt(tl,"y");
            if ((w==null||h==null) && br!=null && x1!=null && y1!=null) {
                Integer x2 = getInt(br,"x"), y2 = getInt(br,"y");
                if (x2!=null && y2!=null) {
                    int ww = x2 - x1 + 1, hh = y2 - y1 + 1;
                    if (w==null) w=ww; if (h==null) h=hh;
                }
            }
            if (w==null) w=defaultW; if (h==null) h=defaultH;
            if (x1==null || y1==null || w==null || h==null)
                throw new IllegalArgumentException("Missing crop coordinates/size");
            return new Crop(ann, saved, sink, x1, y1, w, h);
        }
        private static Integer getInt(JSONObject o, String k){ return o.has(k)? Integer.valueOf(o.optInt(k)):null; }
        public String toString(){ return annotation+" x1="+x1+" y1="+y1+" w="+w+" h="+h; }
    }
    // ==== I/O helpers ====
    private static List<File> discoverManifests(File root){
        List<File> out = new ArrayList<File>();
        walk(root, out);
        Collections.sort(out, new Comparator<File>() {
            public int compare(File a, File b){ return a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()); }
        });
        return out;
    }
    private static void walk(File dir, List<File> out){
        File[] list = safeListFiles(dir);
        if (list==null) return;
        Arrays.sort(list, new Comparator<File>() {
            public int compare(File a, File b){ return a.getName().compareToIgnoreCase(b.getName()); }
        });
        for (File f : list){
            if (f.isDirectory()) walk(f, out);
            else if (f.getName().startsWith("crop_metadata_") && f.getName().endsWith(".json")) out.add(f);
        }
    }
    private static class BadManifest extends Exception { BadManifest(String m){ super(m); } }
    private static Manifest parseManifest(File f) throws Exception {
        String s = readAll(f);
        JSONObject root = new JSONObject(s);
        if (!root.has("crops")) throw new BadManifest("Missing key 'crops'");
        return new Manifest(root);
    }
    private static String readAll(File f) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder(); char[] buf = new char[8192]; int n;
        try { while ((n=br.read(buf))>=0) sb.append(buf,0,n); } finally { try { br.close(); } catch (Exception ignore) {} }
        return sb.toString();
    }
    private static File[] safeListFiles(File dir){ try { return dir.listFiles(); } catch (SecurityException se){ return null; } }
    private static boolean isDoneMarkerPresent(File manifest){
        return new File(manifest.getParentFile(), manifest.getName()+".done").exists();
    }
    private static void writeDoneMarker(File manifest){
        try { new FileOutputStream(new File(manifest.getParentFile(), manifest.getName()+".done")).close(); } catch (IOException ignore){}
    }
    // ==== Path resolution ====
    private static File bestSource(String jsonSource, File fallback){
        if (jsonSource!=null && !jsonSource.trim().isEmpty()){
            File f = new File(jsonSource);
            if (f.exists() && f.isDirectory() && f.canRead()) return f;
        }
        if (fallback!=null && fallback.exists() && fallback.isDirectory() && fallback.canRead()) return fallback;
        return null;
    }
    private static File bestSink(String jsonSink, File fallback){
        if (jsonSink!=null && !jsonSink.trim().isEmpty()){
            File f = new File(jsonSink);
            if (f.exists() || f.mkdirs()) return f;
        }
        if (fallback!=null && (fallback.exists() || fallback.mkdirs())) return fallback;
        return null;
    }
    private static File preferredImagePath(String imgPath, File resolvedSource){
        File f = new File(imgPath);
        if (f.isAbsolute()) return f;
        if (resolvedSource!=null) return new File(resolvedSource, imgPath);
        return f;
    }
    private static File resolveSinkDir(Crop c, Manifest m, BatchConfig cfg){
        File first = (c.sinkOverride!=null && !c.sinkOverride.trim().isEmpty()) ? new File(c.sinkOverride) : null;
        File manifestSink = (m.sinkDir!=null && !m.sinkDir.trim().isEmpty()) ? new File(m.sinkDir) : null;
        File chosen = bestSink(pathOf(first), bestSink(pathOf(manifestSink), cfg.getSinkFallback()));
        return chosen;
    }
    private static String pathOf(File f){ return f==null? null : f.getAbsolutePath(); }
    // ==== Cropping & writing ====
    private static boolean boundsOk(BufferedImage src, Crop c){
        if (c.w<=0 || c.h<=0) return false;
        if (c.x1<0 || c.y1<0) return false;
        if (c.x1 + c.w > src.getWidth()) return false;
        if (c.y1 + c.h > src.getHeight()) return false;
        return true;
    }
    private static BufferedImage crop(BufferedImage src, Crop c){
        BufferedImage out = new BufferedImage(c.w, c.h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.drawImage(src, 0,0,c.w,c.h, c.x1,c.y1,c.x1+c.w,c.y1+c.h, null);
        } finally { g2.dispose(); }
        return out;
    }
    // minimalist per-sink numbering (thread-safe)
    private final ConcurrentMap<String, AtomicInteger> counters = new ConcurrentHashMap<String, AtomicInteger>();
    private File resolveOutputFile(File sinkDir, Crop c, boolean respectSavedAs) {
        if (respectSavedAs && c.savedAs!=null && !c.savedAs.trim().isEmpty()) {
            File f = new File(sinkDir, new File(c.savedAs).getName());
            if (!f.exists()) return f;
        }
        String key = sinkDir.getAbsolutePath() + "|" + c.annotation + "|" + c.w + "x" + c.h;
        AtomicInteger ctr = counters.computeIfAbsent(key, new java.util.function.Function<String, AtomicInteger>() {
            public AtomicInteger apply(String k){ return new AtomicInteger(0); }
        });
        while (true) {
            int n = ctr.incrementAndGet();
            File f = new File(sinkDir, String.format("%s_%dx%d_%05d.png", c.annotation, c.w, c.h, n));
            if (!f.exists()) return f;
        }
    }
    private static void atomicWritePng(BufferedImage img, File out) throws IOException {
        File parent = out.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create sink: " + parent);
        File tmp = File.createTempFile("._tmp_", ".png", parent);
        try {
            ImageIO.write(img, "PNG", tmp);
            try {
                Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tmp.exists()) try { tmp.delete(); } catch (Exception ignore) {}
        }
    }
    // ==== progress counters ====
    private static class Counters {
        final AtomicInteger manifestsQueued = new AtomicInteger();
        final AtomicInteger manifestsProcessed = new AtomicInteger();
        final AtomicInteger manifestsSkipped = new AtomicInteger();
        final AtomicInteger failedManifests = new AtomicInteger();
        final AtomicInteger imagesProcessed = new AtomicInteger();
        final AtomicInteger cropsQueued = new AtomicInteger();
        final AtomicInteger cropsDone = new AtomicInteger();
        final AtomicInteger failedCrops = new AtomicInteger();
        BatchProgress snapshot(){
            return new BatchProgress(
                    manifestsQueued.get(), manifestsProcessed.get(), manifestsSkipped.get(), failedManifests.get(),
                    imagesProcessed.get(), cropsQueued.get(), cropsDone.get(), failedCrops.get()
            );
        }
    }
    // ==== helpers added to fix compile ====
    /** Wait for all submitted tasks; individual task errors are handled inside the tasks. */
    private static void waitAll(java.util.List<java.util.concurrent.Future<?>> futures) {
        for (java.util.concurrent.Future<?> f : futures) {
            try { f.get(); } catch (Exception ignore) { /* already counted upstream */ }
        }
    }
    /** Sanitize strings for filenames (letters/digits/dot/underscore/dash only). */
    private static String sanitize(String s) {
        if (s == null || s.trim().isEmpty()) return "Crop";
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}