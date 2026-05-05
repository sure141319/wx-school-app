package com.campustrade.platform.category.controller;

import com.campustrade.platform.category.dto.response.CategoryResponseDTO;
import com.campustrade.platform.category.service.CategoryService;
import com.campustrade.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponseDTO>> list() {
        return ApiResponse.ok(categoryService.listEnabledCategories());
    }
}
