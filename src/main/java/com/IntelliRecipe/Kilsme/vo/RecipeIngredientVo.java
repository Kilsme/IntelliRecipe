package com.IntelliRecipe.Kilsme.vo;

import lombok.Data;

@Data
public class RecipeIngredientVo {
    private Long ingredientId;
    private String ingredientName;
    private Integer categoryId;
    private Double quantity;
    private String unit;
}

