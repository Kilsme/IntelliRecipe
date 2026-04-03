package com.IntelliRecipe.Kilsme.service;

import com.IntelliRecipe.Kilsme.mappper.IngredientMapper;
import com.IntelliRecipe.Kilsme.model.IngredientDict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IngredientService {
       @Autowired
       private IngredientMapper ingredientMapper;

       //根据id查询食材
       public IngredientDict getIngredientById(Long id){
              return ingredientMapper.selectByIngredientId(id);
       }


       //根据中类id进行查询食材
       public List<IngredientDict> getIngredientsByCategoryId(long categoryId){
              return ingredientMapper.selectByCategoryId(categoryId);
       }

       public List<IngredientDict> getAllIngredients(){
              return ingredientMapper.selectAll();
       }
}
