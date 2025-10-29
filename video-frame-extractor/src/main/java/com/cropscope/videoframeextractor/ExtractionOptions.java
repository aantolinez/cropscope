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

public class ExtractionOptions {
    private boolean dropDuplicates;
    private boolean advancedDropDuplicates;
    private boolean useCustomMpdecimateParams;
    private Integer mpdecimateHi;
    private Integer mpdecimateLo;
    private Double mpdecimateFrac;
    private boolean useUniformSampling;
    private Double uniformSamplingFps;
    private boolean useSceneChanges;
    private Double sceneThreshold;
    private boolean skipDarkFrames;
    private Double minLumaYavg;

    public ExtractionOptions() {
    }

    public boolean isDropDuplicates() {
        return dropDuplicates;
    }

    public void setDropDuplicates(boolean dropDuplicates) {
        this.dropDuplicates = dropDuplicates;
    }

    public boolean isAdvancedDropDuplicates() {
        return advancedDropDuplicates;
    }

    public void setAdvancedDropDuplicates(boolean advancedDropDuplicates) {
        this.advancedDropDuplicates = advancedDropDuplicates;
    }

    public boolean isUseCustomMpdecimateParams() {
        return useCustomMpdecimateParams;
    }

    public void setUseCustomMpdecimateParams(boolean useCustomMpdecimateParams) {
        this.useCustomMpdecimateParams = useCustomMpdecimateParams;
    }

    public Integer getMpdecimateHi() {
        return mpdecimateHi;
    }

    public void setMpdecimateHi(Integer mpdecimateHi) {
        this.mpdecimateHi = mpdecimateHi;
    }

    public Integer getMpdecimateLo() {
        return mpdecimateLo;
    }

    public void setMpdecimateLo(Integer mpdecimateLo) {
        this.mpdecimateLo = mpdecimateLo;
    }

    public Double getMpdecimateFrac() {
        return mpdecimateFrac;
    }

    public void setMpdecimateFrac(Double mpdecimateFrac) {
        this.mpdecimateFrac = mpdecimateFrac;
    }

    public boolean isUseUniformSampling() {
        return useUniformSampling;
    }

    public void setUseUniformSampling(boolean useUniformSampling) {
        this.useUniformSampling = useUniformSampling;
    }

    public Double getUniformSamplingFps() {
        return uniformSamplingFps;
    }

    public void setUniformSamplingFps(Double uniformSamplingFps) {
        this.uniformSamplingFps = uniformSamplingFps;
    }

    public boolean isUseSceneChanges() {
        return useSceneChanges;
    }

    public void setUseSceneChanges(boolean useSceneChanges) {
        this.useSceneChanges = useSceneChanges;
    }

    public Double getSceneThreshold() {
        return sceneThreshold;
    }

    public void setSceneThreshold(Double sceneThreshold) {
        this.sceneThreshold = sceneThreshold;
    }

    public boolean isSkipDarkFrames() {
        return skipDarkFrames;
    }

    public void setSkipDarkFrames(boolean skipDarkFrames) {
        this.skipDarkFrames = skipDarkFrames;
    }

    public Double getMinLumaYavg() {
        return minLumaYavg;
    }

    public void setMinLumaYavg(Double minLumaYavg) {
        this.minLumaYavg = minLumaYavg;
    }
}