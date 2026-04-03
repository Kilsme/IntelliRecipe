package com.IntelliRecipe.Kilsme.service;

import com.IntelliRecipe.Kilsme.Util.PasswordUtil;
import com.IntelliRecipe.Kilsme.mappper.UserMapper;
import com.IntelliRecipe.Kilsme.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordUtil passwordUtil;

    //用过姓名进行查询
    public User selectByUsername(String username){
        return userMapper.selectByUsername(username);
    }
    //通过电话号码进行查询
    public User selectByPhone(String phone){
        return userMapper.selectByPhone(phone);
    }
    //注册用户
    public int insertUser(User user){
        return userMapper.insert(user);
    }

    public User authenticate(String username, String rawPassword) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return null;
        }
        return passwordUtil.matches(rawPassword, user.getPasswordHash()) ? user : null;
    }

    public User register(String username, String phone, String rawPassword) {
        if (selectByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已经存在");
        }
        if (phone != null && !phone.isBlank() && selectByPhone(phone) != null) {
            throw new IllegalArgumentException("电话号码已经存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPasswordHash(passwordUtil.encode(rawPassword));
        userMapper.insert(user);
        return user;
    }
    //更新个人信息
    public void update(User user) {
        userMapper.update(user);
    }
}
