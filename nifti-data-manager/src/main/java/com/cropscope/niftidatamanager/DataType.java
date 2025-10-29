/* ------------------------------------------------------
* This is file is part of the CropScope(R) suite.
* Authors:
* - Alfonso Antolínez García
* - Marina Antolínez Cabrero
--------------------------------------------------------*/

package com.cropscope.niftidatamanager;

public enum DataType {
    DT_NONE(0, 0, "none", Void.class),
    DT_BINARY(1, 1, "binary", byte.class),
    DT_UNSIGNED_CHAR(2, 8, "uint8", byte.class),
    DT_SIGNED_SHORT(4, 16, "int16", short.class),
    DT_SIGNED_INT(8, 32, "int32", int.class),
    DT_FLOAT(16, 32, "float32", float.class),
    DT_COMPLEX(32, 64, "complex64", float[].class),
    DT_DOUBLE(64, 64, "float64", double.class),
    DT_RGB(128, 24, "rgb", byte[].class),
    DT_ALL(255, 0, "all", Object.class),

    DT_UNSIGNED_SHORT(512, 16, "uint16", short.class),
    DT_UNSIGNED_INT(768, 32, "uint32", int.class),
    DT_LONG_LONG(1024, 64, "int64", long.class),
    DT_UNSIGNED_LONG_LONG(1280, 64, "uint64", long.class),
    DT_FLOAT128(1536, 128, "float128", double.class),
    DT_COMPLEX128(1792, 128, "complex128", double[].class),
    DT_COMPLEX256(2048, 256, "complex256", double[].class);

    private final int code;
    private final int bitpix;
    private final String name;
    private final Class<?> javaType;

    DataType(int code, int bitpix, String name, Class<?> javaType) {
        this.code = code;
        this.bitpix = bitpix;
        this.name = name;
        this.javaType = javaType;
    }

    public static DataType fromCode(int code) {
        for (DataType dt : values()) {
            if (dt.code == code) {
                return dt;
            }
        }
        throw new IllegalArgumentException("Unknown data type code: " + code);
    }

    public static DataType fromBitpix(int bitpix) {
        for (DataType dt : values()) {
            if (dt.bitpix == bitpix) {
                return dt;
            }
        }
        throw new IllegalArgumentException("Unknown bitpix: " + bitpix);
    }

    public int getCode() {
        return code;
    }

    public int getBitpix() {
        return bitpix;
    }

    public String getName() {
        return name;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public boolean isSigned() {
        return name.startsWith("int") || name.startsWith("float") || name.startsWith("double");
    }

    public boolean isComplex() {
        return name.startsWith("complex");
    }
}
