package com.focus.mapper;

import com.focus.entity.FocusSession;
import com.focus.vo.DailyVO;
import com.focus.vo.SummaryVO;
import com.focus.vo.TaskStatVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface SessionMapper {
    int insert(FocusSession session);
    FocusSession selectByClientId(@Param("userId") Long userId, @Param("clientId") String clientId);
    FocusSession selectById(@Param("userId") Long userId, @Param("id") Long id);
    List<FocusSession> selectList(@Param("userId") Long userId, @Param("from") LocalDate from,
                                  @Param("to") LocalDate to, @Param("limit") int limit);
    List<FocusSession> selectRange(@Param("userId") Long userId, @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);
    List<String> selectTaskTitles(@Param("userId") Long userId, @Param("limit") int limit);
    int update(FocusSession session);
    int softDelete(@Param("userId") Long userId, @Param("id") Long id);
    SummaryVO summary(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
    List<DailyVO> daily(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
    List<DailyVO> monthly(@Param("userId") Long userId, @Param("year") int year);
    List<TaskStatVO> selectByTask(@Param("userId") Long userId, @Param("from") LocalDate from,
                                  @Param("to") LocalDate to, @Param("limit") int limit);
}
