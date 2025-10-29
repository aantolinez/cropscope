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

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class MetadataExporter {
    private final String sessionId;
    private int exportSequence = 0;
    private String sessionExportStamp = null;

    public MetadataExporter() {
        this.sessionId = UUID.randomUUID().toString();
    }

    public JSONObject buildExportJson(List<ImageCroppingCore.CropMetadata> items,
                                      String exportedAtUtc,
                                      String sessionFolderUtc,
                                      String project,
                                      String user,
                                      String sourceDir,
                                      String sinkDir,
                                      int cropWidth,
                                      int cropHeight) {
        JSONObject root = new JSONObject();
        root.put("project", project);
        root.put("user", user);
        root.put("sourceDir", sourceDir);
        root.put("sinkDir", sinkDir);
        root.put("sessionId", sessionId);
        root.put("exportSequence", exportSequence);
        root.put("defaultCropSize", new JSONObject().put("w", cropWidth).put("h", cropHeight));
        root.put("sessionFolderUtc", sessionFolderUtc);
        root.put("exportedAtUtc", exportedAtUtc);
        root.put("count", items.size());

        JSONArray arr = new JSONArray();
        for (ImageCroppingCore.CropMetadata m : items) arr.put(m.toJsonObject());
        root.put("crops", arr);
        return root;
    }

    public File getExportFile(File saveDirectory, String exportStamp) {
        if (sessionExportStamp == null) sessionExportStamp = exportStamp;
        File sessionExportDir = new File(saveDirectory, "Crop_Metadata_" + sessionExportStamp);
        if (!sessionExportDir.exists()) sessionExportDir.mkdirs();
        return new File(sessionExportDir, "crop_metadata_" + exportStamp + ".json");
    }

    public void writeJsonToFile(JSONObject json, File file) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(json.toString(2));
        }
    }

    public void incrementExportSequence() {
        exportSequence++;
    }

    public String getSessionExportStamp() {
        return sessionExportStamp;
    }
}