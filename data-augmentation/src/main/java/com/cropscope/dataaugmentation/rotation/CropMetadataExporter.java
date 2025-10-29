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

package com.cropscope.dataaugmentation.rotation;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class CropMetadataExporter {
    private int originalWidth;
    private int originalHeight;
    private int cropWidth;
    private int cropHeight;
    private List<Integer> angles;
    private List<AngleMetadata> angleMetadataList;
    private boolean includeAllAngles;
    private String creationTime;
    private String imagePath;
    public CropMetadataExporter(int originalWidth, int originalHeight,
                                int cropWidth, int cropHeight, boolean includeAllAngles,
                                String imagePath) {
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.includeAllAngles = includeAllAngles;
        this.imagePath = imagePath;
        this.creationTime = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        this.angleMetadataList = new ArrayList<>();
        if (includeAllAngles) {
            this.angles = new ArrayList<>();
            for (int i = 0; i <= 9; i++) {
                this.angles.add(i * 10);
            }
        } else {
            this.angles = new ArrayList<>();
        }
        calculateAllAnglesMetadata();
    }

    public void setCurrentAngle(int currentAngle) {
        if (!includeAllAngles) {
            this.angles.clear();
            this.angleMetadataList.clear();
            this.angles.add(currentAngle);
            calculateAllAnglesMetadata();
        }
    }

    private void calculateAllAnglesMetadata() {
        this.angleMetadataList.clear();
        for (int angle : angles) {
            AngleMetadata metadata = new AngleMetadata();
            metadata.angle = angle;
            metadata.geometricParameters = calculateCropParameters(angle);
            metadata.cropRegions = new ArrayList<>();
            generateCropRegionsForAngle(metadata, angle);
            angleMetadataList.add(metadata);
        }
    }

    private Map<String, Double> calculateCropParameters(int angle) {
        Map<String, Double> params = new HashMap<>();
        double angleRad = Math.toRadians(angle);
        if (angle <= 45) {
            params.put("h", cropHeight * Math.cos(angleRad));
            params.put("h1", params.get("h") * Math.cos(angleRad));
            params.put("Ya", originalWidth * Math.sin(angleRad));
            params.put("x0", cropWidth * Math.tan(angleRad));
            params.put("x1", params.get("h") * Math.sin(angleRad));
            params.put("delta_y", params.get("x1") * Math.tan(angleRad));
            params.put("y1", params.get("Ya") - params.get("delta_y"));
            params.put("xb", originalWidth * Math.cos(angleRad));
            params.put("x2", params.get("xb") - params.get("h1"));
            params.put("y2", params.get("x1"));
            params.put("xmax", params.get("xb") - params.get("delta_y"));
            params.put("x3", (originalWidth * Math.cos(angleRad) +
                    originalHeight * Math.sin(angleRad)) - params.get("x1") - cropWidth);
            params.put("Yc", originalHeight * Math.cos(angleRad));
            params.put("y3", params.get("Yc") - params.get("h1"));
            params.put("Xd", originalHeight * Math.sin(angleRad));
            params.put("Yd", (originalWidth * Math.sin(angleRad) +
                    originalHeight * Math.cos(angleRad)));
            params.put("x4", params.get("Xd") - params.get("delta_y"));
            params.put("y4", params.get("Yd") - params.get("x1") - cropHeight);
            params.put("ymax", params.get("Yd") - params.get("x1"));
        } else {
            params.put("Ya", originalWidth * Math.sin(angleRad));
            params.put("h", cropHeight * Math.sin(angleRad));
            params.put("h1", params.get("h") * Math.sin(angleRad));
            params.put("x1", params.get("h") * Math.cos(angleRad));
            params.put("y1", params.get("Ya") - params.get("h1"));
            params.put("x0", cropHeight / Math.tan(angleRad));
            params.put("delta_y", cropHeight - params.get("h1"));
            params.put("Xb", originalWidth * Math.cos(angleRad));
            params.put("x2", params.get("Xb") - params.get("delta_y"));
            params.put("y2", params.get("x1"));
            params.put("xmax", params.get("x2") + cropWidth);
            params.put("Yc", originalHeight * Math.cos(angleRad));
            params.put("x3", (originalWidth * Math.cos(angleRad) +
                    originalHeight * Math.sin(angleRad)) - params.get("x1") - cropWidth);
            params.put("y3", params.get("Yc") - params.get("delta_y"));
            params.put("Yd", (originalWidth * Math.sin(angleRad) +
                    originalHeight * Math.cos(angleRad)));
            params.put("ymax", params.get("Yd") - params.get("x1"));
            params.put("Xd", originalHeight * Math.sin(angleRad));
            params.put("x4", params.get("Xd") - params.get("h1"));
            params.put("y4", params.get("Yd") - params.get("x1") - cropHeight);
        }
        return params;
    }

    private void generateCropRegionsForAngle(AngleMetadata metadata, int angle) {
        if (angle <= 45) {
            generateCropRegionsForAngleLe45(metadata);
        } else {
            generateCropRegionsForAngleGt45(metadata);
        }
    }
    private void generateCropRegionsForAngleLe45(AngleMetadata metadata) {
        Map<String, Double> params = metadata.geometricParameters;
        double l = params.get("xmax") - params.get("x1");
        int numberHorizontalCrops = (int) (l / cropWidth);
        double h = params.get("ymax") - params.get("y1");
        int numberVerticalCrops = (int) (h / cropWidth);
        for (int j = 0; j < numberVerticalCrops; j++) {
            for (int i = 0; i < numberHorizontalCrops; i++) {
                double x = (params.get("x1") + j * params.get("x0")) + i * cropWidth;
                double y = (params.get("y1") + j * cropHeight) - i * params.get("x0");
                metadata.cropRegions.add(new CropRegion(x, y, cropWidth, cropHeight));
                if (h / cropWidth != 0) {
                    x = (params.get("x2") + j * params.get("x0")) - i * cropWidth;
                    y = (params.get("y2") + j * cropHeight) + i * params.get("x0");
                    metadata.cropRegions.add(new CropRegion(x, y, cropWidth, cropHeight));
                    x = (params.get("x3") - j * params.get("x0")) - i * cropWidth;
                    y = (params.get("y3") - j * cropHeight) + i * params.get("x0");
                    metadata.cropRegions.add(new CropRegion(x, y, cropWidth, cropHeight));
                    x = (params.get("x4") - j * params.get("x0")) + i * cropWidth;
                    y = (params.get("y4") - j * cropHeight) - i * params.get("x0");
                    metadata.cropRegions.add(new CropRegion(x, y, cropWidth, cropHeight));
                }
            }
        }
    }
    private void generateCropRegionsForAngleGt45(AngleMetadata metadata) {
        Map<String, Double> params = metadata.geometricParameters;
        double l = params.get("y1") + cropHeight - params.get("y2");
        int numberHorizontalCrops = (int) (l / cropHeight);
        double v = params.get("x4") + cropHeight - params.get("x1");
        int numberVerticalCrops = (int) (v / cropWidth);
        for (int j = 0; j < numberVerticalCrops; j++) {
            for (int i = 0; i < numberHorizontalCrops; i++) {
                double x = params.get("x1") + j * cropWidth + i * params.get("x0");
                double y = params.get("y1") + j * params.get("x0") - i * cropHeight;
                metadata.cropRegions.add(new CropRegion(x, y, cropWidth, cropHeight));
                if (l / cropHeight != 0) {
                    x = params.get("x2") + j * cropWidth - i * params.get("x0");
                    y = params.get("y2") + j * params.get("x0") + i * cropHeight;
                    metadata.cropRegions.add(new CropRegion(x, y, cropWidth, cropHeight));
                }
                if (v / cropWidth != 0) {
                    x = params.get("x3") - j * cropWidth - i * params.get("x0");
                    y = params.get("y3") - j * params.get("x0") + i * cropHeight;
                    metadata.cropRegions.add(new CropRegion(x, y, cropWidth, cropHeight));
                    x = params.get("x4") - j * cropWidth + i * params.get("x0");
                    y = params.get("y4") - j * params.get("x0") - i * cropHeight;
                    metadata.cropRegions.add(new CropRegion(x, y, cropWidth, cropHeight));
                }
            }
        }
    }

    public void exportToJson(String outputFilePath) throws IOException {
        JSONObject json = new JSONObject();
        json.put("Creation time", creationTime);
        json.put("Image path", imagePath);
        JSONObject originalImage = new JSONObject();
        originalImage.put("width", originalWidth);
        originalImage.put("height", originalHeight);
        json.put("originalImage", originalImage);
        JSONObject cropSettings = new JSONObject();
        cropSettings.put("width", cropWidth);
        cropSettings.put("height", cropHeight);
        cropSettings.put("includeAllAngles", includeAllAngles);
        json.put("cropSettings", cropSettings);
        JSONArray anglesArray = new JSONArray();
        for (AngleMetadata angleMeta : angleMetadataList) {
            JSONObject angleObj = new JSONObject();
            angleObj.put("angle", angleMeta.angle);
            JSONObject geoParams = new JSONObject();
            for (Map.Entry<String, Double> entry : angleMeta.geometricParameters.entrySet()) {
                geoParams.put(entry.getKey(), entry.getValue());
            }
            angleObj.put("geometricParameters", geoParams);
            JSONArray regions = new JSONArray();
            for (CropRegion region : angleMeta.cropRegions) {
                JSONObject regionObj = new JSONObject();
                regionObj.put("x", region.x);
                regionObj.put("y", region.y);
                regionObj.put("width", region.width);
                regionObj.put("height", region.height);
                regions.put(regionObj);
            }
            angleObj.put("cropRegions", regions);
            anglesArray.put(angleObj);
        }
        json.put("angles", anglesArray);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            writer.write(json.toString(2));
        }
    }

    public int getTotalCropRegions() {
        int total = 0;
        for (AngleMetadata meta : angleMetadataList) {
            total += meta.cropRegions.size();
        }
        return total;
    }

    public List<Integer> getAngles() {
        return new ArrayList<>(angles);
    }

    private static class AngleMetadata {
        int angle;
        Map<String, Double> geometricParameters;
        List<CropRegion> cropRegions;
    }

    private static class CropRegion {
        double x;
        double y;
        int width;
        int height;

        public CropRegion(double x, double y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}