package com.finme.backend.service;

/**
 * Identifies an uploaded file by its actual leading bytes rather than by what the request said
 * it was.
 * <p>
 * {@code MultipartFile.getContentType()} is a client-supplied header. A browser derives it from
 * the file extension, and any other client can set it to anything - so a content-type check
 * alone proves nothing about the bytes. That matters more here than in a typical upload: these
 * bytes are read by PDFBox, and a receipt's bytes are base64-encoded and sent to a third-party
 * AI provider. Checking the signature costs four bytes and means the pipeline only ever
 * processes what it thinks it is processing.
 * <p>
 * This is a format check, not a safety guarantee - a real PDF can still be malformed or
 * hostile. It exists to reject the obviously-wrong file early, with a message the user can act
 * on, instead of letting it fail confusingly further down.
 */
final class FileSignature {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};                    // %PDF
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};             // .PNG

    private FileSignature() {
    }

    static boolean isPdf(byte[] bytes) {
        return startsWith(bytes, PDF);
    }

    static boolean isJpeg(byte[] bytes) {
        return startsWith(bytes, JPEG);
    }

    static boolean isPng(byte[] bytes) {
        return startsWith(bytes, PNG);
    }

    static boolean isSupportedImage(byte[] bytes) {
        return isJpeg(bytes) || isPng(bytes);
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes == null || bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
