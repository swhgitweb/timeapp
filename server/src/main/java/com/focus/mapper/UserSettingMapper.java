package com.focus.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserSettingMapper {
    @Select("SELECT settings_json FROM user_setting WHERE user_id = #{userId}")
    String findJson(@Param("userId") Long userId);

    @Insert("INSERT INTO user_setting (user_id, settings_json) VALUES (#{userId}, CAST(#{json} AS JSON)) " +
            "ON DUPLICATE KEY UPDATE settings_json = VALUES(settings_json)")
    int upsert(@Param("userId") Long userId, @Param("json") String json);
}
