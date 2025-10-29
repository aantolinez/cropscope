/* ------------------------------------------------------
* This is file is part of the CropScope(R) suite.
* Authors:
* - Alfonso Antolínez García
* - Marina Antolínez Cabrero
--------------------------------------------------------*/

package com.cropscope.niftidatamanager.exceptions;

public class NiftiException extends RuntimeException {
    public NiftiException(String message) {
        super(message);
    }

    public NiftiException(String message, Throwable cause) {
        super(message, cause);
    }
}

