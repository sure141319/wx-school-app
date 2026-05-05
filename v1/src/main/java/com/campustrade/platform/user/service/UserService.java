package com.campustrade.platform.user.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.dto.request.UpdateProfileRequestDTO;
import com.campustrade.platform.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UploadService uploadService;

    public UserService(UserMapper userMapper, UploadService uploadService) {
        this.userMapper = userMapper;
        this.uploadService = uploadService;
    }

    @Transactional(readOnly = true)
    public UserDO getById(Long userId) {
        UserDO user = userMapper.findById(userId);
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    @Transactional
    public UserDO updateProfile(Long userId, UpdateProfileRequestDTO request) {
        UserDO user = getById(userId);
        String newAvatarUrl = request.avatarUrl() == null ? null : request.avatarUrl().trim();
        
        // 如果更换了头像，删除旧头像并设置为待审核
        boolean avatarChanged = newAvatarUrl != null && !newAvatarUrl.equals(user.getAvatarUrl());
        if (avatarChanged) {
            uploadService.deleteObject(user.getAvatarUrl());
        }
        
        userMapper.updateProfile(
                userId,
                request.nickname().trim(),
                newAvatarUrl);
        
        // 如果上传了新头像，设置为待审核状态
        if (avatarChanged) {
            userMapper.updateAvatarAuditStatus(userId, ImageAuditStatusEnum.PENDING, null, null);
        }
        
        return getById(userId);
    }
}
