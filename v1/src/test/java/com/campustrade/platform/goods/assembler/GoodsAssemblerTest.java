package com.campustrade.platform.goods.assembler;

import com.campustrade.platform.category.assembler.CategoryAssembler;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.dto.response.GoodsListItemResponseDTO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.goods.dto.response.MyGoodsListItemResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsAssemblerTest {

    @Test
    void listCoverUsesThumbnailUrlWhenThumbnailExists() {
        UploadService uploadService = mock(UploadService.class);
        GoodsAssembler assembler = newAssembler(uploadService);
        GoodsDO goods = goodsWithImages(List.of(image(
                "images/2026/06/original.jpg",
                "images/2026/06/thumbs/original.webp",
                ImageAuditStatusEnum.APPROVED
        )));

        when(uploadService.getProxyUrl("images/2026/06/thumbs/original.webp"))
                .thenReturn("https://cdn.example.com/images/2026/06/thumbs/original.webp");

        GoodsListItemResponseDTO response = assembler.toListItemResponse(goods);

        assertEquals("https://cdn.example.com/images/2026/06/thumbs/original.webp", response.coverImageUrl());
        verify(uploadService).getProxyUrl("images/2026/06/thumbs/original.webp");
        verify(uploadService, never()).getProxyUrl("images/2026/06/original.jpg");
    }

    @Test
    void listCoverFallsBackToOriginalUrlForOldImagesWithoutThumbnail() {
        UploadService uploadService = mock(UploadService.class);
        GoodsAssembler assembler = newAssembler(uploadService);
        GoodsDO goods = goodsWithImages(List.of(image(
                "images/2026/06/original.jpg",
                null,
                ImageAuditStatusEnum.APPROVED
        )));

        when(uploadService.getProxyUrl("images/2026/06/original.jpg"))
                .thenReturn("https://cdn.example.com/images/2026/06/original.jpg");

        GoodsListItemResponseDTO response = assembler.toListItemResponse(goods);

        assertEquals("https://cdn.example.com/images/2026/06/original.jpg", response.coverImageUrl());
        verify(uploadService).getProxyUrl("images/2026/06/original.jpg");
    }

    @Test
    void listCoverShowsRealUrlForPendingImages() {
        UploadService uploadService = mock(UploadService.class);
        GoodsAssembler assembler = newAssembler(uploadService);
        GoodsDO goods = goodsWithImages(List.of(image(
                "images/2026/06/pending.jpg",
                null,
                ImageAuditStatusEnum.PENDING
        )));

        when(uploadService.getProxyUrl("images/2026/06/pending.jpg"))
                .thenReturn("https://cdn.example.com/images/2026/06/pending.jpg");

        GoodsListItemResponseDTO response = assembler.toListItemResponse(goods);

        assertEquals("https://cdn.example.com/images/2026/06/pending.jpg", response.coverImageUrl());
        verify(uploadService).getProxyUrl("images/2026/06/pending.jpg");
    }

    @Test
    void listCoverShowsRealUrlForRejectedImages() {
        UploadService uploadService = mock(UploadService.class);
        GoodsAssembler assembler = newAssembler(uploadService);
        GoodsDO goods = goodsWithImages(List.of(image(
                "images/2026/06/rejected.jpg",
                null,
                ImageAuditStatusEnum.REJECTED
        )));

        when(uploadService.getProxyUrl("images/2026/06/rejected.jpg"))
                .thenReturn("https://cdn.example.com/images/2026/06/rejected.jpg");

        GoodsListItemResponseDTO response = assembler.toListItemResponse(goods);

        assertEquals("https://cdn.example.com/images/2026/06/rejected.jpg", response.coverImageUrl());
        verify(uploadService).getProxyUrl("images/2026/06/rejected.jpg");
    }

    @Test
    void detailUsesHighQualityWebpDisplayVariantAndKeepsOriginalKey() {
        UploadService uploadService = mock(UploadService.class);
        GoodsAssembler assembler = newAssembler(uploadService);
        GoodsImageDO image = image(
                "images/2026/06/original.jpg",
                "images/2026/06/thumbs/original_thumb.webp",
                ImageAuditStatusEnum.APPROVED
        );
        image.setDisplayUrl("images/2026/06/display/original_display.webp");
        GoodsDO goods = goodsWithImages(List.of(image));
        when(uploadService.getProxyUrl("images/2026/06/display/original_display.webp"))
                .thenReturn("https://cdn.example.com/images/2026/06/display/original_display.webp");

        GoodsResponseDTO response = assembler.toResponse(goods);

        assertEquals(
                "https://cdn.example.com/images/2026/06/display/original_display.webp",
                response.imageUrls().get(0)
        );
        assertEquals("images/2026/06/original.jpg", response.imageKeys().get(0));
    }

    @Test
    void myGoodsListItemContainsOnlyManagementFields() {
        GoodsAssembler assembler = newAssembler(mock(UploadService.class));
        GoodsDO goods = goodsWithImages(List.of());
        goods.setAuditRemark("请补充实拍图");

        MyGoodsListItemResponseDTO response = assembler.toMyGoodsListItemResponse(goods);

        assertEquals(goods.getId(), response.id());
        assertEquals(goods.getTitle(), response.title());
        assertEquals(goods.getPrice(), response.price());
        assertEquals(goods.getStatus(), response.status());
        assertEquals(goods.getAuditRemark(), response.auditRemark());
    }

    private GoodsAssembler newAssembler(UploadService uploadService) {
        return new GoodsAssembler(
                mock(UserProfileAssembler.class),
                mock(CategoryAssembler.class),
                uploadService
        );
    }

    private GoodsDO goodsWithImages(List<GoodsImageDO> images) {
        GoodsDO goods = new GoodsDO();
        goods.setId(1L);
        goods.setTitle("MacBook Air");
        goods.setPrice(BigDecimal.valueOf(4999));
        goods.setConditionLevel("9成新");
        goods.setCampusLocation("Main Campus");
        goods.setStatus(GoodsStatusEnum.ON_SALE);
        goods.setCreatedAt(LocalDateTime.now());
        goods.setImages(new ArrayList<>(images));
        return goods;
    }

    private GoodsImageDO image(String imageUrl, String thumbnailUrl, ImageAuditStatusEnum auditStatus) {
        GoodsImageDO image = new GoodsImageDO();
        image.setImageUrl(imageUrl);
        image.setThumbnailUrl(thumbnailUrl);
        image.setAuditStatus(auditStatus);
        return image;
    }
}
