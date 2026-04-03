package com.IntelliRecipe.Kilsme.service;

import com.IntelliRecipe.Kilsme.mappper.ListMapper;
import com.IntelliRecipe.Kilsme.model.ShoppingList;
import com.IntelliRecipe.Kilsme.model.ShoppingListItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListService {
    @Autowired
    private ListMapper listMapper;
    public ShoppingList selectByUserId(Long userId){
        return   listMapper.selectShopListByUserId(userId);
    }

    public int insertShopList(Long userId) {
      return  listMapper.insertShopList(userId);
    }

    public int addFoodToList(String unit, double quantity, Long ingredientId,Long ListId,int categoryId) {
        return listMapper.insertFoodToShopList(unit,quantity,ingredientId,ListId,categoryId);
    }

    public List<ShoppingListItem> selectItemByListId(Long id) {
        return listMapper.selectItemListByListId(id);
    }

    public ShoppingListItem selectItemByIdAndListId(Long itemId, Long listId) {
        return listMapper.selectItemByIdAndListId(itemId, listId);
    }

    public int logicalDeleteItem(Long itemId, Long listId) {
        return listMapper.logicalDeleteItem(itemId, listId);
    }

    public int cleanLogicalDeletedItems() {
        return listMapper.physicalDeleteLogicalItems();
    }
}
