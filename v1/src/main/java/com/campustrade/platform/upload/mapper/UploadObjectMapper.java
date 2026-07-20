package com.campustrade.platform.upload.mapper;

import com.campustrade.platform.upload.dataobject.UploadObjectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UploadObjectMapper {

    Long lockUserForUpload(@Param("userId") Long userId);

    long countAllByUser(@Param("userId") Long userId);

    long sumAllBytesByUser(@Param("userId") Long userId);

    long countStagedByUser(@Param("userId") Long userId);

    long sumStagedBytesByUser(@Param("userId") Long userId);

    int insert(UploadObjectDO uploadObject);

    UploadObjectDO findByObjectKey(@Param("objectKey") String objectKey);

    UploadObjectDO findByObjectKeyForUpdate(@Param("objectKey") String objectKey);

    int markStaged(@Param("id") Long id,
                   @Param("thumbnailObjectKey") String thumbnailObjectKey,
                   @Param("displayObjectKey") String displayObjectKey,
                   @Param("auditThumbnailObjectKey") String auditThumbnailObjectKey,
                   @Param("totalSizeBytes") long totalSizeBytes,
                   @Param("updatedAt") LocalDateTime updatedAt);

    int updateVariantsByObjectKey(@Param("objectKey") String objectKey,
                                  @Param("thumbnailObjectKey") String thumbnailObjectKey,
                                  @Param("displayObjectKey") String displayObjectKey,
                                  @Param("auditThumbnailObjectKey") String auditThumbnailObjectKey,
                                  @Param("variantSizeBytes") long variantSizeBytes,
                                  @Param("updatedAt") LocalDateTime updatedAt);

    int bind(@Param("id") Long id,
             @Param("boundType") String boundType,
             @Param("boundId") Long boundId,
             @Param("updatedAt") LocalDateTime updatedAt);

    int markDeleting(@Param("id") Long id,
                     @Param("retryAt") LocalDateTime retryAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int markExpiredDeleting(@Param("id") Long id,
                            @Param("now") LocalDateTime now,
                            @Param("retryAt") LocalDateTime retryAt);

    List<UploadObjectDO> findExpired(@Param("now") LocalDateTime now,
                                     @Param("limit") int limit);

    int deleteById(@Param("id") Long id);
}
