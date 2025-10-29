/* ------------------------------------------------------
* This is file is part of the CropScope(R) suite.
* Authors:
* - Alfonso Antolínez García
* - Marina Antolínez Cabrero
--------------------------------------------------------*/

package com.cropscope.niftidatamanager.gui;

import com.cropscope.niftidatamanager.NiftiImage;
import com.cropscope.niftidatamanager.DataType;
import com.cropscope.niftidatamanager.NiftiHeader;
import com.cropscope.niftidatamanager.ImageStatistics;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.Arrays;

public class NiftiImageViewer extends JFrame {
    private NiftiImage currentImage;
    private JLabel imageLabel;
    private JLabel statusLabel;
    private JSlider sliceSlider;
    private JComboBox<String> dimensionCombo;
    private JButton exportButton;
    private JButton openButton;

    private int currentDimension = 2;
    private int currentSlice = 0;

    public NiftiImageViewer() {
        initializeGUI();
        setTitle("NIfTI Image Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
    }

    private void initializeGUI() {
        setLayout(new BorderLayout());
        createMenuBar();
        JPanel imagePanel = new JPanel(new BorderLayout());
        imageLabel = new JLabel("No image loaded", SwingConstants.CENTER);
        imageLabel.setBorder(BorderFactory.createEtchedBorder());
        imageLabel.setPreferredSize(new Dimension(600, 400));
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        imagePanel.add(scrollPane, BorderLayout.CENTER);
        JPanel controlPanel = createControlPanel();
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        add(imagePanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);
        add(statusLabel, BorderLayout.NORTH);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open NIfTI File...");
        openItem.addActionListener(e -> openNiftiFile());
        fileMenu.add(openItem);
        JMenuItem infoItem = new JMenuItem("Show Image Info");
        infoItem.addActionListener(e -> showImageInfo());
        fileMenu.add(infoItem);

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        JMenu exportMenu = new JMenu("Export");
        JMenuItem exportCurrentItem = new JMenuItem("Export Current Slice...");
        exportCurrentItem.addActionListener(e -> exportCurrentSlice());
        exportMenu.add(exportCurrentItem);

        JMenuItem exportAllItem = new JMenuItem("Export All Slices...");
        exportAllItem.addActionListener(e -> exportAllSlices());
        exportMenu.add(exportAllItem);

        menuBar.add(fileMenu);
        menuBar.add(exportMenu);
        setJMenuBar(menuBar);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        JLabel dimLabel = new JLabel("Dimension:");
        dimensionCombo = new JComboBox<>(new String[]{"X (Sagittal)", "Y (Coronal)", "Z (Axial)"});
        dimensionCombo.addActionListener(e -> dimensionChanged());
        panel.add(dimLabel);
        panel.add(dimensionCombo);
        JLabel sliceLabel = new JLabel("Slice:");
        sliceSlider = new JSlider(0, 0);
        sliceSlider.addChangeListener(e -> sliceChanged());
        sliceSlider.setEnabled(false);
        panel.add(sliceLabel);
        panel.add(sliceSlider);
        JButton middleSliceButton = new JButton("Middle Slice");
        middleSliceButton.addActionListener(e -> {
            if (currentImage != null) {
                short[] dims = currentImage.getHeader().getDimensions();
                int maxSlice = dims[currentDimension + 1] - 1;
                currentSlice = maxSlice / 2;
                sliceSlider.setValue(currentSlice);
                displayCurrentSlice();
            }
        });
        panel.add(middleSliceButton);
        exportButton = new JButton("Export Current Slice");
        exportButton.addActionListener(e -> exportCurrentSlice());
        exportButton.setEnabled(false);
        panel.add(exportButton);

        return panel;
    }

    private void openNiftiFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(
                "NIfTI Files (*.nii, *.nii.gz)", "nii", "nii.gz"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();
                statusLabel.setText("Loading " + selectedFile.getName() + "...");
                SwingUtilities.invokeLater(() -> setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)));
                SwingWorker<NiftiImage, Void> worker = new SwingWorker<NiftiImage, Void>() {
                    @Override
                    protected NiftiImage doInBackground() throws Exception {
                        return NiftiImage.read(selectedFile.getAbsolutePath());
                    }

                    @Override
                    protected void done() {
                        try {
                            currentImage = get();
                            updateImageDisplay();
                            statusLabel.setText("Loaded: " + selectedFile.getName());
                        } catch (Exception e) {
                            showError("Failed to load NIfTI file", e);
                            statusLabel.setText("Error loading file");
                        } finally {
                            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        }
                    }
                };
                worker.execute();

            } catch (Exception e) {
                showError("Error opening file", e);
            }
        }
    }

    private void updateImageDisplay() {
        if (currentImage == null) {
            imageLabel.setText("No image loaded");
            sliceSlider.setEnabled(false);
            exportButton.setEnabled(false);
            return;
        }
        updateDimensionCombo();
        int maxSlice = currentImage.getHeader().getDimensions()[currentDimension + 1] - 1;
        sliceSlider.setMaximum(maxSlice);
        sliceSlider.setValue(0);
        sliceSlider.setEnabled(true);
        currentSlice = 0;
        displayCurrentSlice();
        exportButton.setEnabled(true);
    }

    private void updateDimensionCombo() {
        short[] dims = currentImage.getHeader().getDimensions();
        int ndim = dims[0];
        dimensionCombo.removeAllItems();
        if (ndim >= 1) dimensionCombo.addItem("X (Sagittal)");
        if (ndim >= 2) dimensionCombo.addItem("Y (Coronal)");
        if (ndim >= 3) dimensionCombo.addItem("Z (Axial)");
        if (ndim >= 4) dimensionCombo.addItem("Time");
        if (ndim >= 5) dimensionCombo.addItem("Dimension 5");
        if (ndim >= 6) dimensionCombo.addItem("Dimension 6");
        if (ndim >= 7) dimensionCombo.addItem("Dimension 7");
        int preferredDimension = (ndim >= 3) ? 2 : 0;
        dimensionCombo.setSelectedIndex(preferredDimension);
        currentDimension = preferredDimension;
        int maxSlice = dims[currentDimension + 1] - 1;
        currentSlice = Math.max(0, maxSlice / 2);

        sliceSlider.setMaximum(maxSlice);
        sliceSlider.setValue(currentSlice);
        sliceSlider.setEnabled(true);
    }

    private void dimensionChanged() {
        int oldDimension = currentDimension;
        currentDimension = dimensionCombo.getSelectedIndex();
        if (currentDimension < 0) {
            currentDimension = 2;
        }

        if (currentImage != null) {
            short[] dims = currentImage.getHeader().getDimensions();
            int maxSlice = dims[currentDimension + 1] - 1;
            if (oldDimension != currentDimension) {
                currentSlice = Math.max(0, maxSlice / 2);
            } else {
                currentSlice = Math.min(currentSlice, maxSlice);
            }

            sliceSlider.setMaximum(maxSlice);
            sliceSlider.setValue(currentSlice);
            sliceSlider.setEnabled(true);

            displayCurrentSlice();
        }
    }

    private void sliceChanged() {
        if (sliceSlider.getValueIsAdjusting()) return;
        currentSlice = sliceSlider.getValue();
        displayCurrentSlice();
    }

    private void displayCurrentSlice() {
        if (currentImage == null) return;

        try {
            statusLabel.setText("Rendering slice " + currentSlice + "...");
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            BufferedImage bufferedImage = convertNiftiSliceToImage(currentImage, currentDimension, currentSlice);
            BufferedImage scaledImage = scaleImageToFit(bufferedImage, 600, 400);
            imageLabel.setIcon(null);
            imageLabel.setIcon(new ImageIcon(scaledImage));
            imageLabel.setText(null);

            statusLabel.setText(String.format("Slice %d/%d (Dimension %d)",
                    currentSlice + 1,
                    currentImage.getHeader().getDimensions()[currentDimension + 1],
                    currentDimension));

        } catch (Exception e) {
            showError("Error displaying slice", e);
            imageLabel.setIcon(null);
            imageLabel.setText("Error displaying image");
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    private BufferedImage convertNiftiSliceToImage(NiftiImage image, int dimension, int sliceIndex) {
        try {
            Object data = image.getData();
            short[] dims = image.getHeader().getDimensions();
            DataType dataType = image.getHeader().getDataType();

            System.out.println("Converting slice - DataType: " + dataType + ", Dimensions: " + Arrays.toString(dims));

            int[] outputDims = getOutputDimensions(dims, dimension);
            int width = Math.max(1, outputDims[0]);
            int height = Math.max(1, outputDims[1]);

            System.out.println("Output dimensions: " + width + "x" + height);
            float[] sliceData = extractSliceAsFloatArray(data, image.getHeader(), dimension, sliceIndex);
            float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
            for (float value : sliceData) {
                if (Float.isFinite(value)) {
                    if (value < min) min = value;
                    if (value > max) max = value;
                }
            }
            System.out.println("Slice data range: " + min + " to " + max);

            float[] normalizedData = normalizeFloatArray(sliceData);
            float normMin = Float.MAX_VALUE, normMax = Float.MIN_VALUE;
            for (float value : normalizedData) {
                if (value < normMin) normMin = value;
                if (value > normMax) normMax = value;
            }
            System.out.println("Normalized data range: " + normMin + " to " + normMax);
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] pixelData = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();

            int validPixels = 0;
            for (int i = 0; i < normalizedData.length && i < pixelData.length; i++) {
                if (Float.isFinite(normalizedData[i])) {
                    pixelData[i] = (byte) Math.max(0, Math.min(255, (int) (normalizedData[i] * 255)));
                    validPixels++;
                } else {
                    pixelData[i] = (byte) 128;
                }
            }

            System.out.println("Valid pixels: " + validPixels + "/" + normalizedData.length);

            return img;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to convert NIfTI slice to image", e);
        }
    }

    private int[] getOutputDimensions(short[] dims, int slicedDimension) {
        if (slicedDimension < 0) {
            slicedDimension = 2;
        }

        int ndim = dims[0];

        if (ndim == 2) {
            if (slicedDimension == 0) {
                return new int[]{1, dims[2]};
            } else if (slicedDimension == 1) {
                return new int[]{dims[1], 1};
            } else {
                return new int[]{dims[1], 1};
            }
        } else if (ndim == 3) {
            int width = dims[1];
            int height = dims[2];
            int depth = dims[3];
            if (slicedDimension > 2) {
                slicedDimension = 2;
            }

            switch (slicedDimension) {
                case 0:
                    return new int[]{height, depth};
                case 1:
                    return new int[]{width, depth};
                case 2:
                    return new int[]{width, height};
                default:
                    return new int[]{width, height};
            }
        } else {
            int outputSize = 1;
            for (int i = 1; i <= ndim; i++) {
                if (i - 1 != slicedDimension) {
                    outputSize *= dims[i];
                }
            }
            return new int[]{Math.max(1, outputSize), 1};
        }
    }

    private float[] extractSliceAsFloatArray(Object data, com.cropscope.niftidatamanager.NiftiHeader header,
                                             int dimension, int sliceIndex) {
        short[] dims = header.getDimensions();
        DataType dataType = header.getDataType();
        int ndim = dims[0];

        if (ndim == 2) {
            if (dimension == 0) {
                float[] result = new float[dims[2]];
                if (dataType == DataType.DT_UNSIGNED_CHAR) {
                    byte[] byteData = (byte[]) data;
                    for (int y = 0; y < dims[2]; y++) {
                        result[y] = byteData[y * dims[1] + sliceIndex] & 0xFF;
                    }
                } else if (dataType == DataType.DT_UNSIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int y = 0; y < dims[2]; y++) {
                        result[y] = shortData[y * dims[1] + sliceIndex] & 0xFFFF;
                    }
                } else if (dataType == DataType.DT_SIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int y = 0; y < dims[2]; y++) {
                        result[y] = shortData[y * dims[1] + sliceIndex];
                    }
                } else if (dataType == DataType.DT_FLOAT) {
                    float[] floatData = (float[]) data;
                    for (int y = 0; y < dims[2]; y++) {
                        result[y] = floatData[y * dims[1] + sliceIndex];
                    }
                } else if (dataType == DataType.DT_DOUBLE) {
                    double[] doubleData = (double[]) data;
                    for (int y = 0; y < dims[2]; y++) {
                        result[y] = (float) doubleData[y * dims[1] + sliceIndex];
                    }
                }
                return result;
            } else if (dimension == 1) {
                float[] result = new float[dims[1]];
                if (dataType == DataType.DT_UNSIGNED_CHAR) {
                    byte[] byteData = (byte[]) data;
                    for (int x = 0; x < dims[1]; x++) {
                        result[x] = byteData[sliceIndex * dims[1] + x] & 0xFF;
                    }
                } else if (dataType == DataType.DT_UNSIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int x = 0; x < dims[1]; x++) {
                        result[x] = shortData[sliceIndex * dims[1] + x] & 0xFFFF;
                    }
                } else if (dataType == DataType.DT_SIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int x = 0; x < dims[1]; x++) {
                        result[x] = shortData[sliceIndex * dims[1] + x];
                    }
                } else if (dataType == DataType.DT_FLOAT) {
                    float[] floatData = (float[]) data;
                    System.arraycopy(floatData, sliceIndex * dims[1], result, 0, dims[1]);
                } else if (dataType == DataType.DT_DOUBLE) {
                    double[] doubleData = (double[]) data;
                    for (int x = 0; x < dims[1]; x++) {
                        result[x] = (float) doubleData[sliceIndex * dims[1] + x];
                    }
                }
                return result;
            } else {
                throw new IllegalArgumentException("Invalid dimension " + dimension + " for 2D image");
            }
        } else if (ndim == 3) {
            int width = dims[1];
            int height = dims[2];
            int depth = dims[3];
            int maxSlice;
            switch (dimension) {
                case 0:
                    maxSlice = width;
                    break;
                case 1:
                    maxSlice = height;
                    break;
                case 2:
                    maxSlice = depth;
                    break;
                default:
                    dimension = 2;
                    maxSlice = depth;
                    break;
            }

            if (sliceIndex < 0 || sliceIndex >= maxSlice) {
                throw new IllegalArgumentException("Slice index " + sliceIndex + " out of bounds [0, " + maxSlice + ")");
            }

            float[] result;

            if (dimension == 2) {
                result = new float[width * height];
                int srcIndex = sliceIndex * width * height;

                if (dataType == DataType.DT_UNSIGNED_CHAR) {
                    byte[] byteData = (byte[]) data;
                    for (int i = 0; i < result.length; i++) {
                        result[i] = byteData[srcIndex + i] & 0xFF;
                    }
                } else if (dataType == DataType.DT_UNSIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int i = 0; i < result.length; i++) {
                        result[i] = shortData[srcIndex + i] & 0xFFFF;
                    }
                } else if (dataType == DataType.DT_SIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int i = 0; i < result.length; i++) {
                        result[i] = shortData[srcIndex + i];
                    }
                } else if (dataType == DataType.DT_FLOAT) {
                    float[] floatData = (float[]) data;
                    System.arraycopy(floatData, srcIndex, result, 0, result.length);
                } else if (dataType == DataType.DT_DOUBLE) {
                    double[] doubleData = (double[]) data;
                    for (int i = 0; i < result.length; i++) {
                        result[i] = (float) doubleData[srcIndex + i];
                    }
                } else {
                    throw new UnsupportedOperationException("Unsupported data type: " + dataType);
                }

            } else if (dimension == 1) {
                result = new float[width * depth];

                if (dataType == DataType.DT_UNSIGNED_CHAR) {
                    byte[] byteData = (byte[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int x = 0; x < width; x++) {
                            int srcIndex = z * width * height + sliceIndex * width + x;
                            int dstIndex = z * width + x;
                            result[dstIndex] = byteData[srcIndex] & 0xFF;
                        }
                    }
                } else if (dataType == DataType.DT_UNSIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int x = 0; x < width; x++) {
                            int srcIndex = z * width * height + sliceIndex * width + x;
                            int dstIndex = z * width + x;
                            result[dstIndex] = shortData[srcIndex] & 0xFFFF;
                        }
                    }
                } else if (dataType == DataType.DT_SIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int x = 0; x < width; x++) {
                            int srcIndex = z * width * height + sliceIndex * width + x;
                            int dstIndex = z * width + x;
                            result[dstIndex] = shortData[srcIndex];
                        }
                    }
                } else if (dataType == DataType.DT_FLOAT) {
                    float[] floatData = (float[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int x = 0; x < width; x++) {
                            int srcIndex = z * width * height + sliceIndex * width + x;
                            int dstIndex = z * width + x;
                            result[dstIndex] = floatData[srcIndex];
                        }
                    }
                } else if (dataType == DataType.DT_DOUBLE) {
                    double[] doubleData = (double[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int x = 0; x < width; x++) {
                            int srcIndex = z * width * height + sliceIndex * width + x;
                            int dstIndex = z * width + x;
                            result[dstIndex] = (float) doubleData[srcIndex];
                        }
                    }
                } else {
                    throw new UnsupportedOperationException("Unsupported data type: " + dataType);
                }

            } else if (dimension == 0) {
                result = new float[height * depth];

                if (dataType == DataType.DT_UNSIGNED_CHAR) {
                    byte[] byteData = (byte[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int y = 0; y < height; y++) {
                            int srcIndex = z * width * height + y * width + sliceIndex;
                            int dstIndex = z * height + y;
                            result[dstIndex] = byteData[srcIndex] & 0xFF;
                        }
                    }
                } else if (dataType == DataType.DT_UNSIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int y = 0; y < height; y++) {
                            int srcIndex = z * width * height + y * width + sliceIndex;
                            int dstIndex = z * height + y;
                            result[dstIndex] = shortData[srcIndex] & 0xFFFF;
                        }
                    }
                } else if (dataType == DataType.DT_SIGNED_SHORT) {
                    short[] shortData = (short[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int y = 0; y < height; y++) {
                            int srcIndex = z * width * height + y * width + sliceIndex;
                            int dstIndex = z * height + y;
                            result[dstIndex] = shortData[srcIndex];
                        }
                    }
                } else if (dataType == DataType.DT_FLOAT) {
                    float[] floatData = (float[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int y = 0; y < height; y++) {
                            int srcIndex = z * width * height + y * width + sliceIndex;
                            int dstIndex = z * height + y;
                            result[dstIndex] = floatData[srcIndex];
                        }
                    }
                } else if (dataType == DataType.DT_DOUBLE) {
                    double[] doubleData = (double[]) data;
                    for (int z = 0; z < depth; z++) {
                        for (int y = 0; y < height; y++) {
                            int srcIndex = z * width * height + y * width + sliceIndex;
                            int dstIndex = z * height + y;
                            result[dstIndex] = (float) doubleData[srcIndex];
                        }
                    }
                } else {
                    throw new UnsupportedOperationException("Unsupported data type: " + dataType);
                }
            } else {
                throw new IllegalArgumentException("Unsupported dimension: " + dimension);
            }

            return result;
        } else {
            int sliceSize = 1;
            for (int i = 1; i <= ndim; i++) {
                if (i - 1 != dimension) {
                    sliceSize *= dims[i];
                }
            }

            float[] result = new float[sliceSize];
            if (data instanceof float[]) {
                float[] floatData = (float[]) data;
                int dataIndex = sliceIndex * sliceSize;
                for (int i = 0; i < sliceSize && dataIndex + i < floatData.length; i++) {
                    result[i] = floatData[dataIndex + i];
                }
            } else if (data instanceof double[]) {
                double[] doubleData = (double[]) data;
                int dataIndex = sliceIndex * sliceSize;
                for (int i = 0; i < sliceSize && dataIndex + i < doubleData.length; i++) {
                    result[i] = (float) doubleData[dataIndex + i];
                }
            } else if (data instanceof short[]) {
                short[] shortData = (short[]) data;
                int dataIndex = sliceIndex * sliceSize;
                for (int i = 0; i < sliceSize && dataIndex + i < shortData.length; i++) {
                    result[i] = shortData[dataIndex + i];
                }
            } else if (data instanceof byte[]) {
                byte[] byteData = (byte[]) data;
                int dataIndex = sliceIndex * sliceSize;
                for (int i = 0; i < sliceSize && dataIndex + i < byteData.length; i++) {
                    result[i] = byteData[dataIndex + i] & 0xFF;
                }
            }
            return result;
        }
    }

    private float[] normalizeFloatArray(float[] data) {
        if (data.length == 0) return data;

        float min = data[0], max = data[0];
        for (float value : data) {
            if (value < min) min = value;
            if (value > max) max = value;
        }

        float range = max - min;
        if (range == 0) {
            java.util.Arrays.fill(data, 0.5f);
            return data;
        }

        for (int i = 0; i < data.length; i++) {
            data[i] = (data[i] - min) / range;
        }
        return data;
    }

    private BufferedImage scaleImageToFit(BufferedImage original, int maxWidth, int maxHeight) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (width <= maxWidth && height <= maxHeight) {
            return original;
        }
        double scaleX = (double) maxWidth / width;
        double scaleY = (double) maxHeight / height;
        double scale = Math.min(scaleX, scaleY);

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, original.getType());
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return scaled;
    }

    private void exportCurrentSlice() {
        if (currentImage == null) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Images (*.png)", "png"));
        fileChooser.setSelectedFile(new File("slice_" + currentSlice + ".png"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File outputFile = fileChooser.getSelectedFile();
                if (!outputFile.getName().toLowerCase().endsWith(".png")) {
                    outputFile = new File(outputFile.getPath() + ".png");
                }

                statusLabel.setText("Exporting to " + outputFile.getName() + "...");
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                BufferedImage image = convertNiftiSliceToImage(currentImage, currentDimension, currentSlice);
                ImageIO.write(image, "PNG", outputFile);

                statusLabel.setText("Exported to " + outputFile.getName());
                JOptionPane.showMessageDialog(this, "Image exported successfully!",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception e) {
                showError("Export failed", e);
            } finally {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }

    private void exportAllSlices() {
        if (currentImage == null) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Export Directory");

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File directory = fileChooser.getSelectedFile();
                String baseName = "slice";

                statusLabel.setText("Exporting all slices...");
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                int numSlices = currentImage.getHeader().getDimensions()[currentDimension + 1];
                for (int i = 0; i < numSlices; i++) {
                    BufferedImage image = convertNiftiSliceToImage(currentImage, currentDimension, i);
                    File outputFile = new File(directory, String.format("%s_%03d.png", baseName, i));
                    ImageIO.write(image, "PNG", outputFile);
                }

                statusLabel.setText("Exported " + numSlices + " slices to " + directory.getName());
                JOptionPane.showMessageDialog(this,
                        "All " + numSlices + " slices exported successfully!",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception e) {
                showError("Batch export failed", e);
            } finally {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }

    private void showError(String message, Exception e) {
        JOptionPane.showMessageDialog(this,
                message + ":\n" + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
            }

            new NiftiImageViewer().setVisible(true);
        });
    }

    private void showImageInfo() {
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this, "No image loaded", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            NiftiHeader header = currentImage.getHeader();
            ImageStatistics stats = currentImage.getStatistics();
            Object data = currentImage.getData();

            StringBuilder info = new StringBuilder();
            info.append("Filename: ").append(currentImage.getFilename()).append("\n");
            info.append("Dimensions: ").append(Arrays.toString(header.getDimensions())).append("\n");
            info.append("Data Type: ").append(header.getDataType().getName()).append("\n");
            info.append("Voxel Count: ").append(currentImage.getVoxelCount()).append("\n");
            info.append("Data Size: ").append(currentImage.getDataSize()).append(" bytes\n");
            info.append("Min Value: ").append(stats.getMin()).append("\n");
            info.append("Max Value: ").append(stats.getMax()).append("\n");
            info.append("Mean: ").append(stats.getMean()).append("\n");
            info.append("Std Dev: ").append(stats.getStdDev()).append("\n");
            info.append("Data Array Type: ").append(data.getClass().getSimpleName()).append("\n");
            info.append("First 10 values: ");
            if (data instanceof float[]) {
                float[] floatData = (float[]) data;
                for (int i = 0; i < Math.min(10, floatData.length); i++) {
                    info.append(floatData[i]).append(" ");
                }
            } else if (data instanceof double[]) {
                double[] doubleData = (double[]) data;
                for (int i = 0; i < Math.min(10, doubleData.length); i++) {
                    info.append(doubleData[i]).append(" ");
                }
            } else if (data instanceof short[]) {
                short[] shortData = (short[]) data;
                for (int i = 0; i < Math.min(10, shortData.length); i++) {
                    info.append(shortData[i]).append(" ");
                }
            } else if (data instanceof byte[]) {
                byte[] byteData = (byte[]) data;
                for (int i = 0; i < Math.min(10, byteData.length); i++) {
                    info.append(byteData[i] & 0xFF).append(" ");
                }
            }

            JOptionPane.showMessageDialog(this, info.toString(), "Image Information", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error getting image info: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}