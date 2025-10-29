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

import java.nio.ByteOrder;
import java.util.Arrays;

public class NiftiHeader {
    private final boolean isNifti2;
    private final ByteOrder byteOrder;

    private short[] dim = new short[8];
    private short datatype;
    private short bitpix;
    private long vox_offset;
    private float[] pixdim = new float[8];
    private float intent_p1, intent_p2, intent_p3;
    private short intent_code;
    private short slice_code;
    private short slice_start, slice_end;
    private float slice_duration;
    private float toffset;
    private int cal_max, cal_min;
    private double srow_x[], srow_y[], srow_z[];

    private int nifti1_extents;
    private short nifti1_session_error;
    private byte nifti1_regular;
    private byte nifti1_dim_info;

    private long nifti2_unused1;
    private long nifti2_unused2;

    private byte[] extensionData;

    public NiftiHeader(boolean isNifti2, ByteOrder byteOrder) {
        this.isNifti2 = isNifti2;
        this.byteOrder = byteOrder;
    }

    public long getVoxelCount() {
        long count = 1;
        for (int i = 1; i <= dim[0]; i++) {
            count *= dim[i];
        }
        return count;
    }

    public long getDataSize() {
        return getVoxelCount() * ((long) bitpix / 8);
    }

    public boolean isNifti2() {
        return isNifti2;
    }
    public ByteOrder getByteOrder() { return byteOrder; }
    public short[] getDimensions() { return Arrays.copyOf(dim, dim.length); }
    public DataType getDataType() { return DataType.fromCode(datatype); }
    public float[] getVoxelDimensions() { return Arrays.copyOf(pixdim, pixdim.length); }

    public double[] voxelToWorld(double[] voxelCoords) {
        if (srow_x == null) return voxelCoords;

        double[] world = new double[4];
        world[0] = srow_x[0] * voxelCoords[0] + srow_x[1] * voxelCoords[1] +
                srow_x[2] * voxelCoords[2] + srow_x[3];
        world[1] = srow_y[0] * voxelCoords[0] + srow_y[1] * voxelCoords[1] +
                srow_y[2] * voxelCoords[2] + srow_y[3];
        world[2] = srow_z[0] * voxelCoords[0] + srow_z[1] * voxelCoords[1] +
                srow_z[2] * voxelCoords[2] + srow_z[3];
        world[3] = 1.0;

        return world;
    }

    public long getVoxOffset() {
        return vox_offset;
    }

    public void setDimensions(short[] dim) {
        if (dim.length == 8) {
            this.dim = dim.clone();
        } else {
            throw new IllegalArgumentException("Dimensions array must have 8 elements");
        }
    }
    public void setDatatype(short datatype) { this.datatype = datatype; }
    public void setBitpix(short bitpix) { this.bitpix = bitpix; }
    public void setVoxOffset(long vox_offset) { this.vox_offset = vox_offset; }
    public void setPixdim(float[] pixdim) {
        if (pixdim.length == 8) {
            this.pixdim = pixdim.clone();
        } else {
            throw new IllegalArgumentException("Pixdim array must have 8 elements");
        }
    }
}