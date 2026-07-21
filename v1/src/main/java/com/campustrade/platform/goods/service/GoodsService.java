package com.campustrade.platform.goods.service;

import com.campustrade.platform.category.dataobject.CategoryDO;
import com.campustrade.platform.category.service.CategoryService;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.config.cache.GoodsListCacheInvalidator;
import com.campustrade.platform.goods.assembler.GoodsAssembler;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.dto.request.GoodsSaveRequestDTO;
import com.campustrade.platform.goods.dto.response.GoodsListItemResponseDTO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.goods.dto.response.MyGoodsListItemResponseDTO;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.service.UserService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class GoodsService {

    private static final ZoneId GOODS_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final GoodsMapper goodsMapper;
    private final CategoryService categoryService;
    private final UserService userService;
    private final GoodsAssembler goodsAssembler;
    private final UploadService uploadService;
    private final AppProperties appProperties;
    private final GoodsListCacheInvalidator goodsListCacheInvalidator;

    public GoodsService(GoodsMapper goodsMapper,
                        CategoryService categoryService,
                        UserService userService,
                        GoodsAssembler goodsAssembler,
                        UploadService uploadService,
                        AppProperties appProperties,
                        GoodsListCacheInvalidator goodsListCacheInvalidator) {
        this.goodsMapper = goodsMapper;
        this.categoryService = categoryService;
        this.userService = userService;
        this.goodsAssembler = goodsAssembler;
        this.uploadService = uploadService;
        this.appProperties = appProperties;
        this.goodsListCacheInvalidator = goodsListCacheInvalidator;
    }

    @Transactional
    public GoodsResponseDTO create(Long sellerId, GoodsSaveRequestDTO request) {
        userService.getById(sellerId);

        GoodsDO goods = new GoodsDO();
        goods.setSellerId(sellerId);
        goods.setCategoryId(validateCategoryId(request.categoryId()));
        goods.setTitle(request.title().trim());
        goods.setDescription(request.description().trim());
        goods.setPrice(request.price());
        goods.setConditionLevel(request.conditionLevel().trim());
        goods.setCampusLocation(request.campusLocation().trim());
        goods.setStatus(GoodsStatusEnum.PENDING_REVIEW);
        LocalDateTime now = LocalDateTime.now(GOODS_TIME_ZONE);
        goods.setCreatedAt(now);
        goods.setUpdatedAt(now);

        goodsMapper.insert(goods);
        replaceImages(
                goods.getId(),
                sellerId,
                request.imageUrls(),
                request.imageThumbnailUrls(),
                request.imageDisplayUrls(),
                request.imageAuditThumbnailUrls()
        );
        return getDetail(goods.getId());
    }

    @Transactional
    public GoodsResponseDTO update(Long currentUserId, Long goodsId, GoodsSaveRequestDTO request) {
        GoodsDO existing = getGoodsOrThrow(goodsId);
        validateOwner(currentUserId, existing);

        GoodsDO update = new GoodsDO();
        update.setId(goodsId);
        update.setCategoryId(validateCategoryId(request.categoryId()));
        update.setTitle(request.title().trim());
        update.setDescription(request.description().trim());
        update.setPrice(request.price());
        update.setConditionLevel(request.conditionLevel().trim());
        update.setCampusLocation(request.campusLocation().trim());
        update.setStatus(GoodsStatusEnum.PENDING_REVIEW);
        update.setAuditRemark(null);

        goodsMapper.update(update);
        replaceImages(
                goodsId,
                currentUserId,
                request.imageUrls(),
                request.imageThumbnailUrls(),
                request.imageDisplayUrls(),
                request.imageAuditThumbnailUrls()
        );
        resetImageAuditStatus(goodsId);
        tryAutoApprove(goodsId);
        goodsListCacheInvalidator.evictAfterCommit();
        return getDetail(goodsId);
    }

    @Transactional
    public void delete(Long currentUserId, Long goodsId) {
        GoodsDO goods = getGoodsOrThrow(goodsId);
        if (!isReviewer(currentUserId)) {
            validateOwner(currentUserId, goods);
        }

        List<GoodsImageDO> images = goodsMapper.findImagesByGoodsId(goodsId);
        for (GoodsImageDO image : images) {
            uploadService.deleteUploadGroupAfterCommit(
                    image.getImageUrl(),
                    image.getThumbnailUrl(),
                    image.getDisplayUrl(),
                    image.getAuditThumbnailUrl()
            );
        }

        goodsMapper.deleteById(goodsId);
        if (goods.getStatus() == GoodsStatusEnum.ON_SALE) {
            goodsListCacheInvalidator.evictAfterCommit();
        }
    }

    @Transactional
    public GoodsResponseDTO updateStatus(Long currentUserId, Long goodsId, GoodsStatusEnum status) {
        GoodsDO goods = getGoodsOrThrow(goodsId);
        validateOwner(currentUserId, goods);
        if (goods.getStatus() == status) {
            return getDetail(goodsId);
        }
        validateSellerStatusTransition(goodsId, goods.getStatus(), status);
        goodsMapper.updateStatus(goodsId, status);
        if (affectsPublicList(goods.getStatus(), status)) {
            goodsListCacheInvalidator.evictAfterCommit();
        }
        return getDetail(goodsId);
    }

    @Transactional(readOnly = true)
    public GoodsResponseDTO getDetail(Long goodsId) {
        GoodsDO goods = getGoodsOrThrow(goodsId);
        goods.setImages(goodsMapper.findImagesByGoodsId(goodsId));
        return goodsAssembler.toResponse(goods);
    }

    @Transactional(readOnly = true)
    public Object getDetailForViewer(Long goodsId, Long viewerUserId) {
        GoodsDO goods = getGoodsOrThrow(goodsId);
        boolean privileged = canViewInternalDetail(viewerUserId, goods);
        if (!privileged && goods.getStatus() != GoodsStatusEnum.ON_SALE) {
            throw new AppException(HttpStatus.NOT_FOUND, "商品不存在或未上架");
        }
        goods.setImages(goodsMapper.findImagesByGoodsId(goodsId));
        return privileged ? goodsAssembler.toResponse(goods) : goodsAssembler.toPublicResponse(goods);
    }

    @Transactional(readOnly = true)
    public GoodsDO getById(Long goodsId) {
        return getGoodsOrThrow(goodsId);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "goods:list",
            key = "new org.springframework.cache.interceptor.SimpleKey(#keyword == null ? null : #keyword.trim(), #categoryId, #status == null ? 'ON_SALE' : #status.name(), #page, #size)"
    )
    public PageResponse<GoodsListItemResponseDTO> list(String keyword, Long categoryId, GoodsStatusEnum status, int page, int size) {
        int offset = page * size;
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        GoodsStatusEnum effectiveStatus = publicListStatus(status);

        List<GoodsDO> goodsList = goodsMapper.searchList(normalizedKeyword, categoryId, effectiveStatus, size, offset);
        long total = goodsMapper.countSearch(normalizedKeyword, categoryId, effectiveStatus);
        attachCoverImages(goodsList);

        List<GoodsListItemResponseDTO> items = goodsList.stream().map(goodsAssembler::toListItemResponse).toList();
        return PageResponse.of(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<MyGoodsListItemResponseDTO> myGoods(Long sellerId, int page, int size) {
        userService.getById(sellerId);

        int offset = page * size;
        List<GoodsDO> goodsList = goodsMapper.findBySellerId(sellerId, size, offset);
        long total = goodsMapper.countBySellerId(sellerId);
        List<MyGoodsListItemResponseDTO> items = goodsList.stream()
                .map(goodsAssembler::toMyGoodsListItemResponse)
                .toList();
        return PageResponse.of(items, total, page, size);
    }

    private GoodsDO getGoodsOrThrow(Long goodsId) {
        GoodsDO goods = goodsMapper.findById(goodsId);
        if (goods == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return goods;
    }

    private Long validateCategoryId(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        CategoryDO category = categoryService.getById(categoryId);
        return category.getId();
    }

    private void validateOwner(Long userId, GoodsDO goods) {
        Long sellerId = goods.getSellerId();
        if (sellerId == null && goods.getSeller() != null) {
            sellerId = goods.getSeller().getId();
        }
        if (sellerId == null || !sellerId.equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权操作该商品");
        }
    }

    private boolean canViewInternalDetail(Long userId, GoodsDO goods) {
        if (userId == null) {
            return false;
        }
        Long sellerId = goods.getSellerId();
        if (sellerId == null && goods.getSeller() != null) {
            sellerId = goods.getSeller().getId();
        }
        return userId.equals(sellerId) || isReviewer(userId);
    }

    private GoodsStatusEnum publicListStatus(GoodsStatusEnum requestedStatus) {
        if (requestedStatus == null || requestedStatus == GoodsStatusEnum.ON_SALE) {
            return GoodsStatusEnum.ON_SALE;
        }
        throw new AppException(HttpStatus.BAD_REQUEST, "公开列表仅支持查看在售商品");
    }

    private boolean isReviewer(Long userId) {
        return userId != null && appProperties.getImageAudit().getReviewerUserIds().contains(userId);
    }

    private boolean affectsPublicList(GoodsStatusEnum oldStatus, GoodsStatusEnum newStatus) {
        return oldStatus == GoodsStatusEnum.ON_SALE || newStatus == GoodsStatusEnum.ON_SALE;
    }

    private void validateSellerStatusTransition(Long goodsId, GoodsStatusEnum currentStatus, GoodsStatusEnum requestedStatus) {
        if (currentStatus == GoodsStatusEnum.ON_SALE && requestedStatus == GoodsStatusEnum.OFF_SHELF) {
            return;
        }
        if (currentStatus == GoodsStatusEnum.OFF_SHELF && requestedStatus == GoodsStatusEnum.ON_SALE) {
            ensureAllImagesApprovedForSale(goodsId);
            return;
        }
        throw new AppException(HttpStatus.BAD_REQUEST, "当前状态不允许该操作");
    }

    private void ensureAllImagesApprovedForSale(Long goodsId) {
        int total = goodsMapper.countImagesByGoodsId(goodsId);
        int approved = goodsMapper.countApprovedImagesByGoodsId(goodsId);
        if (total == 0 || approved != total) {
            throw new AppException(HttpStatus.BAD_REQUEST, "商品图片未全部通过审核，不能上架");
        }
    }

    private void replaceImages(Long goodsId,
                               Long ownerUserId,
                               List<String> imageUrls,
                               List<String> imageThumbnailUrls,
                               List<String> imageDisplayUrls,
                               List<String> imageAuditThumbnailUrls) {
        List<GoodsImageDO> oldImages = goodsMapper.findImagesByGoodsId(goodsId);
        Map<String, GoodsImageDO> oldKeyMap = new HashMap<>();
        for (GoodsImageDO image : oldImages) {
            oldKeyMap.put(image.getImageUrl(), image);
        }

        Set<String> newKeys = new LinkedHashSet<>();
        Map<String, String> thumbnailByImageKey = new HashMap<>();
        Map<String, String> displayByImageKey = new HashMap<>();
        Map<String, String> auditThumbnailByImageKey = new HashMap<>();
        if (imageUrls != null) {
            for (int i = 0; i < imageUrls.size(); i++) {
                String url = imageUrls.get(i);
                String key = normalizeImageReference(url, oldKeyMap, ownerUserId);
                if (StringUtils.hasText(key)) {
                    newKeys.add(key);
                    GoodsImageDO existingImage = oldKeyMap.get(key);
                    if (existingImage == null) {
                        UploadService.ImageVariantKeys variants = uploadService.bindUploadedImageToGoods(
                                key,
                                ownerUserId,
                                goodsId
                        );
                        putIfPresent(thumbnailByImageKey, key, variants.thumbnailObjectKey());
                        putIfPresent(displayByImageKey, key, variants.displayObjectKey());
                        putIfPresent(auditThumbnailByImageKey, key, variants.auditThumbnailObjectKey());
                    } else {
                        String thumbnailKey = extractThumbnailKey(imageThumbnailUrls, i, existingImage, ownerUserId);
                        putIfPresent(thumbnailByImageKey, key, thumbnailKey);
                        String displayKey = extractDisplayKey(imageDisplayUrls, i, existingImage, ownerUserId);
                        putIfPresent(displayByImageKey, key, displayKey);
                        String auditThumbnailKey = extractAuditThumbnailKey(
                                imageAuditThumbnailUrls,
                                i,
                                existingImage,
                                ownerUserId
                        );
                        putIfPresent(auditThumbnailByImageKey, key, auditThumbnailKey);
                    }
                }
            }
        }

        for (GoodsImageDO oldImage : oldImages) {
            if (!newKeys.contains(oldImage.getImageUrl())) {
                uploadService.deleteUploadGroupAfterCommit(
                        oldImage.getImageUrl(),
                        oldImage.getThumbnailUrl(),
                        oldImage.getDisplayUrl(),
                        oldImage.getAuditThumbnailUrl()
                );
                goodsMapper.deleteImageById(oldImage.getId());
            }
        }

        List<GoodsImageDO> imagesToInsert = new ArrayList<>();
        int idx = 0;
        for (String key : newKeys) {
            GoodsImageDO existing = oldKeyMap.get(key);
            if (existing == null) {
                GoodsImageDO image = new GoodsImageDO();
                image.setGoodsId(goodsId);
                image.setImageUrl(key);
                image.setThumbnailUrl(thumbnailByImageKey.get(key));
                image.setDisplayUrl(displayByImageKey.get(key));
                image.setAuditThumbnailUrl(auditThumbnailByImageKey.get(key));
                image.setSortOrder(idx++);
                image.setAuditStatus(ImageAuditStatusEnum.PENDING);
                imagesToInsert.add(image);
            } else {
                goodsMapper.updateImageSortOrder(existing.getId(), idx++);
                String thumbnailKey = thumbnailByImageKey.get(key);
                String displayKey = displayByImageKey.get(key);
                String auditThumbnailKey = auditThumbnailByImageKey.get(key);
                String effectiveThumbnailKey = StringUtils.hasText(thumbnailKey) ? thumbnailKey : existing.getThumbnailUrl();
                String effectiveDisplayKey = StringUtils.hasText(displayKey) ? displayKey : existing.getDisplayUrl();
                String effectiveAuditThumbnailKey = StringUtils.hasText(auditThumbnailKey)
                        ? auditThumbnailKey
                        : existing.getAuditThumbnailUrl();
                if (!Objects.equals(effectiveThumbnailKey, existing.getThumbnailUrl())
                        || !Objects.equals(effectiveDisplayKey, existing.getDisplayUrl())
                        || !Objects.equals(effectiveAuditThumbnailKey, existing.getAuditThumbnailUrl())) {
                    goodsMapper.updateImageVariants(
                            existing.getId(),
                            effectiveThumbnailKey,
                            effectiveDisplayKey,
                            effectiveAuditThumbnailKey
                    );
                }
            }
        }

        if (!imagesToInsert.isEmpty()) {
            goodsMapper.batchInsertImages(goodsId, imagesToInsert);
        }
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String normalizeImageReference(String url, Map<String, GoodsImageDO> oldKeyMap, Long ownerUserId) {
        String trimmedUrl = url.trim();
        String objectKey = uploadService.extractObjectKey(trimmedUrl);
        if (StringUtils.hasText(objectKey) && oldKeyMap.containsKey(objectKey)) {
            return objectKey;
        }
        return uploadService.validateUploadedImageReference(trimmedUrl, "goods", ownerUserId);
    }

    private String extractThumbnailKey(List<String> imageThumbnailUrls,
                                       int index,
                                       GoodsImageDO existingImage,
                                       Long ownerUserId) {
        if (imageThumbnailUrls == null || index >= imageThumbnailUrls.size()) {
            return null;
        }
        String thumbnailUrl = imageThumbnailUrls.get(index);
        if (!StringUtils.hasText(thumbnailUrl)) {
            return null;
        }
        String trimmedThumbnailUrl = thumbnailUrl.trim();
        String thumbnailKey = uploadService.extractObjectKey(trimmedThumbnailUrl);
        if (existingImage != null
                && StringUtils.hasText(thumbnailKey)
                && thumbnailKey.equals(existingImage.getThumbnailUrl())) {
            return thumbnailKey;
        }
        return uploadService.validateUploadedThumbnailReference(trimmedThumbnailUrl, "goods", ownerUserId);
    }

    private String extractDisplayKey(List<String> imageDisplayUrls,
                                     int index,
                                     GoodsImageDO existingImage,
                                     Long ownerUserId) {
        if (imageDisplayUrls == null || index >= imageDisplayUrls.size()) {
            return null;
        }
        String displayUrl = imageDisplayUrls.get(index);
        if (!StringUtils.hasText(displayUrl)) {
            return null;
        }
        String trimmedDisplayUrl = displayUrl.trim();
        String displayKey = uploadService.extractObjectKey(trimmedDisplayUrl);
        if (existingImage != null
                && StringUtils.hasText(displayKey)
                && displayKey.equals(existingImage.getDisplayUrl())) {
            return displayKey;
        }
        return uploadService.validateUploadedDisplayReference(trimmedDisplayUrl, "goods", ownerUserId);
    }

    private String extractAuditThumbnailKey(List<String> imageAuditThumbnailUrls,
                                            int index,
                                            GoodsImageDO existingImage,
                                            Long ownerUserId) {
        if (imageAuditThumbnailUrls == null || index >= imageAuditThumbnailUrls.size()) {
            return null;
        }
        String auditThumbnailUrl = imageAuditThumbnailUrls.get(index);
        if (!StringUtils.hasText(auditThumbnailUrl)) {
            return null;
        }
        String trimmedAuditThumbnailUrl = auditThumbnailUrl.trim();
        String auditThumbnailKey = uploadService.extractObjectKey(trimmedAuditThumbnailUrl);
        if (existingImage != null
                && StringUtils.hasText(auditThumbnailKey)
                && auditThumbnailKey.equals(existingImage.getAuditThumbnailUrl())) {
            return auditThumbnailKey;
        }
        return uploadService.validateUploadedAuditThumbnailReference(
                trimmedAuditThumbnailUrl,
                "goods",
                ownerUserId
        );
    }

    private void resetImageAuditStatus(Long goodsId) {
        List<GoodsImageDO> images = goodsMapper.findImagesByGoodsId(goodsId);
        for (GoodsImageDO image : images) {
            if (image.getAuditStatus() == ImageAuditStatusEnum.REJECTED) {
                goodsMapper.updateImageAudit(image.getId(), ImageAuditStatusEnum.PENDING, null, null);
            }
        }
    }

    private void tryAutoApprove(Long goodsId) {
        int total = goodsMapper.countImagesByGoodsId(goodsId);
        if (total == 0) {
            goodsMapper.updateStatus(goodsId, GoodsStatusEnum.ON_SALE);
            return;
        }
        int approved = goodsMapper.countApprovedImagesByGoodsId(goodsId);
        if (approved == total) {
            goodsMapper.updateStatus(goodsId, GoodsStatusEnum.ON_SALE);
        }
    }

    private void attachImages(List<GoodsDO> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }

        List<Long> goodsIds = goodsList.stream().map(GoodsDO::getId).toList();
        List<GoodsImageDO> images = goodsMapper.findImagesByGoodsIds(goodsIds);

        Map<Long, List<GoodsImageDO>> grouped = new HashMap<>();
        for (GoodsImageDO image : images) {
            grouped.computeIfAbsent(image.getGoodsId(), ignored -> new ArrayList<>()).add(image);
        }

        for (GoodsDO goods : goodsList) {
            goods.setImages(grouped.getOrDefault(goods.getId(), new ArrayList<>()));
        }
    }

    private void attachCoverImages(List<GoodsDO> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }

        List<Long> goodsIds = goodsList.stream().map(GoodsDO::getId).toList();
        List<GoodsImageDO> coverImages = goodsMapper.findCoverImagesByGoodsIds(goodsIds);

        Map<Long, GoodsImageDO> coverImageMap = new HashMap<>();
        for (GoodsImageDO image : coverImages) {
            coverImageMap.putIfAbsent(image.getGoodsId(), image);
        }

        for (GoodsDO goods : goodsList) {
            GoodsImageDO coverImage = coverImageMap.get(goods.getId());
            goods.setImages(coverImage == null ? new ArrayList<>() : new ArrayList<>(List.of(coverImage)));
        }
    }
}
