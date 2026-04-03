package com.IntelliRecipe.Kilsme.mappper;

import com.IntelliRecipe.Kilsme.model.UserFridge;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FridgeMapper {
    //根据userId进行查询
    @Select("select * from user_fridge where user_id = #{userId} AND quantity > 0")
    List<UserFridge> selectByUserId(Long userId);
     //根据userId 和ingredient_id作为查询条件
    @Update("update user_fridge set quantity = #{quantity} ,unit=#{unit} where user_id = #{userId} and ingredient_id = #{ingredientId}")
    void updateFridge(UserFridge temp);
    @Insert("insert into user_fridge(user_id, ingredient_id, quantity, unit, updated_at) values(#{userId}, #{ingredientId}, #{quantity}, #{unit}, now())")
    void addFridge(UserFridge userFridge);

    @Delete("delete from user_fridge where user_id = #{userId} and ingredient_id = #{ingredientId}")
    int deleteByUserIdAndIngredientId(Long userId, Long ingredientId);
}
