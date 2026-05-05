package com.campustrade.platform.goods.dataobject;

import com.campustrade.platform.category.dataobject.CategoryDO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.user.dataobject.UserDO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GoodsDO {

    private Long id;
    private Long sellerId;
    private Long categoryId;

    private String title;
    private String description;
    private BigDecimal price;
    private String conditionLevel;
    private String campusLocation;
    private GoodsStatusEnum status = GoodsStatusEnum.ON_SALE;
    private String auditRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private UserDO seller;
    private CategoryDO category;
    private List<GoodsImageDO> images = new ArrayList<>();
}
