package com.campustrade.platform.goods.controller;

import com.campustrade.platform.goods.dto.request.GoodsSaveRequestDTO;
import com.campustrade.platform.goods.dto.request.GoodsStatusUpdateRequestDTO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.service.GoodsService;
import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.security.AuthUtils;
import com.campustrade.platform.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/goods")
public class GoodsController {

    private final GoodsService goodsService;

    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

@GetMapping
    public ApiResponse<PageResponse<GoodsResponseDTO>> list(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) GoodsStatusEnum status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(goodsService.list(keyword, categoryId, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<GoodsResponseDTO> detail(@PathVariable Long id) {
        return ApiResponse.ok(goodsService.getDetail(id));
    }

    @PostMapping
    public ApiResponse<GoodsResponseDTO> create(@Valid @RequestBody GoodsSaveRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("商品发布成功", goodsService.create(principal.userId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GoodsResponseDTO> update(@PathVariable Long id, @Valid @RequestBody GoodsSaveRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("商品更新成功", goodsService.update(principal.userId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UserPrincipal principal = AuthUtils.currentUser();
        goodsService.delete(principal.userId(), id);
        return ApiResponse.ok("商品删除成功", null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<GoodsResponseDTO> updateStatus(@PathVariable Long id,
                                                   @Valid @RequestBody GoodsStatusUpdateRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("商品状态更新成功", goodsService.updateStatus(principal.userId(), id, request.status()));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResponse<GoodsResponseDTO>> myGoods(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok(goodsService.myGoods(principal.userId(), page, size));
    }
}

