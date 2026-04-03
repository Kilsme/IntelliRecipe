package com.IntelliRecipe.Kilsme.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class User {
    /** 用户ID（主键，自增） */
    private Long id;
    /** 用户名（唯一） */
    private String username;
    /** 手机号 */
    private String phone;
    /** 加密后的密码哈希 */
    private String passwordHash;
    /** 身高（cm） */
    private Integer heightCm;
    /** 年龄 */
    private Integer age;
    /** 体重（kg） */
    private BigDecimal weightKg;
    /** 性别 */
    private Integer gender;
    /** 用户偏好（JSON字符串） */
    private String preferences;
    /** 过敏原（JSON字符串） */
    private String allergies;
    /** 所在地区 */
    private String region;
    /** 饮食类型：NORMAL / LOSE_WEIGHT / MUSCLE_GAIN / VEGAN */
    private String dietType;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
