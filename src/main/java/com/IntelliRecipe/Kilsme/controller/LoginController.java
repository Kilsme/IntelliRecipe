package com.IntelliRecipe.Kilsme.controller;

import com.IntelliRecipe.Kilsme.dto.Result;
import com.IntelliRecipe.Kilsme.model.User;
import com.IntelliRecipe.Kilsme.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class LoginController {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, String> payload, HttpSession httpSession) {
        String username = payload.get("username");
        String password = payload.get("password");
        if (isBlank(username) || isBlank(password)) {
            return Result.fail("用户名和密码不能为空");
        }
        User user = userService.authenticate(username, password);
        if (user == null) {
            return Result.fail("用户名或者密码错误");
        }
        httpSession.setAttribute("user", user);
        return Result.ok(stripSensitive(user));
    }

    @PostMapping("/register")
    public Result register(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String phone = payload.get("phone");
        String password = payload.get("password");
        if (isBlank(username) || isBlank(password)) {
            return Result.fail("用户名和密码不能为空");
        }
        try {
            userService.register(username, phone, password);
            return Result.ok("注册成功");
        } catch (IllegalArgumentException ex) {
            return Result.fail(ex.getMessage());
        }
    }

    @GetMapping("/session")
    public Result currentUser(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("未登录");
        }
        return Result.ok(stripSensitive(user));
    }

    @PostMapping("/logout")
    public Result logout(HttpSession session) {
        session.invalidate();
        return Result.ok("已退出登录");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private User stripSensitive(User user) {
        if (user != null) {
            user.setPasswordHash(null);
        }
        return user;
    }
}
