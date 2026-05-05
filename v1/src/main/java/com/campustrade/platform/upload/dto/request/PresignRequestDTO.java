package com.campustrade.platform.upload.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PresignRequestDTO(@NotEmpty List<String> urls) {
}
