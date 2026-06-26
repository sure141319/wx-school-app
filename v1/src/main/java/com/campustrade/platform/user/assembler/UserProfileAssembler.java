package com.campustrade.platform.user.assembler;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import com.campustrade.platform.user.dto.response.UserSummaryResponseDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
public class UserProfileAssembler {

    private static final Pattern QQ_PATTERN = Pattern.compile("^\\d{5,12}$");
    private static final String AVATAR_SOURCE_UPLOADED = "UPLOADED";
    private static final String AVATAR_SOURCE_QQ = "QQ";
    private static final String AVATAR_SOURCE_INITIAL = "INITIAL";

    private final UploadService uploadService;

    public UserProfileAssembler(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    public UserProfileResponseDTO toResponse(UserDO user) {
        VisibleAvatar visibleAvatar = toVisibleAvatar(user);
        return new UserProfileResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                visibleAvatar.url(),
                user.getWechatOpenid(),
                user.getWechatId(),
                user.getQq(),
                visibleAvatar.source()
        );
    }

    public UserSummaryResponseDTO toSummaryResponse(UserDO user) {
        VisibleAvatar visibleAvatar = toVisibleAvatar(user);
        return new UserSummaryResponseDTO(
                user.getId(),
                user.getNickname(),
                visibleAvatar.url(),
                visibleAvatar.source()
        );
    }

    private VisibleAvatar toVisibleAvatar(UserDO user) {
        if (user.getAvatarAuditStatus() == ImageAuditStatusEnum.APPROVED
                && StringUtils.hasText(user.getAvatarUrl())) {
            return new VisibleAvatar(uploadService.getProxyUrl(user.getAvatarUrl()), AVATAR_SOURCE_UPLOADED);
        }

        String qqAvatarUrl = qqAvatarUrl(user.getQq());
        if (StringUtils.hasText(qqAvatarUrl)) {
            return new VisibleAvatar(qqAvatarUrl, AVATAR_SOURCE_QQ);
        }

        return new VisibleAvatar("", AVATAR_SOURCE_INITIAL);
    }

    private String qqAvatarUrl(String qq) {
        if (!StringUtils.hasText(qq)) {
            return "";
        }
        String trimmedQq = qq.trim();
        if (!QQ_PATTERN.matcher(trimmedQq).matches()) {
            return "";
        }
        return "https://q1.qlogo.cn/g?b=qq&nk=" + trimmedQq + "&s=640";
    }

    private record VisibleAvatar(String url, String source) {
    }
}
