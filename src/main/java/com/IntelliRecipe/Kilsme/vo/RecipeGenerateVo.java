package com.IntelliRecipe.Kilsme.vo;

import lombok.Data;

import java.util.List;

@Data
public class RecipeGenerateVo {
    private Long recipeId;
    private String title;
    private String description;
    private String imageUrl;
    private String timeNode;
    private String douyinUrl;
    private List<String> flavorTags;
    private Boolean canUseNow;
    private List<RecipeIngredientVo> ingredients;
    private List<RecipeIngredientVo> missingIngredients;
    private List<IngredientReplacementVo> replacements;
    private List<String> steps;
}
