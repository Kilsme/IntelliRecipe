package com.IntelliRecipe.Kilsme.vo;

import lombok.Data;

import java.util.List;

@Data
public class RecipeUseResultVo {
    private boolean canUse;
    private String detailUrl;
    private String message;
    private List<RecipeIngredientVo> missingIngredients;
    private List<IngredientReplacementVo> replacements;
}

