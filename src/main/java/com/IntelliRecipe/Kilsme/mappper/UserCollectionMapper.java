package com.IntelliRecipe.Kilsme.mappper;

import com.IntelliRecipe.Kilsme.model.UserCollection;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserCollectionMapper {

    @Insert("insert into user_collections (user_id, recipe_id, action_type, rating, review_text, cooked_at, created_at) " +
            "values (#{userId}, #{recipeId}, #{actionType}, #{rating}, #{reviewText}, #{cookedAt}, now())")
    int insert(UserCollection userCollection);

    @Select("select * from user_collections where user_id=#{userId} order by created_at desc limit #{limit}")
    List<UserCollection> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("select * from user_collections where user_id=#{userId} and action_type=#{actionType} order by created_at desc")
    List<UserCollection> selectByUserIdAndActionType(@Param("userId") Long userId, @Param("actionType") Integer actionType);

    @Delete("delete from user_collections where user_id=#{userId} and recipe_id=#{recipeId} and action_type=2")
    int deleteCollectedByUserIdAndRecipeId(@Param("userId") Long userId, @Param("recipeId") Long recipeId);

    @Delete({"<script>",
            "delete from user_collections where user_id=#{userId} and action_type=2 and recipe_id in ",
            "<foreach collection='recipeIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"})
    int batchDeleteCollectedByUserId(@Param("userId") Long userId, @Param("recipeIds") List<Long> recipeIds);
}

