package com.campustrade.platform.audit.controller;

import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.goods.dto.response.GoodsListItemResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.service.GoodsService;
import com.campustrade.platform.security.AuthUtils;
import com.campustrade.platform.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/audit/goods")
public class AuditGoodsController {

    private final GoodsService goodsService;

    public AuditGoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    @GetMapping
    public ApiResponse<PageResponse<GoodsListItemResponseDTO>> list(
            @RequestParam(required = false)
            @Size(max = 100, message = "搜索关键词不能超过100个字符") String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) GoodsStatusEnum status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "页码不能小于0")
            @Max(value = 100000, message = "页码不能超过100000") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 50, message = "每页数量不能超过50") int size) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok(goodsService.listForReviewer(
                principal.userId(),
                keyword,
                categoryId,
                status,
                page,
                size
        ));
    }
}
