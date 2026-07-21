package com.campustrade.platform.announcement.mapper;

import com.campustrade.platform.announcement.dataobject.AnnouncementDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnnouncementMapper {

    AnnouncementDO findCurrent();

    int updateCurrent(@Param("title") String title,
                      @Param("content") String content,
                      @Param("enabled") boolean enabled);
}
