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

package com.cropscope.imageverifytool;

public final class FfmpegFilterBuilder {
    private boolean dropDuplicates;
    private Double fps;
    private Double sceneThreshold;
    private Double minLumaYavg;

    private Integer mpHi, mpLo;
    private Double mpFrac;

    public FfmpegFilterBuilder setDropDuplicates(boolean v) { this.dropDuplicates = v; return this; }
    public FfmpegFilterBuilder setFps(Double v)             { this.fps = v; return this; }
    public FfmpegFilterBuilder setSceneThreshold(Double v)  { this.sceneThreshold = v; return this; }
    public FfmpegFilterBuilder setMinLumaYavg(Double y)     { this.minLumaYavg = y; return this; }

    public FfmpegFilterBuilder setMpdecimateParams(Integer hi, Integer lo, Double frac) {
        this.mpHi = hi;
        this.mpLo = lo;
        this.mpFrac = frac;
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder(128);
        boolean first = true;
        if (sceneThreshold != null && sceneThreshold > 0) {
            append(sb, first, "select=gt(scene\\," + trim(sceneThreshold) + ")");
            first = false;
        }
        if (minLumaYavg != null && minLumaYavg > 0) {
            append(sb, first, "signalstats");
            first = false;
            append(sb, first, "select=gt(metadata('lavfi.signalstats.YAVG')\\," + trim(minLumaYavg) + ")");
            first = false;
        }
        if (dropDuplicates) {
            String md = "mpdecimate";
            if (mpHi != null || mpLo != null || mpFrac != null) {
                StringBuilder p = new StringBuilder("mpdecimate");
                String sep = "=";
                if (mpHi != null) {
                    p.append(sep).append("hi=").append(mpHi);
                    sep = ":";
                }
                if (mpLo != null) {
                    p.append(sep).append("lo=").append(mpLo);
                    sep = ":";
                }
                if (mpFrac != null) {
                    p.append(sep).append("frac=").append(trim(mpFrac));
                }
                md = p.toString();
            }
            append(sb, first, md);
            first = false;
        }
        if (fps != null && fps > 0) {
            append(sb, first, "fps=" + trim(fps));
            first = false;
        }
        return first ? null : sb.toString();
    }

    private static void append(StringBuilder sb, boolean first, String part) {
        if (!first) sb.append(',');
        sb.append(part);
    }

    private static String trim(double v) {
        String s = String.valueOf(v);
        return s.endsWith(".0") ? s.substring(0, s.length()-2) : s;
    }
}

