package com.campustrade.platform.upload.dto.response;

public record UploadResponseDTO(
        String url,
        String filename,
        String thumbnailUrl,
        String thumbnailFilename,
        String displayUrl,
        String displayFilename,
        String auditThumbnailUrl,
        String auditThumbnailFilename,
        boolean staged
) {
    public UploadResponseDTO(String url,
                             String filename,
                             String thumbnailUrl,
                             String thumbnailFilename,
                             String displayUrl,
                             String displayFilename,
                             String auditThumbnailUrl,
                             String auditThumbnailFilename) {
        this(
                url,
                filename,
                thumbnailUrl,
                thumbnailFilename,
                displayUrl,
                displayFilename,
                auditThumbnailUrl,
                auditThumbnailFilename,
                true
        );
    }

    public UploadResponseDTO(String url,
                             String filename,
                             String thumbnailUrl,
                             String thumbnailFilename) {
        this(url, filename, thumbnailUrl, thumbnailFilename, null, null, null, null, true);
    }
}

