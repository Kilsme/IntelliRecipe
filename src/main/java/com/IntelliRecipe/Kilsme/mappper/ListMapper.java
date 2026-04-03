package com.IntelliRecipe.Kilsme.mappper;

import com.IntelliRecipe.Kilsme.model.ShoppingList;
import com.IntelliRecipe.Kilsme.model.ShoppingListItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ListMapper {
    @Select("select * from shopping_lists where user_id=#{userId}")
    ShoppingList selectShopListByUserId(Long userId);
     @Insert("insert into shopping_lists (id,user_id,created_at)"
     +"values (default,#{userId},now())")
    int insertShopList(Long userId);
   @Insert("insert into shopping_list_items (id,list_id,ingredient_id,category_id," +
           "quantity_needed,is_deleted,created_at,unit)" +
           "values (default,#{ListId},#{ingredientId},#{categoryId},#{quantity},0,now(),#{unit})")
    int insertFoodToShopList(@Param("unit") String unit,@Param("quantity") double quantity,@Param("ingredientId") Long ingredientId,@Param("ListId") Long ListId,@Param("categoryId")  int categoryId);
    @Select("select * from shopping_list_items where list_id=#{listId} and is_deleted=0")
    List<ShoppingListItem> selectItemListByListId(@Param("listId") Long id);

    @Select("select * from shopping_list_items where id=#{itemId} and list_id=#{listId} and is_deleted=0")
    ShoppingListItem selectItemByIdAndListId(@Param("itemId") Long itemId, @Param("listId") Long listId);

    @Update("update shopping_list_items set is_deleted=1 where id=#{itemId} and list_id=#{listId} and is_deleted=0")
    int logicalDeleteItem(@Param("itemId") Long itemId, @Param("listId") Long listId);

    @Delete("delete from shopping_list_items where is_deleted=1")
    int physicalDeleteLogicalItems();
}
