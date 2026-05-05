package com.campustrade.platform.audit.mapper;

import com.campustrade.platform.audit.dataobject.AuditImageRecordDO;
import com.campustrade.platform.audit.dataobject.AvatarAuditRecordDO;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditImageMapper {

    List<AuditImageRecordDO> search(@Param("status") ImageAuditStatusEnum status,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    long countSearch(@Param("status") ImageAuditStatusEnum status);

    AuditImageRecordDO findByImageId(@Param("imageId") Long imageId);

    // ==================== 头像审核 ====================

    List<AvatarAuditRecordDO> searchAvatars(@Param("status") ImageAuditStatusEnum status,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    long countSearchAvatars(@Param("status") ImageAuditStatusEnum status);

    AvatarAuditRecordDO findAvatarByUserId(@Param("userId") Long userId);
}
