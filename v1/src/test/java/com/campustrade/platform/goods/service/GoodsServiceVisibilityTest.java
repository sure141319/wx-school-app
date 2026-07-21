package com.campustrade.platform.goods.service;

import com.campustrade.platform.category.service.CategoryService;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.config.cache.GoodsListCacheInvalidator;
import com.campustrade.platform.goods.assembler.GoodsAssembler;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.dto.request.GoodsSaveRequestDTO;
import com.campustrade.platform.goods.dto.response.GoodsListItemResponseDTO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.goods.dto.response.PublicGoodsResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoodsServiceVisibilityTest {

    private GoodsMapper goodsMapper;
    private GoodsAssembler goodsAssembler;
    private UploadService uploadService;
    private GoodsListCacheInvalidator goodsListCacheInvalidator;
    private GoodsService goodsService;

    @BeforeEach
    void setUp() {
        goodsMapper = mock(GoodsMapper.class);
        goodsAssembler = mock(GoodsAssembler.class);
        uploadService = mock(UploadService.class);
        goodsListCacheInvalidator = mock(GoodsListCacheInvalidator.class);

        AppProperties appProperties = new AppProperties();
        appProperties.getImageAudit().setReviewerUserIds(List.of(1L));

        goodsService = new GoodsService(
                goodsMapper,
                mock(CategoryService.class),
                mock(UserService.class),
                goodsAssembler,
                uploadService,
                appProperties,
                goodsListCacheInvalidator
        );
    }

    @Test
    void publicListDefaultsToOnSale() {
        GoodsDO goods = goods(10L, 20L, GoodsStatusEnum.ON_SALE);
        GoodsListItemResponseDTO item = new GoodsListItemResponseDTO(
                10L, "Mac", null, null, null, GoodsStatusEnum.ON_SALE, null, null, null, null
        );
        when(goodsMapper.searchList("mac", null, GoodsStatusEnum.ON_SALE, 10, 0)).thenReturn(List.of(goods));
        when(goodsMapper.countSearch("mac", null, GoodsStatusEnum.ON_SALE)).thenReturn(1L);
        when(goodsMapper.findCoverImagesByGoodsIds(List.of(10L))).thenReturn(List.of());
        when(goodsAssembler.toListItemResponse(goods)).thenReturn(item);

        var response = goodsService.list("  mac  ", null, null, 0, 10);

        assertEquals(1L, response.total());
        assertSame(item, response.items().get(0));
        verify(goodsMapper).searchList("mac", null, GoodsStatusEnum.ON_SALE, 10, 0);
        verify(goodsMapper).countSearch("mac", null, GoodsStatusEnum.ON_SALE);
    }

    @Test
    void publicListRejectsNonOnSaleStatus() {
        AppException exception = assertThrows(
                AppException.class,
                () -> goodsService.list(null, null, GoodsStatusEnum.PENDING_REVIEW, 0, 10)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(goodsMapper);
    }

    @Test
    void anonymousUserCannotReadPendingDetail() {
        when(goodsMapper.findById(10L)).thenReturn(goods(10L, 20L, GoodsStatusEnum.PENDING_REVIEW));

        AppException exception = assertThrows(
                AppException.class,
                () -> goodsService.getDetailForViewer(10L, null)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(goodsMapper, never()).findImagesByGoodsId(10L);
        verify(goodsAssembler, never()).toResponse(any());
        verify(goodsAssembler, never()).toPublicResponse(any());
    }

    @Test
    void anonymousUserReadsOnSaleDetailAsPublicResponse() {
        GoodsDO goods = goods(10L, 20L, GoodsStatusEnum.ON_SALE);
        PublicGoodsResponseDTO publicResponse = new PublicGoodsResponseDTO(
                10L, "Mac", null, null, null, null, GoodsStatusEnum.ON_SALE, null, null, List.of(), null, null
        );
        when(goodsMapper.findById(10L)).thenReturn(goods);
        when(goodsMapper.findImagesByGoodsId(10L)).thenReturn(List.of());
        when(goodsAssembler.toPublicResponse(goods)).thenReturn(publicResponse);

        Object response = goodsService.getDetailForViewer(10L, null);

        assertSame(publicResponse, response);
        verify(goodsAssembler).toPublicResponse(goods);
        verify(goodsAssembler, never()).toResponse(any());
    }

    @Test
    void ownerReadsPendingDetailAsInternalResponse() {
        GoodsDO goods = goods(10L, 20L, GoodsStatusEnum.PENDING_REVIEW);
        GoodsResponseDTO internalResponse = new GoodsResponseDTO(
                10L, "Mac", null, null, null, null, GoodsStatusEnum.PENDING_REVIEW, null, null, List.of(), List.of(), null, null, null
        );
        when(goodsMapper.findById(10L)).thenReturn(goods);
        when(goodsMapper.findImagesByGoodsId(10L)).thenReturn(List.of());
        when(goodsAssembler.toResponse(goods)).thenReturn(internalResponse);

        Object response = goodsService.getDetailForViewer(10L, 20L);

        assertSame(internalResponse, response);
        verify(goodsAssembler).toResponse(goods);
        verify(goodsAssembler, never()).toPublicResponse(any());
    }

    @Test
    void ownerCannotMovePendingGoodsToOffShelf() {
        when(goodsMapper.findById(10L)).thenReturn(goods(10L, 20L, GoodsStatusEnum.PENDING_REVIEW));

        AppException exception = assertThrows(
                AppException.class,
                () -> goodsService.updateStatus(20L, 10L, GoodsStatusEnum.OFF_SHELF)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(goodsMapper, never()).updateStatus(10L, GoodsStatusEnum.OFF_SHELF);
    }

    @Test
    void ownerCannotPutOffShelfGoodsOnSaleWhenImagesAreNotAllApproved() {
        when(goodsMapper.findById(10L)).thenReturn(goods(10L, 20L, GoodsStatusEnum.OFF_SHELF));
        when(goodsMapper.countImagesByGoodsId(10L)).thenReturn(2);
        when(goodsMapper.countApprovedImagesByGoodsId(10L)).thenReturn(1);

        AppException exception = assertThrows(
                AppException.class,
                () -> goodsService.updateStatus(20L, 10L, GoodsStatusEnum.ON_SALE)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(goodsMapper, never()).updateStatus(10L, GoodsStatusEnum.ON_SALE);
    }

    @Test
    void ownerCanPutOffShelfGoodsOnSaleWhenImagesAreAllApproved() {
        GoodsDO offShelfGoods = goods(10L, 20L, GoodsStatusEnum.OFF_SHELF);
        GoodsDO onSaleGoods = goods(10L, 20L, GoodsStatusEnum.ON_SALE);
        GoodsResponseDTO internalResponse = new GoodsResponseDTO(
                10L, "Mac", null, null, null, null, GoodsStatusEnum.ON_SALE, null, null, List.of(), List.of(), null, null, null
        );
        when(goodsMapper.findById(10L)).thenReturn(offShelfGoods, onSaleGoods);
        when(goodsMapper.countImagesByGoodsId(10L)).thenReturn(2);
        when(goodsMapper.countApprovedImagesByGoodsId(10L)).thenReturn(2);
        when(goodsMapper.findImagesByGoodsId(10L)).thenReturn(List.of());
        when(goodsAssembler.toResponse(onSaleGoods)).thenReturn(internalResponse);

        GoodsResponseDTO response = goodsService.updateStatus(20L, 10L, GoodsStatusEnum.ON_SALE);

        assertSame(internalResponse, response);
        verify(goodsMapper).updateStatus(10L, GoodsStatusEnum.ON_SALE);
    }

    @Test
    void updateAllowsKeepingLegacyImagesAlreadyAttachedToGoods() {
        String legacyKey = "images/2026/04/legacy.jpg";
        String legacyProxyUrl = "https://www.ahut-campus.site/api/v1/images/2026/04/legacy.jpg";
        GoodsDO existing = goods(10L, 20L, GoodsStatusEnum.ON_SALE);
        GoodsDO updated = goods(10L, 20L, GoodsStatusEnum.PENDING_REVIEW);
        GoodsImageDO legacyImage = image(5L, 10L, legacyKey, ImageAuditStatusEnum.APPROVED);
        GoodsResponseDTO response = new GoodsResponseDTO(
                10L, "Mac", null, null, null, null, GoodsStatusEnum.PENDING_REVIEW, null, null, List.of(), List.of(), null, null, null
        );
        when(goodsMapper.findById(10L)).thenReturn(existing, updated);
        when(goodsMapper.findImagesByGoodsId(10L)).thenReturn(List.of(legacyImage), List.of(legacyImage), List.of(legacyImage));
        when(uploadService.extractObjectKey(legacyProxyUrl)).thenReturn(legacyKey);
        when(goodsMapper.countImagesByGoodsId(10L)).thenReturn(1);
        when(goodsMapper.countApprovedImagesByGoodsId(10L)).thenReturn(0);
        when(goodsAssembler.toResponse(updated)).thenReturn(response);

        GoodsResponseDTO result = goodsService.update(
                20L,
                10L,
                new GoodsSaveRequestDTO(
                        "Mac",
                        "desc",
                        BigDecimal.valueOf(9.9),
                        "new",
                        "campus",
                        null,
                        List.of(legacyProxyUrl),
                        null
                )
        );

        assertSame(response, result);
        verify(uploadService, never()).validateUploadedImageReference(legacyProxyUrl, "goods", 20L);
        verify(goodsMapper, never()).deleteImageById(5L);
    }

    private GoodsDO goods(Long goodsId, Long sellerId, GoodsStatusEnum status) {
        UserDO seller = new UserDO();
        seller.setId(sellerId);

        GoodsDO goods = new GoodsDO();
        goods.setId(goodsId);
        goods.setSellerId(sellerId);
        goods.setSeller(seller);
        goods.setStatus(status);
        return goods;
    }

    private GoodsImageDO image(Long imageId, Long goodsId, String imageUrl, ImageAuditStatusEnum auditStatus) {
        GoodsImageDO image = new GoodsImageDO();
        image.setId(imageId);
        image.setGoodsId(goodsId);
        image.setImageUrl(imageUrl);
        image.setSortOrder(0);
        image.setAuditStatus(auditStatus);
        return image;
    }
}
