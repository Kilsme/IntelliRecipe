package com.IntelliRecipe.Kilsme.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserCollection {
    /** 记录ID（主键，自增） */
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 菜谱ID */
    private Long recipeId;
    /** 行为类型：0历史，1做过，2收藏 */
    private Integer actionType;
    /** 评分（如 1-5） */
    private Integer rating;
    /** 评论内容 */
    private String reviewText;
    /** 实际烹饪时间 */
    private LocalDateTime cookedAt;
    /** 创建时间 */
    private LocalDateTime createdAt;
}

