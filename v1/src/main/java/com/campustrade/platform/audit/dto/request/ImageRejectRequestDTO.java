package com.campustrade.platform.audit.dto.request;

import jakarta.validation.constraints.Size;

public record ImageRejectRequestDTO(
        @Size(max = 500) String remark
) {
}
