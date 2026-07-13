package com.campustrade.platform.goods.service;

import com.campustrade.platform.auth.service.MailService;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dto.response.ContactEmailEligibilityResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class GoodsContactService {

    private final GoodsService goodsService;
    private final UserService userService;
    private final MailService mailService;

    public GoodsContactService(GoodsService goodsService,
                               UserService userService,
                               MailService mailService) {
        this.goodsService = goodsService;
        this.userService = userService;
        this.mailService = mailService;
    }

    @Transactional(readOnly = true)
    public ContactEmailEligibilityResponseDTO getEligibility(Long buyerId, Long goodsId) {
        ContactContext context = loadContext(buyerId, goodsId);
        return new ContactEmailEligibilityResponseDTO(
                StringUtils.hasText(context.buyer().getEmail()),
                StringUtils.hasText(context.seller().getEmail()),
                isOwnGoods(context)
        );
    }

    public void sendContactEmail(Long buyerId, Long goodsId) {
        ContactContext context = loadContext(buyerId, goodsId);
        if (isOwnGoods(context)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "不能联系自己发布的商品");
        }
        if (!StringUtils.hasText(context.buyer().getEmail())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "请先绑定邮箱");
        }
        if (!StringUtils.hasText(context.seller().getEmail())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "卖家未绑定邮箱，无法发送");
        }

        boolean delivered = mailService.sendGoodsContactNotification(
                context.seller().getEmail(),
                context.buyer().getEmail(),
                context.buyer().getNickname(),
                context.goods().getTitle(),
                context.goods().getPrice()
        );
        if (!delivered) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请稍后重试");
        }
    }

    private ContactContext loadContext(Long buyerId, Long goodsId) {
        UserDO buyer = userService.getById(buyerId);
        GoodsDO goods = goodsService.getById(goodsId);
        if (goods.getStatus() != GoodsStatusEnum.ON_SALE) {
            throw new AppException(HttpStatus.NOT_FOUND, "商品不存在或未上架");
        }

        UserDO seller = goods.getSeller();
        if (seller == null) {
            seller = userService.getById(goods.getSellerId());
        }
        return new ContactContext(goods, buyer, seller);
    }

    private boolean isOwnGoods(ContactContext context) {
        return Objects.equals(context.buyer().getId(), context.seller().getId());
    }

    private record ContactContext(GoodsDO goods, UserDO buyer, UserDO seller) {
    }
}
