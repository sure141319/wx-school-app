package com.campustrade.platform.message.dataobject;

import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.user.dataobject.UserDO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ConversationDO {

    private Long id;
    private Long goodsId;
    private Long buyerId;
    private Long sellerId;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;

    private String goodsCoverImage;
    private GoodsDO goods;
    private UserDO buyer;
    private UserDO seller;
}
