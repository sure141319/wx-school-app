package com.campustrade.platform.audit.service;

import com.campustrade.platform.audit.dataobject.AuditImageRecordDO;
import com.campustrade.platform.audit.dataobject.AvatarAuditRecordDO;
import com.campustrade.platform.audit.dto.response.ThumbnailBackfillResponseDTO;
import com.campustrade.platform.audit.mapper.AuditImageMapper;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.config.cache.GoodsListCacheInvalidator;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditImageServiceTest {

    private final AuditImageMapper auditImageMapper = mock(AuditImageMapper.class);
    private final GoodsMapper goodsMapper = mock(GoodsMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final GoodsListCacheInvalidator goodsListCacheInvalidator = mock(GoodsListCacheInvalidator.class);
    private final AppProperties appProperties = appProperties();
    private final AuditImageService service = new AuditImageService(
            auditImageMapper,
            goodsMapper,
            userMapper,
            uploadService,
            appProperties,
            goodsListCacheInvalidator
    );

    @Test
    void listIncludesSellerContactFields() {
        AuditImageRecordDO record = new AuditImageRecordDO();
        record.setImageId(10L);
        record.setGoodsId(20L);
        record.setGoodsTitle("台灯");
        record.setSellerId(30L);
        record.setSellerNickname("卖家");
        record.setSellerWechatId("wx_seller_30");
        record.setSellerQq("123456789");
        record.setImageUrl("images/original.jpg");
        record.setAuditStatus(ImageAuditStatusEnum.PENDING);
        when(auditImageMapper.search(ImageAuditStatusEnum.PENDING, 10, 0)).thenReturn(List.of(record));
        when(auditImageMapper.countSearch(ImageAuditStatusEnum.PENDING)).thenReturn(1L);
        when(uploadService.getProxyUrl("images/original.jpg")).thenReturn("/api/v1/images/proxy/original.jpg");

        var response = service.list(1L, null, 0, 10);

        assertEquals(1, response.total());
        assertEquals("wx_seller_30", response.items().get(0).sellerWechatId());
        assertEquals("123456789", response.items().get(0).sellerQq());
    }

    @Test
    void listAvatarsIncludesUserContactFields() {
        AvatarAuditRecordDO record = new AvatarAuditRecordDO();
        record.setUserId(30L);
        record.setNickname("用户");
        record.setWechatId("wx_user_30");
        record.setQq("987654321");
        record.setAvatarUrl("avatars/user.jpg");
        record.setAvatarAuditStatus(ImageAuditStatusEnum.PENDING);
        when(auditImageMapper.searchAvatars(ImageAuditStatusEnum.PENDING, 10, 0)).thenReturn(List.of(record));
        when(auditImageMapper.countSearchAvatars(ImageAuditStatusEnum.PENDING)).thenReturn(1L);
        when(uploadService.getProxyUrl("avatars/user.jpg")).thenReturn("/api/v1/images/proxy/avatar.jpg");

        var response = service.listAvatars(1L, null, 0, 10);

        assertEquals(1, response.total());
        assertEquals("wx_user_30", response.items().get(0).wechatId());
        assertEquals("987654321", response.items().get(0).qq());
    }

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

    @Test
    void approveAllPendingRequiresExplicitConfirmation() {
        AppException exception = assertThrows(
                AppException.class,
                () -> service.approveAllPending(1L, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(goodsMapper, never()).findImagesByAuditStatus(ImageAuditStatusEnum.PENDING);
    }

    @Test
    void approveAllPendingApprovesImagesAndPublishesCompletedGoods() {
        GoodsImageDO first = image(10L, "images/first.jpg");
        first.setGoodsId(20L);
        GoodsImageDO second = image(11L, "images/second.jpg");
        second.setGoodsId(20L);
        when(goodsMapper.findImagesByAuditStatus(ImageAuditStatusEnum.PENDING)).thenReturn(List.of(first, second));
        when(goodsMapper.findImageById(10L)).thenReturn(first);
        when(goodsMapper.findImageById(11L)).thenReturn(second);
        when(goodsMapper.countImagesByGoodsId(20L)).thenReturn(2);
        when(goodsMapper.countApprovedImagesByGoodsId(20L)).thenReturn(1, 2);

        int count = service.approveAllPending(1L, "APPROVE_ALL_PENDING");

        assertEquals(2, count);
        verify(goodsMapper).updateImageAudit(10L, ImageAuditStatusEnum.APPROVED, null, 1L);
        verify(goodsMapper).updateImageAudit(11L, ImageAuditStatusEnum.APPROVED, null, 1L);
        verify(goodsMapper).updateAuditStatus(20L, GoodsStatusEnum.ON_SALE, null);
        verify(goodsListCacheInvalidator).evictAfterCommit();
    }

    @Test
    void rejectAllApprovedRequiresExplicitConfirmation() {
        AppException exception = assertThrows(
                AppException.class,
                () -> service.rejectAllApproved(1L, "误操作", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(goodsMapper, never()).findImagesByAuditStatus(ImageAuditStatusEnum.APPROVED);
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
