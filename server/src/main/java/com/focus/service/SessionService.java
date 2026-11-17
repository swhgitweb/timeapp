package com.focus.service;

import com.focus.dto.SessionCreateReq;
import com.focus.dto.SessionUpdateReq;
import com.focus.vo.DailyVO;
import com.focus.vo.SessionVO;
import com.focus.vo.SummaryVO;
import com.focus.vo.TaskStatVO;

import java.time.LocalDate;
import java.util.List;

public interface SessionService {

    SessionVO create(SessionCreateReq req);

    SessionVO update(Long id, SessionUpdateReq req);

    SessionVO get(Long id);

    List<SessionVO> list(LocalDate from, LocalDate to, Integer limit);

    List<String> taskTitles(Integer limit);

    void delete(Long id);

    SummaryVO summary(LocalDate from, LocalDate to);

    List<DailyVO> daily(LocalDate from, LocalDate to);

    List<DailyVO> yearly(Integer year);

    List<TaskStatVO> byTask(LocalDate from, LocalDate to, Integer limit);
}
