package com.IntelliRecipe.Kilsme.Util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码加密工具类
 */
@Component // 如果是 Spring 项目，注入使用；如果不是，去掉注解并手动 new 即可
public class PasswordUtil {

    // BCrypt 强度因子 (4-31)，默认 10。数值越大越安全但越慢。
    // 10 是一个平衡点，生成一个哈希约需 60-100ms。
    private static final int STRENGTH = 10;

    private final PasswordEncoder passwordEncoder;

    public PasswordUtil() {
        this.passwordEncoder = new BCryptPasswordEncoder(STRENGTH);
    }

    /**
     * 1. 加密过程 (注册/修改密码时使用)
     * @param rawPassword 明文密码
     * @return 加密后的哈希字符串 (以 $2a$ 开头)
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 2. 验证过程 (登录时使用)
     * @param rawPassword 用户输入的明文密码
     * @param encodedPassword 数据库中存储的哈希密码
     * @return 匹配成功返回 true，否则 false
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}