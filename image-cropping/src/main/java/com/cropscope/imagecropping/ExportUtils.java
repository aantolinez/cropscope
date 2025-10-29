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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ExportUtils {
    public static String nowUtcStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static String stampToIso(String stamp) {
        if (stamp == null || stamp.length() != 16) return stamp;
        return stamp.substring(0,4) + "-" + stamp.substring(4,6) + "-" + stamp.substring(6,8) +
                "T" + stamp.substring(9,11) + ":" + stamp.substring(11,13) + ":" + stamp.substring(13,15) + "Z";
    }
}