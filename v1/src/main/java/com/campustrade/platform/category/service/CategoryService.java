package com.campustrade.platform.category.service;

import com.campustrade.platform.category.assembler.CategoryAssembler;
import com.campustrade.platform.category.dataobject.CategoryDO;
import com.campustrade.platform.category.dto.response.CategoryResponseDTO;
import com.campustrade.platform.category.mapper.CategoryMapper;
import com.campustrade.platform.common.AppException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final CategoryAssembler categoryAssembler;

    public CategoryService(CategoryMapper categoryMapper, CategoryAssembler categoryAssembler) {
        this.categoryMapper = categoryMapper;
        this.categoryAssembler = categoryAssembler;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "category:list", key = "'all'")
    public List<CategoryResponseDTO> listEnabledCategories() {
        return categoryMapper.findEnabledTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(categoryAssembler::toResponse)
                .toList();
    }

@CacheEvict(cacheNames = "category:list", allEntries = true)
    public void evictCategoryListCache() {
    }

    @Transactional(readOnly = true)
    public CategoryDO getById(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        CategoryDO category = categoryMapper.findById(categoryId);
        if (category == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "商品分类不存在");
        }
        return category;
    }
}
