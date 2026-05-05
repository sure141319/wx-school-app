package com.campustrade.platform.category.assembler;

import com.campustrade.platform.category.dataobject.CategoryDO;
import com.campustrade.platform.category.dto.response.CategoryResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class CategoryAssembler {

    public CategoryResponseDTO toResponse(CategoryDO category) {
        return new CategoryResponseDTO(category.getId(), category.getName(), category.getSortOrder());
    }
}

