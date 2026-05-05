package com.campustrade.platform.goods.service;

import com.campustrade.platform.category.dataobject.CategoryDO;
import com.campustrade.platform.category.service.CategoryService;
import com.campustrade.platform.common.AppException;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.goods.assembler.GoodsAssembler;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.dataobject.GoodsImageDO;
import com.campustrade.platform.goods.dto.request.GoodsSaveRequestDTO;
import com.campustrade.platform.goods.dto.response.GoodsResponseDTO;
import com.campustrade.platform.goods.enums.ImageAuditStatusEnum;
import com.campustrade.platform.goods.enums.GoodsStatusEnum;
import com.campustrade.platform.goods.mapper.GoodsMapper;
import com.campustrade.platform.message.mapper.ConversationMapper;
import com.campustrade.platform.upload.service.UploadService;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.service.UserService;
import org.springframework.cache.annotation.CacheEvict;
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
import java.util.Set;

@Service
public class GoodsService {

    private final GoodsMapper goodsMapper;
    private final CategoryService categoryService;
    private final UserService userService;
    private final ConversationMapper conversationMapper;
    private final GoodsAssembler goodsAssembler;
    private final UploadService uploadService;
    private final AppProperties appProperties;

    public GoodsService(GoodsMapper goodsMapper,
                        CategoryService categoryService,
                        UserService userService,
                        ConversationMapper conversationMapper,
                        GoodsAssembler goodsAssembler,
                        UploadService uploadService,
                        AppProperties appProperties) {
        this.goodsMapper = goodsMapper;
        this.categoryService = categoryService;
        this.userService = userService;
        this.conversationMapper = conversationMapper;
        this.goodsAssembler = goodsAssembler;
        this.uploadService = uploadService;
        this.appProperties = appProperties;
    }

    @Transactional
    @CacheEvict(cacheNames = "goods:list", allEntries = true)
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

        goodsMapper.insert(goods);
        replaceImages(goods.getId(), request.imageUrls());
        return getDetail(goods.getId());
    }

    @Transactional
    @CacheEvict(cacheNames = "goods:list", allEntries = true)
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
        replaceImages(goodsId, request.imageUrls());
        tryAutoApprove(goodsId);
        return getDetail(goodsId);
    }

    @Transactional
    @CacheEvict(cacheNames = "goods:list", allEntries = true)
    public void delete(Long currentUserId, Long goodsId) {
        GoodsDO goods = getGoodsOrThrow(goodsId);
        if (!isReviewer(currentUserId)) {
            validateOwner(currentUserId, goods);
        }

        List<GoodsImageDO> images = goodsMapper.findImagesByGoodsId(goodsId);
        for (GoodsImageDO image : images) {
            uploadService.deleteObject(image.getImageUrl());
        }

        conversationMapper.deleteByGoodsId(goodsId);
        goodsMapper.deleteById(goodsId);
    }

    @Transactional
    @CacheEvict(cacheNames = "goods:list", allEntries = true)
    public GoodsResponseDTO updateStatus(Long currentUserId, Long goodsId, GoodsStatusEnum status) {
        GoodsDO goods = getGoodsOrThrow(goodsId);
        validateOwner(currentUserId, goods);
        if (goods.getStatus() == status) {
            return getDetail(goodsId);
        }
        if (goods.getStatus() == GoodsStatusEnum.OFF_SHELF && status == GoodsStatusEnum.OFF_SHELF) {
            return getDetail(goodsId);
        }
        if (status == GoodsStatusEnum.ON_SALE && goods.getStatus() != GoodsStatusEnum.OFF_SHELF) {
            throw new AppException(HttpStatus.BAD_REQUEST, "当前状态不允许直接上架");
        }
        goodsMapper.updateStatus(goodsId, status);
        return getDetail(goodsId);
    }

    @Transactional(readOnly = true)
    public GoodsResponseDTO getDetail(Long goodsId) {
        GoodsDO goods = getGoodsOrThrow(goodsId);
        goods.setImages(goodsMapper.findImagesByGoodsId(goodsId));
        return goodsAssembler.toResponse(goods);
    }

    @Transactional(readOnly = true)
    public GoodsDO getById(Long goodsId) {
        return getGoodsOrThrow(goodsId);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "goods:list",
            key = "new org.springframework.cache.interceptor.SimpleKey(#keyword == null ? null : #keyword.trim(), #categoryId, #status == null ? null : #status.name(), #page, #size)"
    )
    public PageResponse<GoodsResponseDTO> list(String keyword, Long categoryId, GoodsStatusEnum status, int page, int size) {
        int offset = page * size;
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        List<GoodsDO> goodsList = goodsMapper.search(normalizedKeyword, categoryId, status, size, offset);
        long total = goodsMapper.countSearch(normalizedKeyword, categoryId, status);
        attachImages(goodsList);

        List<GoodsResponseDTO> items = goodsList.stream().map(goodsAssembler::toResponse).toList();
        return PageResponse.of(items, total, page, size);
    }

@Transactional(readOnly = true)
    public PageResponse<GoodsResponseDTO> myGoods(Long sellerId, int page, int size) {
        userService.getById(sellerId);

        int offset = page * size;
        List<GoodsDO> goodsList = goodsMapper.findBySellerId(sellerId, size, offset);
        long total = goodsMapper.countBySellerId(sellerId);
        attachImages(goodsList);

        List<GoodsResponseDTO> items = goodsList.stream().map(goodsAssembler::toResponse).toList();
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

    private boolean isReviewer(Long userId) {
        return userId != null && appProperties.getImageAudit().getReviewerUserIds().contains(userId);
    }

    private static final String PLACEHOLDER_IMAGE_KEY = "images/2026/04/auditing.webp";

    private void replaceImages(Long goodsId, List<String> imageUrls) {
        List<GoodsImageDO> oldImages = goodsMapper.findImagesByGoodsId(goodsId);

        Set<String> newKeys = new LinkedHashSet<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                String key = uploadService.extractObjectKey(url.trim());
                if (StringUtils.hasText(key) && !PLACEHOLDER_IMAGE_KEY.equals(key)) {
                    newKeys.add(key);
                }
            }
        }

        Map<String, GoodsImageDO> oldKeyMap = new HashMap<>();
        for (GoodsImageDO image : oldImages) {
            oldKeyMap.put(image.getImageUrl(), image);
        }

        for (GoodsImageDO oldImage : oldImages) {
            if (!newKeys.contains(oldImage.getImageUrl())) {
                uploadService.deleteObject(oldImage.getImageUrl());
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
                image.setSortOrder(idx++);
                image.setAuditStatus(ImageAuditStatusEnum.PENDING);
                imagesToInsert.add(image);
            } else {
                goodsMapper.updateImageSortOrder(existing.getId(), idx++);
            }
        }

        if (!imagesToInsert.isEmpty()) {
            goodsMapper.batchInsertImages(goodsId, imagesToInsert);
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
}
