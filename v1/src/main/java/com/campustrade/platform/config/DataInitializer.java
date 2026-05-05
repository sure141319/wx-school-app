package com.campustrade.platform.config;

import com.campustrade.platform.category.dataobject.CategoryDO;
import com.campustrade.platform.category.mapper.CategoryMapper;
import com.campustrade.platform.category.service.CategoryService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initCategories(CategoryMapper categoryMapper, CategoryService categoryService) {
        return args -> {
            if (categoryMapper.countAll() > 0) {
                return;
            }

            List<String> defaults = List.of("二手书", "日常用品", "学习用品", "数码产品", "其他");
            int idx = 1;
            for (String name : defaults) {
                CategoryDO category = new CategoryDO();
                category.setName(name);
                category.setSortOrder(idx++);
                category.setEnabled(true);
                categoryMapper.insert(category);
            }

            categoryService.evictCategoryListCache();
        };
    }
}
