package com.campustrade.platform.goods.assembler;

import com.campustrade.platform.category.assembler.CategoryAssembler;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.dto.response.GoodsListItemResponseDTO;
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
    void listCoverUsesThumbnailUrlWhenApprovedThumbnailExists() {
        UploadService uploadService = mock(UploadService.class);
        GoodsAssembler assembler = newAssembler(uploadService);
        GoodsDO goods = goodsWithImages(List.of(approvedImage(
                "images/2026/06/original.jpg",
                "images/2026/06/thumbs/original.webp"
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
        GoodsDO goods = goodsWithImages(List.of(approvedImage(
                "images/2026/06/original.jpg",
                null
        )));

        when(uploadService.getProxyUrl("images/2026/06/original.jpg"))
                .thenReturn("https://cdn.example.com/images/2026/06/original.jpg");

        GoodsListItemResponseDTO response = assembler.toListItemResponse(goods);

        assertEquals("https://cdn.example.com/images/2026/06/original.jpg", response.coverImageUrl());
        verify(uploadService).getProxyUrl("images/2026/06/original.jpg");
    }

    private GoodsAssembler newAssembler(UploadService uploadService) {
        AppProperties appProperties = new AppProperties();
        return new GoodsAssembler(
                mock(UserProfileAssembler.class),
                mock(CategoryAssembler.class),
                uploadService,
                appProperties
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

    private GoodsImageDO approvedImage(String imageUrl, String thumbnailUrl) {
        GoodsImageDO image = new GoodsImageDO();
        image.setImageUrl(imageUrl);
        image.setThumbnailUrl(thumbnailUrl);
        image.setAuditStatus(ImageAuditStatusEnum.APPROVED);
        return image;
    }
}
