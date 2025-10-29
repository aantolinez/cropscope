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

package com.cropscope.videoframeextractor;

import java.util.List;

public class BatchMetadata {
    private String videoPath;
    private String sinkDirectory;
    private String project;
    private String user;
    private ExtractionOptions extractionOptions;
    private String filterString;
    private List<VideoFrameExtractor.Segment> segments;

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getSinkDirectory() {
        return sinkDirectory;
    }

    public void setSinkDirectory(String sinkDirectory) {
        this.sinkDirectory = sinkDirectory;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public ExtractionOptions getExtractionOptions() {
        return extractionOptions;
    }

    public void setExtractionOptions(ExtractionOptions extractionOptions) {
        this.extractionOptions = extractionOptions;
    }

    public String getFilterString() {
        return filterString;
    }

    public void setFilterString(String filterString) {
        this.filterString = filterString;
    }

    public List<VideoFrameExtractor.Segment> getSegments() {
        return segments;
    }

    public void setSegments(List<VideoFrameExtractor.Segment> segments) {
        this.segments = segments;
    }
}
