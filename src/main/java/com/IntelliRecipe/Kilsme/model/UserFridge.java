package com.IntelliRecipe.Kilsme.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserFridge {
    /** 用户ID（联合主键） */
    private Long userId;
    /** 食材ID（联合主键） */
    private Long ingredientId;
    /** 库存数量 */
    private double quantity;
    /** 单位（默认 g） */
    private String unit;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}

