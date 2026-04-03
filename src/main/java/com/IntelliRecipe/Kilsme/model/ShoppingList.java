package com.IntelliRecipe.Kilsme.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShoppingList {
    /** 清单ID（主键，自增） */
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 创建时间 */
    private LocalDateTime createdAt;
}

