package com.focus.mapper;

import com.focus.entity.FocusPreset;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface FocusPresetMapper {
    List<FocusPreset> selectList(@Param("userId") Long userId);
    FocusPreset selectById(@Param("userId") Long userId, @Param("id") Long id);
    int insert(FocusPreset preset);
    int update(FocusPreset preset);
    int delete(@Param("userId") Long userId, @Param("id") Long id);
}
