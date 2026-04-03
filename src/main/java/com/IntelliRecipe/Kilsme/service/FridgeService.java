package com.IntelliRecipe.Kilsme.service;

import com.IntelliRecipe.Kilsme.mappper.FridgeMapper;
import com.IntelliRecipe.Kilsme.model.UserFridge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FridgeService {
    @Autowired
    private FridgeMapper fridgeMapper;

    //根据用户id查询冰箱信息
    public List<UserFridge> getFridgeByUserId(Long userId) {
        return fridgeMapper.selectByUserId(userId);
    }

    public void updateFridge(UserFridge temp) {
        //进行修改
        fridgeMapper.updateFridge(temp);
    }

    public void addFridge(UserFridge userFridge) {
        //进行添加
        fridgeMapper.addFridge(userFridge);
    }

    public int deleteFridgeFood(Long userId, Long ingredientId) {
        return fridgeMapper.deleteByUserIdAndIngredientId(userId, ingredientId);
    }
}
