package com.focus.mapper;

import com.focus.entity.CalendarEvent;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CalendarMapper {
    CalendarEvent selectById(@Param("userId") Long userId, @Param("id") Long id);
    List<CalendarEvent> selectRange(@Param("userId") Long userId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("kinds") List<String> kinds,
                                    @Param("listIds") List<Long> listIds);
    List<CalendarEvent> selectExport(@Param("userId") Long userId);
    int insert(CalendarEvent event);
    int update(CalendarEvent event);
    int softDelete(@Param("userId") Long userId, @Param("id") Long id, @Param("version") Integer version);
}
