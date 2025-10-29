/* ------------------------------------------------------
* This is file is part of the CropScope(R) suite.
* Authors:
* - Alfonso Antolínez García
* - Marina Antolínez Cabrero
--------------------------------------------------------*/

package com.cropscope.niftidatamanager;

import com.cropscope.niftidatamanager.exceptions.NiftiException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public class ImageExporter {

    private static final Set<DataType> SUPPORTED_EXPORT_TYPES = EnumSet.of(
            DataType.DT_UNSIGNED_CHAR,
            DataType.DT_SIGNED_SHORT,
            DataType.DT_UNSIGNED_SHORT,
            DataType.DT_SIGNED_INT,
            DataType.DT_FLOAT,
            DataType.DT_DOUBLE
    );

    public static void exportSliceToPNG(NiftiImage image, String outputFilename,
                                        int dimension, int sliceIndex) throws IOException {
        validateExportParameters(image, dimension, sliceIndex);
        BufferedImage bufferedImage = convertSliceToBufferedImage(image, dimension, sliceIndex);
        ImageIO.write(bufferedImage, "PNG", new File(outputFilename));
    }

    public static void exportAllSlicesToPNG(NiftiImage image, String outputBaseFilename,
                                            int dimension) throws IOException {
        NiftiHeader header = image.getHeader();
        short[] dims = header.getDimensions();
        int numSlices = dims[dimension + 1];

        for (int i = 0; i < numSlices; i++) {
            String filename = String.format("%s_%03d.png", outputBaseFilename, i);
            exportSliceToPNG(image, filename, dimension, i);
        }
    }

    public static void exportMIPTopNG(NiftiImage image, String outputFilename,
                                      int dimension) throws IOException {
        BufferedImage mipImage = createMaximumIntensityProjection(image, dimension);
        ImageIO.write(mipImage, "PNG", new File(outputFilename));
    }

    private static void validateExportParameters(NiftiImage image, int dimension, int sliceIndex) {
        NiftiHeader header = image.getHeader();
        short[] dims = header.getDimensions();

        if (dims[0] != 3) {
            throw new IllegalArgumentException("Only 3D images are supported for PNG export");
        }

        if (dimension != 2) {
            throw new IllegalArgumentException("Only axial (Z) slices (dimension=2) are supported");
        }

        if (sliceIndex < 0 || sliceIndex >= dims[3]) {
            throw new IllegalArgumentException(
                    String.format("Slice index %d out of bounds [0, %d)", sliceIndex, dims[3]));
        }

        DataType dataType = header.getDataType();
        if (!SUPPORTED_EXPORT_TYPES.contains(dataType)) {
            throw new NiftiException("Unsupported data type: " + dataType +
                    " (code: " + dataType.getCode() + ")");
        }
    }

    private static BufferedImage convertSliceToBufferedImage(NiftiImage image,
                                                             int dimension, int sliceIndex) {
        Object data = image.getData();
        NiftiHeader header = image.getHeader();
        short[] dims = header.getDimensions();
        DataType dataType = header.getDataType();
        int[] outputDims = getOutputDimensions(dims, dimension);
        int width = outputDims[0];
        int height = outputDims[1];
        float[] normalizedSlice = extractAndNormalizeSlice(data, header, dimension, sliceIndex);
        return createBufferedImageFromFloatArray(normalizedSlice, width, height);
    }

    private static int[] getOutputDimensions(short[] dims, int slicedDimension) {
        if (dims[0] != 3) {
            throw new UnsupportedOperationException("Only 3D images supported");
        }

        if (slicedDimension == 2) {
            return new int[]{dims[1], dims[2]};
        } else {
            throw new UnsupportedOperationException("Only Z dimension supported");
        }
    }

    private static float[] extractAndNormalizeSlice(Object data, NiftiHeader header,
                                                    int dimension, int sliceIndex) {
        short[] dims = header.getDimensions();
        DataType dataType = header.getDataType();
        if (dims[0] != 3) {
            throw new UnsupportedOperationException("Only 3D images supported for export");
        }

        int width = dims[1];
        int height = dims[2];
        int depth = dims[3];
        if (sliceIndex < 0 || sliceIndex >= depth) {
            throw new IllegalArgumentException("Slice index " + sliceIndex + " out of bounds [0, " + depth + ")");
        }

        float[] sliceData = new float[width * height];
        int srcIndex = sliceIndex * width * height;

        if (dataType == DataType.DT_FLOAT) {
            float[] floatData = (float[]) data;
            System.arraycopy(floatData, srcIndex, sliceData, 0, sliceData.length);
        } else if (dataType == DataType.DT_DOUBLE) {
            double[] doubleData = (double[]) data;
            for (int i = 0; i < sliceData.length; i++) {
                sliceData[i] = (float) doubleData[srcIndex + i];
            }
        } else if (dataType == DataType.DT_UNSIGNED_CHAR) {
            byte[] byteData = (byte[]) data;
            for (int i = 0; i < sliceData.length; i++) {
                sliceData[i] = byteData[srcIndex + i] & 0xFF;
            }
        } else if (dataType == DataType.DT_SIGNED_SHORT) {
            short[] shortData = (short[]) data;
            for (int i = 0; i < sliceData.length; i++) {
                sliceData[i] = shortData[srcIndex + i];
            }
        } else if (dataType == DataType.DT_UNSIGNED_SHORT) {
            short[] shortData = (short[]) data;
            for (int i = 0; i < sliceData.length; i++) {
                sliceData[i] = shortData[srcIndex + i] & 0xFFFF;
            }
        } else if (dataType == DataType.DT_SIGNED_INT) {
            int[] intData = (int[]) data;
            for (int i = 0; i < sliceData.length; i++) {
                sliceData[i] = intData[srcIndex + i];
            }
        } else {
            throw new NiftiException("Unsupported data type for export: " + dataType +
                    " (code: " + dataType.getCode() + ")");
        }

        return normalizeFloatArray(sliceData);
    }

    private static float[] normalizeFloatArray(float[] data) {
        if (data.length == 0) return data;
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        boolean foundValid = false;

        for (float value : data) {
            if (Float.isFinite(value)) {
                if (value < min) min = value;
                if (value > max) max = value;
                foundValid = true;
            }
        }

        if (!foundValid) {
            Arrays.fill(data, 0.5f);
            return data;
        }

        float range = max - min;
        if (range == 0) {
            Arrays.fill(data, 0.5f);
            return data;
        }
        for (int i = 0; i < data.length; i++) {
            if (Float.isFinite(data[i])) {
                data[i] = (data[i] - min) / range;
            } else {
                data[i] = 0.5f;
            }
        }

        return data;
    }

    private static BufferedImage createBufferedImageFromFloatArray(float[] data, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] pixelData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < data.length && i < pixelData.length; i++) {
            pixelData[i] = (byte) Math.max(0, Math.min(255, (int) (data[i] * 255)));
        }

        return image;
    }

    private static BufferedImage createMaximumIntensityProjection(NiftiImage image, int dimension) {
        Object data = image.getData();
        NiftiHeader header = image.getHeader();
        short[] dims = header.getDimensions();
        int[] outputDims = getOutputDimensions(dims, dimension);
        int width = outputDims[0];
        int height = outputDims[1];
        int projectionSize = width * height;
        float[] mipData = new float[projectionSize];
        Arrays.fill(mipData, Float.NEGATIVE_INFINITY);
        int numSlices = dims[dimension + 1];
        for (int slice = 0; slice < numSlices; slice++) {
            float[] sliceData = extractAndNormalizeSlice(data, header, dimension, slice);
            for (int i = 0; i < projectionSize; i++) {
                if (Float.isFinite(sliceData[i]) && sliceData[i] > mipData[i]) {
                    mipData[i] = sliceData[i];
                }
            }
        }
        boolean allInvalid = true;
        for (float value : mipData) {
            if (Float.isFinite(value)) {
                allInvalid = false;
                break;
            }
        }

        if (allInvalid) {
            Arrays.fill(mipData, 0.5f);
        }
        mipData = normalizeFloatArray(mipData);
        return createBufferedImageFromFloatArray(mipData, width, height);
    }

    public static class ExportOptions {
        private boolean autoContrast = true;
        private float windowMin = 0.0f;
        private float windowMax = 1.0f;
        private boolean invertGrayscale = false;
        private ColorMap colorMap = ColorMap.GRAYSCALE;

        public static ExportOptions create() {
            return new ExportOptions();
        }

        public ExportOptions autoContrast(boolean autoContrast) {
            this.autoContrast = autoContrast;
            return this;
        }

        public ExportOptions windowLevel(float min, float max) {
            this.windowMin = min;
            this.windowMax = max;
            return this;
        }

        public ExportOptions invert(boolean invert) {
            this.invertGrayscale = invert;
            return this;
        }

        public ExportOptions colorMap(ColorMap colorMap) {
            this.colorMap = colorMap;
            return this;
        }
    }

    public enum ColorMap {
        GRAYSCALE,
        JET,
        HOT,
        COOL,
        BONE
    }
}