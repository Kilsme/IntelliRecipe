package com.IntelliRecipe.Kilsme.vo;

import lombok.Data;

@Data
public class FoodVo {
    private Long userId;
    private String IngredientName;//食材名称
    private Double quantity;
    private String unit;
    private int categoryId;
    private Long IngredientId;
    private String nutrition_info;
}
