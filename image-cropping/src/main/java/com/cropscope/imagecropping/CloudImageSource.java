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
import com.cropscope.cloudstorage.model.StorageObjectSummary;
import com.cropscope.cloudstorage.service.ConnectionProfileManager;
import com.cropscope.cloudstorage.service.S3Service;
import com.cropscope.cloudstorage.service.StorageService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CloudImageSource {

    public interface ProgressListener {
        default void onStart(int total) {
        }

        default void onItem(int done, int total, String key, File localFile, boolean downloaded) {
        }

        default void onFinish(MirrorSummary summary) {
        }
    }

    public static final class MirrorSummary {
        public final int total;
        public final int downloaded;
        public final int skipped;
        public final int failed;
        public final File localRoot;

        public MirrorSummary(int total, int downloaded, int skipped, int failed, File localRoot) {
            this.total = total;
            this.downloaded = downloaded;
            this.skipped = skipped;
            this.failed = failed;
            this.localRoot = localRoot;
        }

        @Override
        public String toString() {
            return "MirrorSummary{total=" + total +
                    ", downloaded=" + downloaded +
                    ", skipped=" + skipped +
                    ", failed=" + failed +
                    ", localRoot=" + (localRoot == null ? "null" : localRoot.getAbsolutePath()) + "}";
        }
    }

    public CloudImageSource(String connectionName,
                            String bucket,
                            String scopeString,
                            File cacheRoot,
                            int timeoutSeconds) {
        this.connectionName = Objects.requireNonNull(connectionName, "connectionName");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        parseScope(scopeString);
        this.tailExec = Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "CloudMirrorTail");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public synchronized void connect() throws Exception {
        if (storage != null) return;
        ConnectionProfile prof = new ConnectionProfileManager().getConnection(connectionName);
        if (prof == null) throw new IllegalStateException("Connection profile not found: " + connectionName);
        StorageService svc = new S3Service(prof);
        if (!svc.connect()) throw new IllegalStateException("Failed to connect using profile: " + connectionName);
        storage = svc;
    }

    public synchronized void disconnect() {
        if (storage != null) {
            try {
                storage.disconnect();
            } catch (Throwable ignore) {
            }
            storage = null;
        }
    }

    public void shutdown() {
        stopTail.set(true);
        try {
            tailExec.shutdownNow();
        } catch (Throwable ignore) {
        }
        disconnect();
    }

    public synchronized void index() throws Exception {
        ensureConnected();
        List<StorageObjectSummary> all = storage.listObjects(bucket);
        List<String> keys = new ArrayList<String>();
        String p = this.prefix;
        for (int i = 0; i < all.size(); i++) {
            StorageObjectSummary s = all.get(i);
            String k = s.getKey();
            if (k == null || k.isEmpty()) continue;
            if (!p.isEmpty() && !k.startsWith(p)) continue;
            if (!isImageKey(k)) continue;
            keys.add(k);
        }
        Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
        this.imageKeys = keys;
        this.localRoot = buildLocalRoot(cacheRoot, bucket, prefix);
        if (!localRoot.exists() && !localRoot.mkdirs()) {
            throw new IllegalStateException("Cannot create local cache root: " + localRoot.getAbsolutePath());
        }
    }

    public synchronized List<String> listImageKeys() {
        if (imageKeys == null) return Collections.<String>emptyList();
        return new ArrayList<String>(imageKeys);
    }

    public MirrorSummary mirrorHeadBlocking(int headCount, ProgressListener listener) throws Exception {
        ensureIndexed();
        int total = imageKeys.size();
        if (listener != null) listener.onStart(Math.min(headCount, total));

        int downloaded = 0, skipped = 0, failed = 0;
        int limit = Math.min(Math.max(0, headCount), total);

        for (int i = 0; i < limit; i++) {
            String key = imageKeys.get(i);
            File dest = mapKeyToLocalFile(key);
            boolean ok = mirrorOne(key, dest);
            if (ok) {
                if (dest.exists() && dest.length() > 0) {
                    if (listener != null) listener.onItem(i + 1, limit, key, dest, true);
                    downloaded++;
                } else {
                    if (listener != null) listener.onItem(i + 1, limit, key, dest, false);
                    failed++;
                }
            } else {
                if (dest.exists() && dest.length() > 0) {
                    if (listener != null) listener.onItem(i + 1, limit, key, dest, false);
                    skipped++;
                } else {
                    if (listener != null) listener.onItem(i + 1, limit, key, dest, false);
                    failed++;
                }
            }
        }

        MirrorSummary sum = new MirrorSummary(limit, downloaded, skipped, failed, localRoot);
        if (listener != null) listener.onFinish(sum);
        return sum;
    }

    public void mirrorTailAsync(final ProgressListener listener) {
        ensureIndexed();
        if (tailStarted.compareAndSet(false, true)) {
            tailExec.submit(new Runnable() {
                public void run() {
                    final int total = imageKeys.size();
                    if (listener != null) listener.onStart(total);

                    int downloaded = 0, skipped = 0, failed = 0;
                    for (int i = 0; i < total && !stopTail.get(); i++) {
                        String key = imageKeys.get(i);
                        File dest = mapKeyToLocalFile(key);
                        boolean ok = mirrorOne(key, dest);
                        if (ok) {
                            if (dest.exists() && dest.length() > 0) downloaded++;
                            else failed++;
                        } else {
                            if (dest.exists() && dest.length() > 0) skipped++;
                            else failed++;
                        }
                        if (listener != null) listener.onItem(i + 1, total, key, dest, ok);
                    }
                    if (listener != null) {
                        listener.onFinish(new MirrorSummary(total, downloaded, skipped, failed, localRoot));
                    }
                }
            });
        }
    }

    public File getLocalRoot() {
        return localRoot;
    }

    private static final Set<String> IMAGE_EXTS = new HashSet<String>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp"
    ));

    private final String connectionName;
    private final String bucket;
    private final File cacheRoot;
    private final int timeoutSeconds;

    private String prefix = "";

    private volatile StorageService storage;
    private volatile List<String> imageKeys;
    private volatile File localRoot;

    private final ExecutorService tailExec;
    private final AtomicBoolean tailStarted = new AtomicBoolean(false);
    private final AtomicBoolean stopTail = new AtomicBoolean(false);

    private void ensureConnected() {
        if (storage == null) throw new IllegalStateException("Not connected. Call connect() first.");
    }

    private void ensureIndexed() {
        if (imageKeys == null) throw new IllegalStateException("Not indexed. Call index() first.");
    }

    private static boolean isImageKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTS) {
            if (k.endsWith(ext)) return true;
        }
        return false;
    }

    private void parseScope(String scopeString) {
        String raw = scopeString == null ? "" : scopeString.trim();
        if (raw.isEmpty()) {
            this.prefix = "";
            return;
        }
        while (raw.startsWith("/")) raw = raw.substring(1);
        String expectedLead = bucket + "/";
        if (raw.startsWith(expectedLead)) {
            raw = raw.substring(expectedLead.length());
        }
        if (raw.equals("/") || raw.isEmpty()) {
            this.prefix = "";
        } else {
            while (raw.startsWith("/")) raw = raw.substring(1);
            if (!raw.endsWith("/")) raw = raw + "/";
            this.prefix = raw;
        }
    }

    private static File buildLocalRoot(File cacheRoot, String bucket, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new File(cacheRoot, bucket);
        }
        String sub = prefix.replace('/', File.separatorChar);
        return new File(new File(cacheRoot, bucket), sub);
    }

    private File mapKeyToLocalFile(String key) {
        String rel = prefix.isEmpty() ? key : key.substring(prefix.length());
        rel = rel.replace('/', File.separatorChar);
        return new File(localRoot, rel);
    }

    private boolean mirrorOne(String key, File dest) {
        try {
            if (dest.exists() && dest.length() > 0) return false;
            File parent = dest.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) return false;

            byte[] data = storage.downloadBytes(bucket, key, timeoutSeconds);
            if (data == null || data.length == 0) return false;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                BufferedImage check = ImageIO.read(bais);
                if (check == null) return false;
            } catch (Throwable t) {
                return false;
            }

            File tmp = new File(parent, dest.getName() + ".part");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(tmp);
                fos.write(data);
            } finally {
                if (fos != null) try {
                    fos.close();
                } catch (Throwable ignore) {
                }
            }
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Throwable ex) {
            try {
                File tmp = new File(dest.getParentFile(), dest.getName() + ".part");
                if (tmp.exists()) tmp.delete();
            } catch (Throwable ignore) {
            }
            return false;
        }
    }
}
