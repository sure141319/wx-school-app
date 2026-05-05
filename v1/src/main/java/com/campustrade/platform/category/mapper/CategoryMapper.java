package com.campustrade.platform.category.mapper;

import com.campustrade.platform.category.dataobject.CategoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {

    long countAll();

    int insert(CategoryDO category);

    CategoryDO findById(@Param("id") Long id);

    List<CategoryDO> findEnabledTrueOrderBySortOrderAscNameAsc();
}
