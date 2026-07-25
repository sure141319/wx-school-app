package com.campustrade.platform.audit.dto.request;

import jakarta.validation.constraints.Size;

public record ImageRejectRequestDTO(
        @Size(max = 500, message = "驳回原因不能超过500个字符") String remark,
        @Size(max = 64, message = "确认内容不能超过64个字符") String confirmation
) {
}
