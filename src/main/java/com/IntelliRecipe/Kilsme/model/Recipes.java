package com.IntelliRecipe.Kilsme.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Recipes {
    /** 菜谱ID（主键，自增） */
    private Long id;
    /** 菜谱标题 */
    private String title;
    /** 菜谱简介 */
    private String description;
    /** 发布用户ID */
    private Long userId;
    /** 风味标签（JSON字符串） */
    private String flavorTags;
    /** 菜系标签（JSON字符串） */
    private String cuisineTags;
    /** 主风味（由 flavorTags 计算） */
    private String primaryFlavor;
    /** 主菜系（由 cuisineTags 计算） */
    private String primaryCuisine;
    /** 预计耗时（分钟） */
    private Integer timeMinutes;
    /** 难度：0简单，1中等，2困难 */
    private Integer difficulty;
    /** 所需食材（JSON字符串） */
    private String ingredientsRequired;
    /** 制作步骤（JSON字符串） */
    private String steps;
    /** 营养摘要（JSON字符串） */
    private String nutritionSummary;
    /** 视频教程（JSON字符串） */
    private String videoTutorials;
    /** 是否公开：true公开，false私有 */
    private Boolean isPublic;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
