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
import com.cropscope.niftidatamanager.batch.BatchNiftiProcessor;
import com.cropscope.niftidatamanager.gui.NiftiImageViewer;
public class NiftiDataManager {
    public static void main(String[] args) {
        if (args.length == 0) {
            NiftiImageViewer.main(args);
        } else if ("batch".equals(args[0])) {
            String[] batchArgs = new String[args.length - 1];
            System.arraycopy(args, 1, batchArgs, 0, batchArgs.length);
            BatchNiftiProcessor.main(batchArgs);
        } else if ("batch-quality".equals(args[0])) {
            System.out.println("Quality filtering mode is configured in BatchNiftiProcessor");
            String[] batchArgs = new String[args.length - 1];
            System.arraycopy(args, 1, batchArgs, 0, batchArgs.length);
            BatchNiftiProcessor.main(batchArgs);
        } else {
            System.out.println("Usage:");
            System.out.println("  GUI mode: java -jar nifti-data-manager.jar");
            System.out.println("  Batch mode: java -jar nifti-data-manager.jar batch <source> <sink> <dimension>");
            System.out.println("  Quality filtering is automatically applied to skip low-value slices");
            System.out.println("");
            System.out.println("Dimensions:");
            System.out.println("  0 = X/sagittal slices");
            System.out.println("  1 = Y/coronal slices");
            System.out.println("  2 = Z/axial slices");
            System.out.println("");
            System.out.println("Example: java -jar nifti-data-manager.jar batch /data/nifti /output/png 2");
        }
    }
}