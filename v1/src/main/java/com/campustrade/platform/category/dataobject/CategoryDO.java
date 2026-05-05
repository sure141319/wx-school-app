package com.campustrade.platform.category.dataobject;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryDO {

    private Long id;
    private String name;
    private Integer sortOrder = 0;
    private Boolean enabled = true;
}
