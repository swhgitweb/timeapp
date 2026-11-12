package com.focus.service;

import com.focus.dto.SessionCreateReq;
import com.focus.vo.DailyVO;
import com.focus.vo.SessionVO;
import com.focus.vo.SummaryVO;

import java.time.LocalDate;
import java.util.List;

public interface SessionService {

    SessionVO create(SessionCreateReq req);

    List<SessionVO> list(LocalDate from, LocalDate to, Integer limit);

    List<String> taskTitles(Integer limit);

    void delete(Long id);

    SummaryVO summary(LocalDate from, LocalDate to);

    List<DailyVO> daily(LocalDate from, LocalDate to);
}
