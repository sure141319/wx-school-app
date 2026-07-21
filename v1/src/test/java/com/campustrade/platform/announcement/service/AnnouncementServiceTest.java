package com.campustrade.platform.announcement.service;

import com.campustrade.platform.announcement.dataobject.AnnouncementDO;
import com.campustrade.platform.announcement.dto.request.UpdateAnnouncementRequestDTO;
import com.campustrade.platform.announcement.mapper.AnnouncementMapper;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementServiceTest {
    private final AnnouncementMapper announcementMapper = mock(AnnouncementMapper.class);
    private final AppProperties appProperties = appProperties();
    private final AnnouncementService service = new AnnouncementService(announcementMapper, appProperties);

    @Test
    void disabledAnnouncementIsNotReturnedPublicly() {
        when(announcementMapper.findCurrent()).thenReturn(announcement(false, 1L, "公告", "维护通知"));

        assertNull(service.getCurrentPublic());
    }

    @Test
    void enabledAnnouncementReturnsPublicFields() {
        when(announcementMapper.findCurrent()).thenReturn(announcement(true, 3L, "失物招领", "请到值班室领取"));

        var response = service.getCurrentPublic();

        assertEquals("失物招领", response.title());
        assertEquals("请到值班室领取", response.content());
        assertEquals(3L, response.revision());
    }

    @Test
    void unchangedUpdateDoesNotIncrementRevision() {
        AnnouncementDO current = announcement(true, 3L, "失物招领", "请到值班室领取");
        when(announcementMapper.findCurrent()).thenReturn(current);

        var response = service.update(1L, new UpdateAnnouncementRequestDTO(
                "失物招领",
                "请到值班室领取",
                true
        ));

        assertEquals(3L, response.revision());
        verify(announcementMapper, never()).updateCurrent("失物招领", "请到值班室领取", true);
    }

    @Test
    void changedUpdateTrimsTextAndReturnsIncrementedRevision() {
        AnnouncementDO current = announcement(true, 3L, "失物招领", "请到值班室领取");
        AnnouncementDO updated = announcement(true, 4L, "临时通知", "今晚维护");
        when(announcementMapper.findCurrent()).thenReturn(current, updated);
        when(announcementMapper.updateCurrent("临时通知", "今晚维护", true)).thenReturn(1);

        var response = service.update(1L, new UpdateAnnouncementRequestDTO(
                " 临时通知 ",
                " 今晚维护 ",
                true
        ));

        assertEquals(4L, response.revision());
        verify(announcementMapper).updateCurrent("临时通知", "今晚维护", true);
    }

    @Test
    void nonReviewerCannotReadAdminConfig() {
        AppException exception = assertThrows(
                AppException.class,
                () -> service.getCurrentForAdmin(99L)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(announcementMapper, never()).findCurrent();
    }

    private AnnouncementDO announcement(boolean enabled, long revision, String title, String content) {
        AnnouncementDO announcement = new AnnouncementDO();
        announcement.setId(1L);
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setEnabled(enabled);
        announcement.setRevision(revision);
        announcement.setCreatedAt(LocalDateTime.of(2026, 7, 21, 12, 0));
        announcement.setUpdatedAt(LocalDateTime.of(2026, 7, 21, 12, 0));
        return announcement;
    }

    private AppProperties appProperties() {
        AppProperties properties = new AppProperties();
        properties.getImageAudit().setReviewerUserIds(List.of(1L));
        return properties;
    }
}
