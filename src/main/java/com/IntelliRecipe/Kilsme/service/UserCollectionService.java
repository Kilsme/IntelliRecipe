package com.IntelliRecipe.Kilsme.service;

import com.IntelliRecipe.Kilsme.mappper.UserCollectionMapper;
import com.IntelliRecipe.Kilsme.model.UserCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserCollectionService {

    @Autowired
    private UserCollectionMapper userCollectionMapper;

    public int add(UserCollection userCollection) {
        return userCollectionMapper.insert(userCollection);
    }

    public List<UserCollection> getRecentByUserId(Long userId, int limit) {
        return userCollectionMapper.selectRecentByUserId(userId, limit);
    }

    public List<UserCollection> getByUserIdAndActionType(Long userId, Integer actionType) {
        return userCollectionMapper.selectByUserIdAndActionType(userId, actionType);
    }

    public int deleteCollected(Long userId, Long recipeId) {
        return userCollectionMapper.deleteCollectedByUserIdAndRecipeId(userId, recipeId);
    }

    public int batchDeleteCollected(Long userId, List<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return 0;
        }
        return userCollectionMapper.batchDeleteCollectedByUserId(userId, recipeIds);
    }
}

