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

package com.cropscope.cloudstorage.service;

import com.cropscope.cloudstorage.model.ConnectionProfile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class ConnectionProfileManager {
    private final File configFile;
    private List<ConnectionProfile> profiles = new ArrayList<>();

    public ConnectionProfileManager() {
        String configPath = System.getenv("S3_CREDENTIALS_PATH");
        if (configPath == null || configPath.trim().isEmpty()) {
            configPath = "s3Credentials.json";
            System.out.println("S3_CREDENTIALS_PATH not set. Using default path: s3Credentials.json");
        } else {
            System.out.println("Using credentials file from S3_CREDENTIALS_PATH: " + configPath);
        }
        this.configFile = new File(configPath);
        loadProfiles();
    }

    public List<String> listConnections() {
        return profiles.stream()
                .map(ConnectionProfile::getName)
                .collect(Collectors.toList());
    }

    public ConnectionProfile getConnection(String name) {
        return profiles.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public void refresh() {
        loadProfiles();
    }

    private void loadProfiles() {
        profiles.clear();
        if (!configFile.exists()) {
            System.err.println("Credentials file not found: " + configFile.getAbsolutePath());
            System.err.println("Please check S3_CREDENTIALS_PATH environment variable or create s3Credentials.json");
            if ("s3Credentials.json".equals(configFile.getName())) {
                profiles.add(new ConnectionProfile(
                        "AWS S3", "AKIA...", "secret123",
                        "https://s3.amazonaws.com", "us-east-1", "AWS_S3"
                ));
                profiles.add(new ConnectionProfile(
                        "BackBlaze", "b2_key", "b2_secret",
                        "https://s3.eu-central-003.backblazeb2.com", "eu-central-003", "AWS_S3"
                ));
                saveProfiles();
                System.out.println("Created sample profiles in: s3Credentials.json");
            }
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(configFile.toPath());
            String content = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            JSONArray arr = json.getJSONArray("connections");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                profiles.add(new ConnectionProfile(
                        obj.getString("name"),
                        obj.getString("accessKey"),
                        obj.getString("secretKey"),
                        obj.getString("endpoint"),
                        obj.optString("region", "us-east-1"),
                        obj.optString("type", "AWS_S3")
                ));
            }
        } catch (Exception e) {
            System.err.println("Error loading profiles from " + configFile.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveProfiles() {
        try {
            JSONArray arr = new JSONArray(profiles.stream().map(p -> {
                JSONObject obj = new JSONObject();
                obj.put("name", p.getName());
                obj.put("accessKey", p.getAccessKey());
                obj.put("secretKey", p.getSecretKey());
                obj.put("endpoint", p.getEndpoint());
                obj.put("region", p.getRegion());
                obj.put("type", p.getType());
                return obj;
            }).toArray());
            JSONObject json = new JSONObject().put("connections", arr);
            Files.write(configFile.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Error saving profiles to " + configFile.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    public void saveProfile(ConnectionProfile profile) {
        profiles.removeIf(p -> p.getName().equals(profile.getName()));
        profiles.add(profile);
        saveProfiles();
    }

    public void deleteProfile(String name) {
        profiles.removeIf(p -> p.getName().equals(name));
        saveProfiles();
    }
}