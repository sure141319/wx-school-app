package com.campustrade.platform.user.mapper;

import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.user.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper {

    UserDO findById(@Param("id") Long id);

    UserDO findByEmail(@Param("email") String email);

    int countByEmail(@Param("email") String email);

    int insert(UserDO user);

    int updateAuthState(@Param("id") Long id,
                        @Param("failedLoginCount") int failedLoginCount,
                        @Param("lockedUntil") LocalDateTime lockedUntil);

    int updatePasswordAndUnlock(@Param("id") Long id,
                                @Param("passwordHash") String passwordHash,
                                @Param("failedLoginCount") int failedLoginCount,
                                @Param("lockedUntil") LocalDateTime lockedUntil);

    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("avatarUrl") String avatarUrl);

    int updateAvatarAuditStatus(@Param("id") Long id,
                                @Param("avatarAuditStatus") ImageAuditStatusEnum avatarAuditStatus,
                                @Param("avatarAuditRemark") String avatarAuditRemark,
                                @Param("avatarAuditedBy") Long avatarAuditedBy);
}
