package com.campustrade.platform.goods.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record GoodsSaveRequestDTO(
        @NotBlank(message = "商品标题不能为空")
        @Size(max = 120, message = "商品标题不能超过120个字符") String title,
        @NotBlank(message = "商品描述不能为空")
        @Size(max = 5000, message = "商品描述不能超过5000个字符") String description,
        @NotNull(message = "商品价格不能为空")
        @DecimalMin(value = "0.01", message = "商品价格必须大于0") BigDecimal price,
        @NotBlank(message = "商品成色不能为空")
        @Size(max = 50, message = "商品成色不能超过50个字符") String conditionLevel,
        @NotBlank(message = "交易地点不能为空")
        @Size(max = 120, message = "交易地点不能超过120个字符") String campusLocation,
        Long categoryId,
        @NotEmpty(message = "请至少上传一张商品图片")
        @Size(max = 9, message = "商品图片不能超过9张")
        List<
                @NotBlank(message = "商品图片地址不能为空")
                @Size(max = 500, message = "商品图片地址过长")
                String> imageUrls,
        @Size(max = 9, message = "商品缩略图不能超过9张")
        List<@Size(max = 500, message = "商品缩略图地址过长") String> imageThumbnailUrls,
        @Size(max = 9, message = "商品展示图不能超过9张")
        List<@Size(max = 500, message = "商品展示图地址过长") String> imageDisplayUrls,
        @Size(max = 9, message = "商品审核图不能超过9张")
        List<@Size(max = 500, message = "商品审核图地址过长") String> imageAuditThumbnailUrls
) {
    public GoodsSaveRequestDTO(String title,
                               String description,
                               BigDecimal price,
                               String conditionLevel,
                               String campusLocation,
                               Long categoryId,
                               List<String> imageUrls,
                               List<String> imageThumbnailUrls) {
        this(
                title,
                description,
                price,
                conditionLevel,
                campusLocation,
                categoryId,
                imageUrls,
                imageThumbnailUrls,
                null,
                null
        );
    }
}

