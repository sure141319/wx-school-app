package com.campustrade.platform.announcement.dataobject;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AnnouncementDO {
    private Long id;
    private String title;
    private String content;
    private Boolean enabled;
    private Long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
