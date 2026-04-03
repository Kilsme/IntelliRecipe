package com.IntelliRecipe.Kilsme.vo;

import lombok.Data;

@Data
public class ListFoodVo {
    private Long listItemId;
    private Long userId;
    private String IngredientName;//食材名称
    private Double quantity;
    private String unit;
    private int categoryId;
    private Long IngredientId;
}
