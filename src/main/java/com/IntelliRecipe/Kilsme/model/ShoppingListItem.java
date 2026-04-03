package com.IntelliRecipe.Kilsme.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShoppingListItem {
    /** 明细ID（主键，自增） */
    private Long id;
    /** 购物清单ID */
    private Long listId;
    /** 食材ID */
    private Long ingredientId;
    /** 冗余分类ID（用于快速排序） */
    private Integer categoryId;
    /** 需求数量 */
    private Double quantityNeeded;
    /** 用户备注 */
    private String userNote;
    /** 是否逻辑删除：true已删除，false未删除 */
    private Boolean isDeleted;
    /** 创建时间 */
    private LocalDateTime createdAt;

    //单位
    private String unit;

}

