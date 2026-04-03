package com.IntelliRecipe.Kilsme.mappper;

import com.IntelliRecipe.Kilsme.model.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    @Select("select * from users where username = #{username}")
    User selectByUsername(@Param("username") String username);

    @Select("select * from users where phone = #{phone}")
    User selectByPhone(@Param("phone") String phone);
    @Insert("insert into users(username, phone, password_hash, created_at, updated_at) values(#{username}, #{phone}, #{passwordHash}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("update users set height_cm = #{heightCm}," +
            " age = #{age}, weight_kg = #{weightKg} ,gender=#{gender}" +
            ",preferences=#{preferences},allergies=#{allergies},region=#{region}" +
            ",diet_type=#{dietType}, updated_at = now() where id = #{id}")
    void update(User user);
}
