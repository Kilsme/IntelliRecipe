package com.IntelliRecipe.Kilsme.vo;

import lombok.Data;

@Data
public class IngredientReplacementVo {
    private String originalIngredientName;
    private String replacementIngredientName;
    private String reason;
}

