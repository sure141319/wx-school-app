package com.campustrade.platform.goods.service;

import com.campustrade.platform.auth.service.MailService;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dto.response.ContactEmailEligibilityResponseDTO;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.ratelimit.ContactEmailRateLimiter;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsContactServiceTest {

    private final GoodsService goodsService = mock(GoodsService.class);
    private final UserService userService = mock(UserService.class);
    private final MailService mailService = mock(MailService.class);
    private final ContactEmailRateLimiter contactEmailRateLimiter = mock(ContactEmailRateLimiter.class);
    private final GoodsContactService contactService = new GoodsContactService(
            goodsService, userService, mailService, contactEmailRateLimiter
    );

    @BeforeEach
    void allowContactEmailByDefault() {
        when(contactEmailRateLimiter.tryAcquire(anyLong(), anyLong()))
                .thenReturn(ContactEmailRateLimiter.Result.ACQUIRED);
    }

    @Test
    void eligibilityReportsBothBindingStatesWithoutReturningAddresses() {
        UserDO buyer = user(2L, "买家", "buyer@qq.com");
        GoodsDO goods = goods(user(1L, "卖家", "seller@qq.com"));
        stubContext(buyer, goods);

        ContactEmailEligibilityResponseDTO result = contactService.getEligibility(2L, 10L);

        assertTrue(result.buyerEmailBound());
        assertTrue(result.sellerEmailBound());
        assertFalse(result.ownGoods());
    }

    @Test
    void eligibilityReportsBuyerWithoutEmail() {
        UserDO buyer = user(2L, "买家", null);
        GoodsDO goods = goods(user(1L, "卖家", "seller@qq.com"));
        stubContext(buyer, goods);

        ContactEmailEligibilityResponseDTO result = contactService.getEligibility(2L, 10L);

        assertFalse(result.buyerEmailBound());
        assertTrue(result.sellerEmailBound());
        assertFalse(result.ownGoods());
    }

    @Test
    void eligibilityIdentifiesTheSellersOwnGoods() {
        UserDO seller = user(2L, "卖家", "seller@qq.com");
        GoodsDO goods = goods(seller);
        stubContext(seller, goods);

        ContactEmailEligibilityResponseDTO result = contactService.getEligibility(2L, 10L);

        assertTrue(result.ownGoods());
    }

    @Test
    void sendContactEmailNotifiesSellerAndUsesBuyerReplyAddress() {
        UserDO buyer = user(2L, "软工-231", "buyer@qq.com");
        UserDO seller = user(1L, "卖家", "seller@qq.com");
        GoodsDO goods = goods(seller);
        stubContext(buyer, goods);
        when(mailService.sendGoodsContactNotification(
                "seller@qq.com", "buyer@qq.com", "软工-231", "校园二手教材", new BigDecimal("18.00")
        )).thenReturn(true);

        contactService.sendContactEmail(2L, 10L);

        verify(mailService).sendGoodsContactNotification(
                "seller@qq.com", "buyer@qq.com", "软工-231", "校园二手教材", new BigDecimal("18.00")
        );
        verify(contactEmailRateLimiter, never()).release(2L, 10L);
    }

    @Test
    void sendContactEmailRejectsBuyerWithoutEmail() {
        UserDO buyer = user(2L, "买家", null);
        GoodsDO goods = goods(user(1L, "卖家", "seller@qq.com"));
        stubContext(buyer, goods);

        AppException ex = assertThrows(AppException.class, () -> contactService.sendContactEmail(2L, 10L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("请先绑定邮箱", ex.getMessage());
        verify(mailService, never()).sendGoodsContactNotification(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void sendContactEmailRejectsTheSellersOwnGoodsBeforeSending() {
        UserDO seller = user(2L, "卖家", "seller@qq.com");
        GoodsDO goods = goods(seller);
        stubContext(seller, goods);

        AppException ex = assertThrows(AppException.class, () -> contactService.sendContactEmail(2L, 10L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("不能联系自己发布的商品", ex.getMessage());
        verify(mailService, never()).sendGoodsContactNotification(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void sendContactEmailRejectsSellerWithoutEmail() {
        UserDO buyer = user(2L, "买家", "buyer@qq.com");
        GoodsDO goods = goods(user(1L, "卖家", null));
        stubContext(buyer, goods);

        AppException ex = assertThrows(AppException.class, () -> contactService.sendContactEmail(2L, 10L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("卖家未绑定邮箱，无法发送", ex.getMessage());
    }

    @Test
    void sendContactEmailSurfacesDeliveryFailure() {
        UserDO buyer = user(2L, "买家", "buyer@qq.com");
        GoodsDO goods = goods(user(1L, "卖家", "seller@qq.com"));
        stubContext(buyer, goods);
        when(mailService.sendGoodsContactNotification(
                "seller@qq.com", "buyer@qq.com", "买家", "校园二手教材", new BigDecimal("18.00")
        )).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> contactService.sendContactEmail(2L, 10L));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("邮件发送失败，请稍后重试", ex.getMessage());
        verify(contactEmailRateLimiter).release(2L, 10L);
    }

    @Test
    void sendContactEmailRejectsRepeatedContactBeforeSending() {
        UserDO buyer = user(2L, "买家", "buyer@qq.com");
        GoodsDO goods = goods(user(1L, "卖家", "seller@qq.com"));
        stubContext(buyer, goods);
        when(contactEmailRateLimiter.tryAcquire(2L, 10L))
                .thenReturn(ContactEmailRateLimiter.Result.CONTACT_COOLDOWN);

        AppException ex = assertThrows(AppException.class, () -> contactService.sendContactEmail(2L, 10L));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("已联系过该商品卖家，请稍后再试", ex.getMessage());
        verify(mailService, never()).sendGoodsContactNotification(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void sendContactEmailRejectsHourlyLimitBeforeSending() {
        UserDO buyer = user(2L, "买家", "buyer@qq.com");
        GoodsDO goods = goods(user(1L, "卖家", "seller@qq.com"));
        stubContext(buyer, goods);
        when(contactEmailRateLimiter.tryAcquire(2L, 10L))
                .thenReturn(ContactEmailRateLimiter.Result.HOURLY_LIMIT);

        AppException ex = assertThrows(AppException.class, () -> contactService.sendContactEmail(2L, 10L));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("联系卖家操作过于频繁，请稍后再试", ex.getMessage());
    }

    @Test
    void contactEmailRejectsOffShelfGoods() {
        UserDO buyer = user(2L, "买家", "buyer@qq.com");
        GoodsDO goods = goods(user(1L, "卖家", "seller@qq.com"));
        goods.setStatus(GoodsStatusEnum.OFF_SHELF);
        stubContext(buyer, goods);

        AppException ex = assertThrows(AppException.class, () -> contactService.getEligibility(2L, 10L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("商品不存在或未上架", ex.getMessage());
    }

    private void stubContext(UserDO buyer, GoodsDO goods) {
        when(userService.getById(2L)).thenReturn(buyer);
        when(goodsService.getById(10L)).thenReturn(goods);
    }

    private UserDO user(Long id, String nickname, String email) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setNickname(nickname);
        user.setEmail(email);
        return user;
    }

    private GoodsDO goods(UserDO seller) {
        GoodsDO goods = new GoodsDO();
        goods.setId(10L);
        goods.setSellerId(seller.getId());
        goods.setSeller(seller);
        goods.setTitle("校园二手教材");
        goods.setPrice(new BigDecimal("18.00"));
        goods.setStatus(GoodsStatusEnum.ON_SALE);
        return goods;
    }
}
