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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

public class NiftiReader {

    public static NiftiImage read(String filename) throws IOException {
        boolean isGzipped = filename.toLowerCase().endsWith(".gz");
        boolean useMemoryMapping = !isGzipped;
        System.out.println("Reading file: " + filename);
        System.out.println("Is gzipped: " + isGzipped);
        FileInputStream fileStream = new FileInputStream(filename);
        BufferedInputStream bufferedStream = new BufferedInputStream(fileStream, 16384);

        InputStream decompressedStream;
        if (isGzipped) {
            decompressedStream = new GZIPInputStream(bufferedStream);
        } else {
            decompressedStream = bufferedStream;
        }

        try (DataInputStream dis = new DataInputStream(decompressedStream)) {
            byte[] first4Bytes = new byte[4];
            int bytesRead = dis.read(first4Bytes);
            if (bytesRead < 4) {
                throw new IOException("File too short: only " + bytesRead + " bytes read");
            }
            int asLittleEndian = (first4Bytes[3] << 24) | (first4Bytes[2] << 16) |
                    (first4Bytes[1] << 8) | (first4Bytes[0] & 0xFF);
            int asBigEndian = (first4Bytes[0] << 24) | (first4Bytes[1] << 16) |
                    (first4Bytes[2] << 8) | (first4Bytes[3] & 0xFF);

            System.out.println("First 4 bytes (hex): " +
                    String.format("%02X %02X %02X %02X",
                            first4Bytes[0], first4Bytes[1], first4Bytes[2], first4Bytes[3]));
            System.out.println("As little-endian: " + asLittleEndian);
            System.out.println("As big-endian: " + asBigEndian);
            boolean isNifti2 = (asLittleEndian == 540672) || (asBigEndian == 540672);

            NiftiHeader header;
            if (isNifti2) {
                System.out.println("Detected NIfTI-2 format");
                fileStream.close();
                fileStream = new FileInputStream(filename);
                bufferedStream = new BufferedInputStream(fileStream, 16384);
                decompressedStream = isGzipped ?
                        new GZIPInputStream(bufferedStream) : bufferedStream;
                try (DataInputStream newDis = new DataInputStream(decompressedStream)) {
                    header = readNifti2Header(newDis, filename);
                }
            } else {
                ByteOrder byteOrder;
                if (asLittleEndian == 348) {
                    byteOrder = ByteOrder.LITTLE_ENDIAN;
                    System.out.println("Detected NIfTI-1, little-endian");
                } else if (asBigEndian == 348) {
                    byteOrder = ByteOrder.BIG_ENDIAN;
                    System.out.println("Detected NIfTI-1, big-endian");
                } else {
                    throw new IOException("Not a valid NIfTI file. Header size: " +
                            asLittleEndian + " (LE) / " + asBigEndian + " (BE). Expected 348 or 540672.");
                }
                fileStream.close();
                fileStream = new FileInputStream(filename);
                bufferedStream = new BufferedInputStream(fileStream, 16384);
                decompressedStream = isGzipped ?
                        new GZIPInputStream(bufferedStream) : bufferedStream;
                try (DataInputStream newDis = new DataInputStream(decompressedStream)) {
                    header = readNifti1Header(newDis, byteOrder, filename);
                }
            }

            boolean shouldUseMemoryMapping = useMemoryMapping &&
                    header.getDataSize() > NiftiImage.MEMORY_MAPPING_THRESHOLD;

            System.out.println("Creating NiftiImage with memory mapping: " + shouldUseMemoryMapping);
            NiftiImage image = new NiftiImage(filename, header, shouldUseMemoryMapping);

            if (!shouldUseMemoryMapping || isGzipped) {
                System.out.println("Loading data immediately (small file or compressed)");
                image.getData();
            }

            return image;
        } finally {
            try {
                fileStream.close();
            } catch (IOException e) {
            }
        }
    }

    private static boolean isSingleFileNifti(String filename) {
        return filename.toLowerCase().endsWith(".nii") || filename.toLowerCase().endsWith(".nii.gz");
    }

    private static NiftiHeader readNifti1Header(DataInputStream dis, ByteOrder byteOrder, String filename) throws IOException {
        System.out.println("Reading NIfTI-1 header with byte order: " + byteOrder);

        byte[] headerBytes = new byte[348];
        int totalRead = 0;
        while (totalRead < 348) {
            int read = dis.read(headerBytes, totalRead, 348 - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of file while reading header. Read " + totalRead + "/348 bytes");
            }
            totalRead += read;
        }

        NiftiHeader header = new NiftiHeader(false, byteOrder);
        int sizeofHdr = byteOrder == ByteOrder.LITTLE_ENDIAN ?
                (headerBytes[3] << 24) | (headerBytes[2] << 16) | (headerBytes[1] << 8) | (headerBytes[0] & 0xFF) :
                (headerBytes[0] << 24) | (headerBytes[1] << 16) | (headerBytes[2] << 8) | (headerBytes[3] & 0xFF);

        System.out.println("Parsed sizeof_hdr: " + sizeofHdr);

        if (sizeofHdr != 348) {
            throw new IOException("Invalid NIfTI-1 header size: " + sizeofHdr + ". Expected 348.");
        }
        short[] dim = new short[8];
        for (int i = 0; i < 8; i++) {
            int offset = 40 + i * 2;
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                dim[i] = (short) ((headerBytes[offset + 1] << 8) | (headerBytes[offset] & 0xFF));
            } else {
                dim[i] = (short) ((headerBytes[offset] << 8) | (headerBytes[offset + 1] & 0xFF));
            }
        }
        if (dim[0] < 1 || dim[0] > 7) {
            System.out.println("Warning: Invalid ndim = " + dim[0] + ", assuming 3D");
            dim[0] = 3;
        }

        for (int i = 1; i <= dim[0]; i++) {
            if (dim[i] <= 0) {
                System.out.println("Warning: Invalid dimension[" + i + "] = " + dim[i] + ", setting to 1");
                dim[i] = 1;
            }
        }
        for (int i = dim[0] + 1; i < 8; i++) {
            dim[i] = 1;
        }

        System.out.println("Dimensions: " + Arrays.toString(dim));
        header.setDimensions(dim);
        int datatypeOffset = 70;
        short datatype = byteOrder == ByteOrder.LITTLE_ENDIAN ?
                (short) ((headerBytes[datatypeOffset + 1] << 8) | (headerBytes[datatypeOffset] & 0xFF)) :
                (short) ((headerBytes[datatypeOffset] << 8) | (headerBytes[datatypeOffset + 1] & 0xFF));
        System.out.println("Datatype code: " + datatype);
        header.setDatatype(datatype);
        int bitpixOffset = 72;
        short bitpix = byteOrder == ByteOrder.LITTLE_ENDIAN ?
                (short) ((headerBytes[bitpixOffset + 1] << 8) | (headerBytes[bitpixOffset] & 0xFF)) :
                (short) ((headerBytes[bitpixOffset] << 8) | (headerBytes[bitpixOffset + 1] & 0xFF));
        System.out.println("Bitpix: " + bitpix);
        header.setBitpix(bitpix);
        int voxOffsetOffset = 108;
        int voxOffsetInt = byteOrder == ByteOrder.LITTLE_ENDIAN ?
                (headerBytes[voxOffsetOffset + 3] << 24) | (headerBytes[voxOffsetOffset + 2] << 16) |
                        (headerBytes[voxOffsetOffset + 1] << 8) | (headerBytes[voxOffsetOffset] & 0xFF) :
                (headerBytes[voxOffsetOffset] << 24) | (headerBytes[voxOffsetOffset + 1] << 16) |
                        (headerBytes[voxOffsetOffset + 2] << 8) | (headerBytes[voxOffsetOffset + 3] & 0xFF);
        float voxOffsetFloat = Float.intBitsToFloat(voxOffsetInt);
        long voxOffset;
        if (Float.isNaN(voxOffsetFloat) || voxOffsetFloat <= 0 || voxOffsetFloat > 1000000000) {
            if (isSingleFileNifti(filename)) {
                voxOffset = 352;
            } else {
                voxOffset = 0;
            }
            System.out.println("Warning: Invalid vox_offset = " + voxOffsetFloat + ", using default: " + voxOffset);
        } else {
            voxOffset = (long) voxOffsetFloat;
        }
        System.out.println("Vox offset (float): " + voxOffsetFloat + ", (long): " + voxOffset);
        header.setVoxOffset(voxOffset);
        float[] pixdim = new float[8];
        for (int i = 0; i < 8; i++) {
            int offset = 80 + i * 4;
            int floatBits = byteOrder == ByteOrder.LITTLE_ENDIAN ?
                    (headerBytes[offset + 3] << 24) | (headerBytes[offset + 2] << 16) |
                            (headerBytes[offset + 1] << 8) | (headerBytes[offset] & 0xFF) :
                    (headerBytes[offset] << 24) | (headerBytes[offset + 1] << 16) |
                            (headerBytes[offset + 2] << 8) | (headerBytes[offset + 3] & 0xFF);
            pixdim[i] = Float.intBitsToFloat(floatBits);
        }
        if (Float.isNaN(pixdim[0]) || pixdim[0] < 0 || pixdim[0] > 7) {
            System.out.println("Warning: Invalid pixdim[0] (ndim) = " + pixdim[0] + ", setting to " + dim[0]);
            pixdim[0] = dim[0];
        }

        for (int i = 1; i <= (int) pixdim[0]; i++) {
            if (Float.isNaN(pixdim[i]) || Float.isInfinite(pixdim[i]) || pixdim[i] <= 0 || pixdim[i] > 1000) {
                System.out.println("Warning: Invalid pixdim[" + i + "] = " + pixdim[i] + ", setting to 1.0");
                pixdim[i] = 1.0f;
            }
        }
        for (int i = (int) pixdim[0] + 1; i < 8; i++) {
            pixdim[i] = 1.0f;
        }

        System.out.println("Pixdim: " + Arrays.toString(pixdim));
        header.setPixdim(pixdim);

        return header;
    }

    private static NiftiHeader readNifti2Header(DataInputStream dis, String filename) throws IOException {
        System.out.println("Reading NIfTI-2 header");
        byte[] headerBytes = new byte[540];
        int totalRead = 0;
        while (totalRead < 540) {
            int read = dis.read(headerBytes, totalRead, 540 - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of file while reading NIfTI-2 header. Read " + totalRead + "/540 bytes");
            }
            totalRead += read;
        }

        NiftiHeader header = new NiftiHeader(true, ByteOrder.LITTLE_ENDIAN);
        short[] dim = new short[8];
        for (int i = 0; i < 8; i++) {
            int offset = 8 + i * 8;
            int dimValue = (headerBytes[offset + 3] << 24) | (headerBytes[offset + 2] << 16) |
                    (headerBytes[offset + 1] << 8) | (headerBytes[offset] & 0xFF);
            dim[i] = (short) dimValue;
        }
        if (dim[0] < 1 || dim[0] > 7) {
            System.out.println("Warning: Invalid ndim = " + dim[0] + ", assuming 3D");
            dim[0] = 3;
        }

        for (int i = 1; i <= dim[0]; i++) {
            if (dim[i] <= 0) {
                System.out.println("Warning: Invalid dimension[" + i + "] = " + dim[i] + ", setting to 1");
                dim[i] = 1;
            }
        }

        for (int i = dim[0] + 1; i < 8; i++) {
            dim[i] = 1;
        }

        System.out.println("NIfTI-2 Dimensions: " + Arrays.toString(dim));
        header.setDimensions(dim);
        short datatype = (short) ((headerBytes[41] << 8) | (headerBytes[40] & 0xFF));
        System.out.println("NIfTI-2 Datatype: " + datatype);
        header.setDatatype(datatype);
        short bitpix = (short) ((headerBytes[43] << 8) | (headerBytes[42] & 0xFF));
        System.out.println("NIfTI-2 Bitpix: " + bitpix);
        header.setBitpix(bitpix);
        long voxOffset = 0;
        for (int i = 0; i < 8; i++) {
            voxOffset |= ((long) (headerBytes[44 + i] & 0xFF)) << (i * 8);
        }
        if (voxOffset <= 0 || voxOffset > 10000000000L) {
            voxOffset = 544;
            System.out.println("Warning: Invalid NIfTI-2 vox_offset, using default: " + voxOffset);
        }

        System.out.println("NIfTI-2 Vox offset: " + voxOffset);
        header.setVoxOffset(voxOffset);
        float[] pixdim = new float[8];
        for (int i = 0; i < 8; i++) {
            int offset = 52 + i * 8;
            int floatBits = (headerBytes[offset + 3] << 24) | (headerBytes[offset + 2] << 16) |
                    (headerBytes[offset + 1] << 8) | (headerBytes[offset] & 0xFF);
            pixdim[i] = Float.intBitsToFloat(floatBits);
        }
        if (Float.isNaN(pixdim[0]) || pixdim[0] < 0 || pixdim[0] > 7) {
            pixdim[0] = dim[0];
        }

        for (int i = 1; i <= (int) pixdim[0]; i++) {
            if (Float.isNaN(pixdim[i]) || Float.isInfinite(pixdim[i]) || pixdim[i] <= 0 || pixdim[i] > 1000) {
                pixdim[i] = 1.0f;
            }
        }

        for (int i = (int) pixdim[0] + 1; i < 8; i++) {
            pixdim[i] = 1.0f;
        }

        System.out.println("NIfTI-2 Pixdim: " + Arrays.toString(pixdim));
        header.setPixdim(pixdim);

        return header;
    }

    static Object loadData(NiftiImage image) throws IOException {
        System.out.println("Loading data for image: " + image.getFilename());
        NiftiHeader header = image.getHeader();
        long voxelCount = header.getVoxelCount();
        DataType dataType = header.getDataType();
        ByteOrder byteOrder = header.getByteOrder();
        String filename = image.getFilename();
        long voxOffset = header.getVoxOffset();

        System.out.println("Voxel count: " + voxelCount + ", Data type: " + dataType + ", Byte order: " + byteOrder);
        System.out.println("Vox offset: " + voxOffset);

        boolean isGzipped = filename.toLowerCase().endsWith(".gz");

        if (isGzipped) {
            return loadCompressedData(filename, header, voxelCount, dataType, byteOrder, voxOffset);
        } else {
            return loadUncompressedData(filename, header, voxelCount, dataType, byteOrder, voxOffset);
        }
    }

    private static Object loadCompressedData(String filename, NiftiHeader header, long voxelCount,
                                             DataType dataType, ByteOrder byteOrder, long voxOffset) throws IOException {
        System.out.println("Loading compressed data sequentially");

        try (FileInputStream fis = new FileInputStream(filename);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gis = new GZIPInputStream(bis)) {
            long bytesToSkip = voxOffset;
            long skipped = 0;
            byte[] skipBuffer = new byte[8192];

            while (skipped < bytesToSkip) {
                int toRead = (int) Math.min(skipBuffer.length, bytesToSkip - skipped);
                int read = gis.read(skipBuffer, 0, toRead);
                if (read == -1) {
                    throw new IOException("Unexpected end of compressed file while skipping header. " +
                            "Expected to skip " + bytesToSkip + " bytes, skipped " + skipped);
                }
                skipped += read;
            }
            return readDataFromStream(gis, voxelCount, dataType, byteOrder);
        }
    }

    private static Object loadUncompressedData(String filename, NiftiHeader header, long voxelCount,
                                               DataType dataType, ByteOrder byteOrder, long voxOffset) throws IOException {
        System.out.println("Loading uncompressed data with random access");

        try (RandomAccessFile raf = new RandomAccessFile(filename, "r")) {
            raf.seek(voxOffset);
            FileInputStream fis = new FileInputStream(raf.getFD());
            return readDataFromStream(fis, voxelCount, dataType, byteOrder);
        }
    }

    private static Object readDataFromStream(InputStream inputStream, long voxelCount,
                                             DataType dataType, ByteOrder byteOrder) throws IOException {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream));

        switch (dataType) {
            case DT_FLOAT:
                float[] floatData = new float[(int) voxelCount];
                byte[] floatBytes = new byte[4];
                for (int i = 0; i < voxelCount; i++) {
                    dis.readFully(floatBytes);
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        byte temp = floatBytes[0];
                        floatBytes[0] = floatBytes[3];
                        floatBytes[3] = temp;
                        temp = floatBytes[1];
                        floatBytes[1] = floatBytes[2];
                        floatBytes[2] = temp;
                    }
                    floatData[i] = ByteBuffer.wrap(floatBytes).getFloat();
                }
                return floatData;

            case DT_DOUBLE:
                double[] doubleData = new double[(int) voxelCount];
                byte[] doubleBytes = new byte[8];
                for (int i = 0; i < voxelCount; i++) {
                    dis.readFully(doubleBytes);
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        for (int j = 0; j < 4; j++) {
                            byte temp = doubleBytes[j];
                            doubleBytes[j] = doubleBytes[7 - j];
                            doubleBytes[7 - j] = temp;
                        }
                    }
                    doubleData[i] = ByteBuffer.wrap(doubleBytes).getDouble();
                }
                return doubleData;

            case DT_UNSIGNED_CHAR:
                byte[] byteData = new byte[(int) voxelCount];
                dis.readFully(byteData);
                return byteData;

            case DT_SIGNED_SHORT:
                short[] shortData = new short[(int) voxelCount];
                byte[] shortBytes = new byte[2];
                for (int i = 0; i < voxelCount; i++) {
                    dis.readFully(shortBytes);
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        shortData[i] = (short) ((shortBytes[0] & 0xFF) | ((shortBytes[1] & 0xFF) << 8));
                    } else {
                        shortData[i] = (short) (((shortBytes[0] & 0xFF) << 8) | (shortBytes[1] & 0xFF));
                    }
                }
                return shortData;

            case DT_UNSIGNED_SHORT:
                short[] ushortData = new short[(int) voxelCount];
                byte[] ushortBytes = new byte[2];
                for (int i = 0; i < voxelCount; i++) {
                    dis.readFully(ushortBytes);
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        ushortData[i] = (short) ((ushortBytes[0] & 0xFF) | ((ushortBytes[1] & 0xFF) << 8));
                    } else {
                        ushortData[i] = (short) (((ushortBytes[0] & 0xFF) << 8) | (ushortBytes[1] & 0xFF));
                    }
                }
                return ushortData;

            case DT_SIGNED_INT:
                int[] intData = new int[(int) voxelCount];
                byte[] intBytes = new byte[4];
                for (int i = 0; i < voxelCount; i++) {
                    dis.readFully(intBytes);
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        intData[i] = (intBytes[3] << 24) | (intBytes[2] << 16) |
                                (intBytes[1] << 8) | (intBytes[0] & 0xFF);
                    } else {
                        intData[i] = (intBytes[0] << 24) | (intBytes[1] << 16) |
                                (intBytes[2] << 8) | (intBytes[3] & 0xFF);
                    }
                }
                return intData;

            default:
                throw new UnsupportedOperationException("Data type not implemented: " + dataType + " (code: " + dataType.getCode() + ")");
        }
    }

    static Object readChunk(NiftiImage image, int[] start, int[] end) throws IOException {
        System.out.println("Reading chunk from " + java.util.Arrays.toString(start) +
                " to " + java.util.Arrays.toString(end));
        return loadData(image);
    }
}