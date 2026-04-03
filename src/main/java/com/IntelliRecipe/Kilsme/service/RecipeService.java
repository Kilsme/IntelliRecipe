package com.IntelliRecipe.Kilsme.service;

import com.IntelliRecipe.Kilsme.mappper.RecipeMapper;
import com.IntelliRecipe.Kilsme.model.Recipes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecipeService {

    @Autowired
    private RecipeMapper recipeMapper;

    public int create(Recipes recipe) {
        return recipeMapper.insert(recipe);
    }

    public Recipes getById(Long id) {
        return recipeMapper.selectById(id);
    }

    public int updateVideoTutorials(Long id, String videoTutorials) {
        return recipeMapper.updateVideoTutorialsById(id, videoTutorials);
    }
}

