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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

public final class CocoExporter {

    public JSONObject buildCocoInstances(
            List<ImageCroppingCore.CropMetadata> crops,
            String datasetName,
            String createdBy,
            String dateCreatedIsoUtc
    ) {
        JSONObject root = new JSONObject();
        JSONObject info = new JSONObject();
        JSONArray images = new JSONArray();
        JSONArray annotations = new JSONArray();
        JSONArray categories = new JSONArray();
        JSONArray licenses = new JSONArray();
        String year = (dateCreatedIsoUtc != null && dateCreatedIsoUtc.length() >= 4)
                ? dateCreatedIsoUtc.substring(0, 4) : "";
        info.put("description", (datasetName == null ? "" : datasetName) + " (COCO instances)");
        info.put("version", "1.0");
        info.put("year", year);
        info.put("contributor", (createdBy == null ? "" : createdBy));
        info.put("date_created", (dateCreatedIsoUtc == null ? "" : dateCreatedIsoUtc));
        int nextImageId = 1;
        int nextAnnId = 1;
        int nextCatId = 1;
        Map<String, Integer> imageIdByKey = new LinkedHashMap<String, Integer>();
        Map<String, Integer> catIdByName = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < crops.size(); i++) {
            ImageCroppingCore.CropMetadata m = crops.get(i);
            if (m == null) continue;
            String imageKey = (m.imagePath != null && m.imagePath.trim().length() > 0)
                    ? m.imagePath
                    : m.imageName;

            if (imageKey == null || imageKey.trim().isEmpty()) {
                imageKey = "img_" + (i + 1);
            }

            Integer imageId = imageIdByKey.get(imageKey);
            if (imageId == null) {
                imageId = Integer.valueOf(nextImageId++);
                imageIdByKey.put(imageKey, imageId);

                String fileName = (m.imageName != null && m.imageName.trim().length() > 0)
                        ? m.imageName
                        : new File(imageKey).getName();

                JSONObject img = new JSONObject();
                img.put("id", imageId.intValue());
                img.put("file_name", fileName);
                img.put("width", Math.max(1, m.imageWidth));
                img.put("height", Math.max(1, m.imageHeight));
                images.put(img);
            }
            String catName = (m.annotation == null || m.annotation.trim().isEmpty())
                    ? "unlabeled"
                    : m.annotation.trim();

            Integer catId = catIdByName.get(catName);
            if (catId == null) {
                catId = Integer.valueOf(nextCatId++);
                catIdByName.put(catName, catId);

                JSONObject cat = new JSONObject();
                cat.put("id", catId.intValue());
                cat.put("name", catName);
                cat.put("supercategory", "default");
                categories.put(cat);
            }
            int x1 = Math.min(m.cropX1, m.cropX2);
            int y1 = Math.min(m.cropY1, m.cropY2);
            int x2 = Math.max(m.cropX1, m.cropX2);
            int y2 = Math.max(m.cropY1, m.cropY2);
            int W = Math.max(1, m.imageWidth);
            int H = Math.max(1, m.imageHeight);
            x1 = clamp(x1, 0, W - 1);
            y1 = clamp(y1, 0, H - 1);
            x2 = clamp(x2, 0, W - 1);
            y2 = clamp(y2, 0, H - 1);

            int bw = Math.max(0, x2 - x1);
            int bh = Math.max(0, y2 - y1);

            JSONObject ann = new JSONObject();
            ann.put("id", nextAnnId++);
            ann.put("image_id", imageId.intValue());
            ann.put("category_id", catId.intValue());
            ann.put("bbox", new JSONArray(Arrays.asList(x1, y1, bw, bh)));
            ann.put("area", (double) (bw * bh));
            ann.put("iscrowd", 0);
            ann.put("segmentation", new JSONArray());

            annotations.put(ann);
        }
        root.put("info", info);
        root.put("licenses", licenses);
        root.put("images", images);
        root.put("annotations", annotations);
        root.put("categories", categories);

        return root;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
