package com.IntelliRecipe.Kilsme.mappper;

import com.IntelliRecipe.Kilsme.model.Recipes;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RecipeMapper {

    @Insert("insert into recipes (title, description, user_id, flavor_tags, cuisine_tags, " +
            "time_minutes, difficulty, ingredients_required, steps, nutrition_summary, video_tutorials, is_public, created_at) " +
            "values (#{title}, #{description}, #{userId}, #{flavorTags}, #{cuisineTags}, " +
            "#{timeMinutes}, #{difficulty}, #{ingredientsRequired}, #{steps}, #{nutritionSummary}, #{videoTutorials}, #{isPublic}, now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Recipes recipe);

    @Select("select * from recipes where id = #{id}")
    Recipes selectById(@Param("id") Long id);

    @Update("update recipes set video_tutorials=#{videoTutorials} where id=#{id}")
    int updateVideoTutorialsById(@Param("id") Long id, @Param("videoTutorials") String videoTutorials);
}

