package com.IntelliRecipe.Kilsme;

import com.IntelliRecipe.Kilsme.vo.RecipeGenerateVo;
import com.IntelliRecipe.Kilsme.vo.RecipeIngredientVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class SmartRecipeVoTest {

    @Test
    void shouldSerializeRecipeGenerateVo() throws Exception {
        RecipeIngredientVo ing = new RecipeIngredientVo();
        ing.setIngredientId(1L);
        ing.setIngredientName("西红柿");
        ing.setCategoryId(1);
        ing.setQuantity(120D);
        ing.setUnit("g");

        RecipeGenerateVo vo = new RecipeGenerateVo();
        vo.setRecipeId(100L);
        vo.setTitle("测试菜谱");
        vo.setDescription("测试描述");
        vo.setTimeNode("晚餐");
        vo.setDouyinUrl("https://www.douyin.com/search/测试菜谱");
        vo.setIngredients(List.of(ing));
        vo.setSteps(List.of("步骤1", "步骤2"));

        String json = new ObjectMapper().writeValueAsString(vo);
        Assertions.assertTrue(json.contains("测试菜谱"));
        Assertions.assertTrue(json.contains("西红柿"));
        Assertions.assertTrue(json.contains("steps"));
    }
}

