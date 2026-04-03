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

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/info")
    public Result showUser(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(stripSensitive(user));
    }

    @PostMapping("/updateUser")
    public Result updateUser(@RequestBody User requestUser, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            return Result.fail("请先登录");
        }
        User updateUser = new User();
        updateUser.setId(sessionUser.getId());
        updateUser.setHeightCm(requestUser.getHeightCm());
        updateUser.setAge(requestUser.getAge());
        updateUser.setWeightKg(requestUser.getWeightKg());
        updateUser.setGender(requestUser.getGender());
        updateUser.setPreferences(requestUser.getPreferences());
        updateUser.setAllergies(requestUser.getAllergies());
        updateUser.setRegion(requestUser.getRegion());
        updateUser.setDietType(requestUser.getDietType());
        userService.update(updateUser);
        // 同步 session 中可展示字段（用户名和手机号保持只读）
        sessionUser.setHeightCm(updateUser.getHeightCm());
        sessionUser.setAge(updateUser.getAge());
        sessionUser.setWeightKg(updateUser.getWeightKg());
        sessionUser.setGender(updateUser.getGender());
        sessionUser.setPreferences(updateUser.getPreferences());
        sessionUser.setAllergies(updateUser.getAllergies());
        sessionUser.setRegion(updateUser.getRegion());
        sessionUser.setDietType(updateUser.getDietType());
        session.setAttribute("user", sessionUser);

        return Result.ok(stripSensitive(sessionUser));
    }

    private User stripSensitive(User user) {
        if (user != null) {
            user.setPasswordHash(null);
        }
        return user;
    }

}
