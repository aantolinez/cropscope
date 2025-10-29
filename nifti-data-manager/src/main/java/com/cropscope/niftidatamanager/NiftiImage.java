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

import com.cropscope.niftidatamanager.exceptions.NiftiException;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NiftiImage {
    private final String filename;
    private final NiftiHeader header;
    private final Path filePath;

    private volatile Object data;
    private volatile MappedByteBuffer mappedBuffer;
    private final ReadWriteLock dataLock = new ReentrantReadWriteLock();
    private final boolean useMemoryMapping;

    static final long MEMORY_MAPPING_THRESHOLD = 100 * 1024 * 1024;

    NiftiImage(String filename, NiftiHeader header, boolean useMemoryMapping) {
        this.filename = filename;
        this.header = header;
        this.filePath = Paths.get(filename);
        this.useMemoryMapping = useMemoryMapping;
    }

    public static NiftiImage read(String filename) throws IOException {
        return NiftiReader.read(filename);
    }

    public Object getData() {
        if (data == null && mappedBuffer == null) {
            dataLock.writeLock().lock();
            try {
                if (data == null && mappedBuffer == null) {
                    loadData();
                }
            } finally {
                dataLock.writeLock().unlock();
            }
        }
        return data;
    }

    public MappedByteBuffer getMappedBuffer() throws IOException {
        if (mappedBuffer == null && data == null) {
            dataLock.writeLock().lock();
            try {
                if (mappedBuffer == null && data == null) {
                    if (useMemoryMapping || header.getDataSize() > MEMORY_MAPPING_THRESHOLD) {
                        FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ);
                        mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY,
                                header.getVoxOffset(), header.getDataSize());
                    } else {
                        loadData();
                    }
                }
            } finally {
                dataLock.writeLock().unlock();
            }
        }
        return mappedBuffer;
    }

    public Object getChunk(int[] start, int[] end) throws IOException {
        return NiftiReader.readChunk(this, start, end);
    }

    public ImageStatistics getStatistics() {
        return new ImageStatistics(this);
    }

    public void exportSliceToPNG(String outputFilename, int dimension, int sliceIndex)
            throws IOException {
        ImageExporter.exportSliceToPNG(this, outputFilename, dimension, sliceIndex);
    }

    public void exportAllSlicesToPNG(String outputBaseFilename, int dimension) throws IOException {
        ImageExporter.exportAllSlicesToPNG(this, outputBaseFilename, dimension);
    }

    public void exportMIPTopNG(String outputFilename, int dimension) throws IOException {
        ImageExporter.exportMIPTopNG(this, outputFilename, dimension);
    }

    private void loadData() {
        try {
            data = NiftiReader.loadData(this);
        } catch (IOException e) {
            System.err.println("Warning: Failed to load data from " + filename + ": " + e.getMessage());
            NiftiHeader header = getHeader();
            DataType dataType = header.getDataType();
            long voxelCount = header.getVoxelCount();

            switch (dataType) {
                case DT_FLOAT:
                    data = new float[(int) Math.min(voxelCount, Integer.MAX_VALUE)];
                    break;
                case DT_DOUBLE:
                    data = new double[(int) Math.min(voxelCount, Integer.MAX_VALUE)];
                    break;
                case DT_UNSIGNED_CHAR:
                    data = new byte[(int) Math.min(voxelCount, Integer.MAX_VALUE)];
                    break;
                default:
                    data = new short[(int) Math.min(voxelCount, Integer.MAX_VALUE)];
                    break;
            }
        }
    }

    public double[] voxelToWorld(double[] voxelCoords) {
        return header.voxelToWorld(voxelCoords);
    }

    public double[] worldToVoxel(double[] worldCoords) {
        return new double[]{worldCoords[0], worldCoords[1], worldCoords[2]};
    }

    public NiftiHeader getHeader() {
        return header;
    }

    public String getFilename() {
        return filename;
    }

    public long getVoxelCount() {
        return header.getVoxelCount();
    }

    public long getDataSize() {
        return header.getDataSize();
    }

    public boolean isNifti2() {
        return header.isNifti2();
    }

    public short[] getDimensions() {
        return header.getDimensions();
    }

    public DataType getDataType() {
        return header.getDataType();
    }

    public float[] getVoxelDimensions() {
        return header.getVoxelDimensions();
    }

    public NiftiImage resample(short[] newDimensions) {
        throw new UnsupportedOperationException("Resampling not yet implemented");
    }

    public NiftiImage crop(int[] start, int[] size) {
        throw new UnsupportedOperationException("Cropping not yet implemented");
    }

    @Override
    public String toString() {
        return String.format("NiftiImage{filename='%s', dimensions=%s, dataType=%s, isNifti2=%s}",
                filename,
                Arrays.toString(getDimensions()),
                getDataType().getName(),
                isNifti2());
    }
}