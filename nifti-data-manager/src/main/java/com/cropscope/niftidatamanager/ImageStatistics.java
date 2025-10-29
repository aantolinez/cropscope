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

package com.cropscope.niftidatamanager;

public class ImageStatistics {
    private final double min, max, mean, stdDev;
    private final long voxelCount;

    public ImageStatistics(NiftiImage image) {
        Object data = image.getData();
        DataType dataType = image.getHeader().getDataType();

        double minVal = Double.MAX_VALUE;
        double maxVal = Double.MIN_VALUE;
        double sum = 0.0;
        long count = 0;

        if (dataType == DataType.DT_FLOAT) {
            float[] floatData = (float[]) data;
            for (float value : floatData) {
                if (Float.isFinite(value)) {
                    double dval = (double) value;
                    if (dval < minVal) minVal = dval;
                    if (dval > maxVal) maxVal = dval;
                    sum += dval;
                    count++;
                }
            }
        } else if (dataType == DataType.DT_DOUBLE) {
            double[] doubleData = (double[]) data;
            for (double value : doubleData) {
                if (Double.isFinite(value)) {
                    if (value < minVal) minVal = value;
                    if (value > maxVal) maxVal = value;
                    sum += value;
                    count++;
                }
            }
        } else if (dataType == DataType.DT_SIGNED_SHORT) {
            short[] shortData = (short[]) data;
            for (short value : shortData) {
                double dval = (double) value;
                if (dval < minVal) minVal = dval;
                if (dval > maxVal) maxVal = dval;
                sum += dval;
                count++;
            }
        } else if (dataType == DataType.DT_UNSIGNED_SHORT) {
            short[] shortData = (short[]) data;
            for (short value : shortData) {
                double dval = (double) (value & 0xFFFF);
                if (dval < minVal) minVal = dval;
                if (dval > maxVal) maxVal = dval;
                sum += dval;
                count++;
            }
        } else if (dataType == DataType.DT_UNSIGNED_CHAR) {
            byte[] byteData = (byte[]) data;
            for (byte value : byteData) {
                double dval = (double) (value & 0xFF);
                if (dval < minVal) minVal = dval;
                if (dval > maxVal) maxVal = dval;
                sum += dval;
                count++;
            }
        }

        if (count == 0) {
            this.min = 0;
            this.max = 0;
            this.mean = 0;
            this.stdDev = 0;
        } else {
            this.min = minVal;
            this.max = maxVal;
            this.mean = sum / count;
            double sumSquared = 0.0;
            if (dataType == DataType.DT_FLOAT) {
                float[] floatData = (float[]) data;
                for (float value : floatData) {
                    if (Float.isFinite(value)) {
                        double dval = (double) value - this.mean;
                        sumSquared += dval * dval;
                    }
                }
            } else if (dataType == DataType.DT_DOUBLE) {
                double[] doubleData = (double[]) data;
                for (double value : doubleData) {
                    if (Double.isFinite(value)) {
                        double dval = value - this.mean;
                        sumSquared += dval * dval;
                    }
                }
            } else if (dataType == DataType.DT_SIGNED_SHORT) {
                short[] shortData = (short[]) data;
                for (short value : shortData) {
                    double dval = (double) value - this.mean;
                    sumSquared += dval * dval;
                }
            } else if (dataType == DataType.DT_UNSIGNED_SHORT) {
                short[] shortData = (short[]) data;
                for (short value : shortData) {
                    double dval = (double) (value & 0xFFFF) - this.mean;
                    sumSquared += dval * dval;
                }
            } else if (dataType == DataType.DT_UNSIGNED_CHAR) {
                byte[] byteData = (byte[]) data;
                for (byte value : byteData) {
                    double dval = (double) (value & 0xFF) - this.mean;
                    sumSquared += dval * dval;
                }
            }

            this.stdDev = Math.sqrt(sumSquared / count);
        }

        this.voxelCount = count;
    }

    public double getMin() {
        return min;
    }
    public double getMax() { return max; }
    public double getMean() { return mean; }
    public double getStdDev() { return stdDev; }
    public long getVoxelCount() { return voxelCount; }
}