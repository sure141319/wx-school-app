package com.campustrade.platform.audit.service;

import com.campustrade.platform.audit.dto.response.ThumbnailBackfillResponseDTO;
import com.campustrade.platform.audit.mapper.AuditImageMapper;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditImageServiceTest {

    private final AuditImageMapper auditImageMapper = mock(AuditImageMapper.class);
    private final GoodsMapper goodsMapper = mock(GoodsMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final AppProperties appProperties = appProperties();
    private final AuditImageService service = new AuditImageService(
            auditImageMapper,
            goodsMapper,
            userMapper,
            uploadService,
            appProperties
    );

    @Test
    void backfillMissingThumbnailsGeneratesThumbnailAndStoresObjectKey() {
        GoodsImageDO image = image(10L, "images/2026/04/original.jpg");
        when(goodsMapper.findImagesMissingThumbnails(50)).thenReturn(List.of(image));
        when(uploadService.generateThumbnailForObject("images/2026/04/original.jpg"))
                .thenReturn("images/2026/04/thumbs/original_thumb.jpg");
        when(goodsMapper.countImagesMissingThumbnails()).thenReturn(0L);

        ThumbnailBackfillResponseDTO response = service.backfillMissingThumbnails(1L, 50);

        assertEquals(1, response.processed());
        assertEquals(1, response.generated());
        assertEquals(0, response.skipped());
        assertEquals(0, response.failed());
        assertEquals(0L, response.remaining());
        verify(goodsMapper).updateImageThumbnail(10L, "images/2026/04/thumbs/original_thumb.jpg");
    }

    @Test
    void backfillMissingThumbnailsKeepsProcessingWhenOneImageCannotBeConverted() {
        GoodsImageDO unsupported = image(10L, "images/2026/04/unsupported.heic");
        GoodsImageDO missing = image(11L, "images/2026/04/missing.jpg");
        when(goodsMapper.findImagesMissingThumbnails(50)).thenReturn(List.of(unsupported, missing));
        when(uploadService.generateThumbnailForObject("images/2026/04/unsupported.heic")).thenReturn(null);
        when(uploadService.generateThumbnailForObject("images/2026/04/missing.jpg"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "image not found"));
        when(goodsMapper.countImagesMissingThumbnails()).thenReturn(2L);

        ThumbnailBackfillResponseDTO response = service.backfillMissingThumbnails(1L, 50);

        assertEquals(2, response.processed());
        assertEquals(0, response.generated());
        assertEquals(1, response.skipped());
        assertEquals(1, response.failed());
        assertEquals(2L, response.remaining());
        verify(goodsMapper, never()).updateImageThumbnail(10L, null);
        verify(goodsMapper, never()).updateImageThumbnail(11L, null);
    }

    private GoodsImageDO image(Long id, String imageUrl) {
        GoodsImageDO image = new GoodsImageDO();
        image.setId(id);
        image.setImageUrl(imageUrl);
        return image;
    }

    private AppProperties appProperties() {
        AppProperties properties = new AppProperties();
        properties.getImageAudit().setReviewerUserIds(List.of(1L));
        return properties;
    }
}
