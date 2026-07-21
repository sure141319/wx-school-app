package com.campustrade.platform.announcement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAnnouncementRequestDTO(
        @NotBlank(message = "公告标题不能为空")
        @Size(max = 50, message = "公告标题不能超过50个字符")
        String title,
        @NotBlank(message = "公告正文不能为空")
        @Size(max = 1000, message = "公告正文不能超过1000个字符")
        String content,
        @NotNull(message = "公告启用状态不能为空")
        Boolean enabled
) {
}
