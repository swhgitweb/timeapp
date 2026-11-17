package com.focus.mapper;

import com.focus.entity.AppUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper {
    @Select("SELECT COUNT(*) FROM app_user")
    long count();

    @Select("SELECT * FROM app_user WHERE id = #{id}")
    AppUser selectById(Long id);

    @Select("SELECT * FROM app_user WHERE username = #{username}")
    AppUser selectByUsername(String username);

    @Select("SELECT * FROM app_user WHERE ics_token = #{token}")
    AppUser selectByIcsToken(String token);

    @Insert("INSERT INTO app_user(username, password_hash, display_name, timezone) VALUES(#{username}, #{passwordHash}, #{displayName}, #{timezone})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AppUser user);

    @Update("UPDATE focus_session SET user_id = #{userId} WHERE user_id IS NULL")
    int claimLegacySessions(Long userId);

    @Update("UPDATE app_user SET timezone = #{timezone} WHERE id = #{userId}")
    int updateTimezone(@Param("userId") Long userId, @Param("timezone") String timezone);

    @Update("UPDATE app_user SET ics_token = #{token} WHERE id = #{userId}")
    int updateIcsToken(@Param("userId") Long userId, @Param("token") String token);
}
