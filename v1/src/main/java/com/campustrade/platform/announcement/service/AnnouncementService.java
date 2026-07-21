package com.campustrade.platform.announcement.service;

import com.campustrade.platform.announcement.dataobject.AnnouncementDO;
import com.campustrade.platform.announcement.dto.request.UpdateAnnouncementRequestDTO;
import com.campustrade.platform.announcement.dto.response.AnnouncementAdminResponseDTO;
import com.campustrade.platform.announcement.dto.response.AnnouncementPublicResponseDTO;
import com.campustrade.platform.announcement.mapper.AnnouncementMapper;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class AnnouncementService {
    private final AnnouncementMapper announcementMapper;
    private final AppProperties appProperties;

    public AnnouncementService(AnnouncementMapper announcementMapper, AppProperties appProperties) {
        this.announcementMapper = announcementMapper;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public AnnouncementPublicResponseDTO getCurrentPublic() {
        AnnouncementDO announcement = getCurrentOrThrow();
        if (!Boolean.TRUE.equals(announcement.getEnabled())) {
            return null;
        }
        return new AnnouncementPublicResponseDTO(
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getRevision()
        );
    }

    @Transactional(readOnly = true)
    public AnnouncementAdminResponseDTO getCurrentForAdmin(Long reviewerUserId) {
        ensureReviewer(reviewerUserId);
        return toAdminResponse(getCurrentOrThrow());
    }

    @Transactional
    public AnnouncementAdminResponseDTO update(Long reviewerUserId, UpdateAnnouncementRequestDTO request) {
        ensureReviewer(reviewerUserId);
        AnnouncementDO current = getCurrentOrThrow();
        String title = request.title().trim();
        String content = request.content().trim();

        boolean unchanged = Objects.equals(current.getTitle(), title)
                && Objects.equals(current.getContent(), content)
                && Objects.equals(current.getEnabled(), request.enabled());
        if (unchanged) {
            return toAdminResponse(current);
        }

        int updated = announcementMapper.updateCurrent(title, content, request.enabled());
        if (updated != 1) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "公告配置更新失败");
        }
        return toAdminResponse(getCurrentOrThrow());
    }

    private AnnouncementDO getCurrentOrThrow() {
        AnnouncementDO announcement = announcementMapper.findCurrent();
        if (announcement == null) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "公告配置不存在");
        }
        return announcement;
    }

    private void ensureReviewer(Long reviewerUserId) {
        if (reviewerUserId == null
                || !appProperties.getImageAudit().getReviewerUserIds().contains(reviewerUserId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权管理公告");
        }
    }

    private AnnouncementAdminResponseDTO toAdminResponse(AnnouncementDO announcement) {
        return new AnnouncementAdminResponseDTO(
                announcement.getTitle(),
                announcement.getContent(),
                Boolean.TRUE.equals(announcement.getEnabled()),
                announcement.getRevision(),
                announcement.getUpdatedAt()
        );
    }
}
