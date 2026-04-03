package com.IntelliRecipe.Kilsme.vo;

import lombok.Data;

@Data
public class CollectedRecipeVo {
    private Long recipeId;
    private String title;
    private String description;
    private String collectedAt;
}

