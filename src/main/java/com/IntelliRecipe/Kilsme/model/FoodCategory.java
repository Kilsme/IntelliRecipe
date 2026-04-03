package com.IntelliRecipe.Kilsme.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FoodCategory {
    /** 分类ID（主键，自增） */
    private Integer id;
    /** 分类名称（如：蔬菜菌菇、肉禽蛋类） */
    private String name;
    /** 排序权重，数字越小越靠前 */
    private Integer sortOrder;
    /** 前端图标标识（如：veg、meat、fruit） */
    private String iconCode;
    /** 分类描述 */
    private String description;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
