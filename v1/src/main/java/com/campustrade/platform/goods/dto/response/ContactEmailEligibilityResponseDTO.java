package com.campustrade.platform.goods.dto.response;

public record ContactEmailEligibilityResponseDTO(
        boolean buyerEmailBound,
        boolean sellerEmailBound,
        boolean ownGoods
) {
}
