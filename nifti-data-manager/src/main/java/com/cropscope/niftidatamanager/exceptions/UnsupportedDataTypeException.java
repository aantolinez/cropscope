/* ------------------------------------------------------
* This is file is part of the CropScope(R) suite.
* Authors:
* - Alfonso Antolínez García
* - Marina Antolínez Cabrero
--------------------------------------------------------*/

package com.cropscope.niftidatamanager.exceptions;

public class UnsupportedDataTypeException extends NiftiException {
    public UnsupportedDataTypeException(String dataType) {
        super("Unsupported data type: " + dataType);
    }
}