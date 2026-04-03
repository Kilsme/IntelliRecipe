package com.IntelliRecipe.Kilsme.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IngredientDict {
    /** 食材ID（主键，自增） */
    private Long id;
    /** 食材标准名称 */
    private String name;
    /** 关联食品分类ID */
    private Integer categoryId;
    /** 营养信息（JSON字符串） */
    private String nutritionInfo;
    /** 时令标签（JSON字符串） */
    private String seasonTags;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
