package com.campustrade.platform.upload.service;

final class UploadImageFormat {

    static final int HEADER_BYTES = 32;

    private UploadImageFormat() {
    }

    static boolean isHeifFamily(byte[] header) {
        if (header == null || header.length < 12 || !asciiEquals(header, 4, "ftyp")) {
            return false;
        }
        for (int offset = 8; offset + 4 <= header.length; offset += 4) {
            if (isHeifBrand(header, offset)) {
                return true;
            }
        }
        return false;
    }

    static boolean asciiEquals(byte[] value, int offset, String expected) {
        if (value == null || offset < 0 || value.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (value[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHeifBrand(byte[] header, int offset) {
        return asciiEquals(header, offset, "heic")
                || asciiEquals(header, offset, "heix")
                || asciiEquals(header, offset, "hevc")
                || asciiEquals(header, offset, "hevx")
                || asciiEquals(header, offset, "heim")
                || asciiEquals(header, offset, "heis")
                || asciiEquals(header, offset, "hevm")
                || asciiEquals(header, offset, "hevs")
                || asciiEquals(header, offset, "heif")
                || asciiEquals(header, offset, "mif1")
                || asciiEquals(header, offset, "msf1")
                || asciiEquals(header, offset, "miaf")
                || asciiEquals(header, offset, "MiHE")
                || asciiEquals(header, offset, "MiPr")
                || asciiEquals(header, offset, "MiHB");
    }
}
