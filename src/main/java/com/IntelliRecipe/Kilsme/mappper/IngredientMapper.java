package com.IntelliRecipe.Kilsme.mappper;

import com.IntelliRecipe.Kilsme.model.IngredientDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IngredientMapper {
    @Select("select * from ingredients_dict where id = #{id}")
    IngredientDict selectByIngredientId(Long id);
    @Select("select * from ingredients_dict where category_id = #{categoryId}")
    List<IngredientDict> selectByCategoryId(Long categoryId);

    @Select("select * from ingredients_dict")
    List<IngredientDict> selectAll();
}
