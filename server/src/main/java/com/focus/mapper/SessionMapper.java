package com.focus.mapper;

import com.focus.entity.FocusSession;
import com.focus.vo.DailyVO;
import com.focus.vo.SummaryVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface SessionMapper {

    /** 插入，回填自增 id */
    int insert(FocusSession session);

    /** 按幂等键查询（含已软删除，保证重复提交返回同一条） */
    FocusSession selectByClientId(@Param("clientId") String clientId);

    /** 记录列表，按开始时间倒序 */
    List<FocusSession> selectList(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("limit") int limit);

    /** 任务名候选，按最近使用排序 */
    List<String> selectTaskTitles(@Param("limit") int limit);

    /** 软删除，返回受影响行数 */
    int softDelete(@Param("id") Long id);

    SummaryVO summary(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<DailyVO> daily(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
