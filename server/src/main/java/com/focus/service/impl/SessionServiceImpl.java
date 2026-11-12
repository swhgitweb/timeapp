package com.focus.service.impl;

import com.focus.common.BizException;
import com.focus.dto.SessionCreateReq;
import com.focus.entity.FocusSession;
import com.focus.mapper.SessionMapper;
import com.focus.service.SessionService;
import com.focus.vo.DailyVO;
import com.focus.vo.SessionVO;
import com.focus.vo.SummaryVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SessionServiceImpl implements SessionService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final SessionMapper sessionMapper;

    public SessionServiceImpl(SessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    /** 创建会话：先校验，再按 clientId 幂等 */
    @Override
    public SessionVO create(SessionCreateReq req) {
        validateCrossField(req);

        FocusSession existing = sessionMapper.selectByClientId(req.getClientId());
        if (existing != null) {
            return toVO(existing);
        }

        FocusSession session = new FocusSession();
        session.setClientId(req.getClientId());
        session.setTaskTitle(req.getTaskTitle() == null ? "" : req.getTaskTitle().trim());
        session.setNote(req.getNote() == null ? "" : req.getNote().trim());
        session.setMode(req.getMode());
        session.setPlannedSec("countdown".equals(req.getMode()) ? req.getPlannedSec() : null);
        session.setStartedAt(req.getStartedAt());
        session.setEndedAt(req.getEndedAt());
        session.setDurationSec(req.getDurationSec());
        session.setPausedSec(req.getPausedSec() == null ? 0 : req.getPausedSec());
        session.setStatus(req.getStatus());
        session.setSessionDate(req.getStartedAt().toLocalDate());

        try {
            sessionMapper.insert(session);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：唯一键兜底，回查后返回已有记录
            FocusSession duplicated = sessionMapper.selectByClientId(req.getClientId());
            if (duplicated != null) {
                return toVO(duplicated);
            }
            throw e;
        }
        return toVO(session);
    }

    @Override
    public List<SessionVO> list(LocalDate from, LocalDate to, Integer limit) {
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_RANGE_DAYS - 1) : from;
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        int size = limit == null ? DEFAULT_LIST_LIMIT : Math.max(1, Math.min(DEFAULT_LIST_LIMIT, limit));

        List<FocusSession> rows = sessionMapper.selectList(start, end, size);
        List<SessionVO> result = new ArrayList<>(rows.size());
        for (FocusSession row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    @Override
    public List<String> taskTitles(Integer limit) {
        int size = limit == null ? 10 : Math.max(1, Math.min(50, limit));
        return sessionMapper.selectTaskTitles(size);
    }

    @Override
    public void delete(Long id) {
        int rows = sessionMapper.softDelete(id);
        if (rows == 0) {
            throw BizException.notFound("记录不存在");
        }
    }

    @Override
    public SummaryVO summary(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end : from;
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        SummaryVO vo = sessionMapper.summary(start, end);
        if (vo == null) {
            vo = new SummaryVO();
            vo.setTotalSec(0L);
            vo.setPomodoroCount(0L);
            vo.setAbortedCount(0L);
            vo.setActiveDays(0L);
        }
        return vo;
    }

    /** 每日趋势：数据库缺失的日期补零，保证区间连续 */
    @Override
    public List<DailyVO> daily(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        Map<LocalDate, DailyVO> cached = new HashMap<>();
        for (DailyVO vo : sessionMapper.daily(start, end)) {
            cached.put(vo.getDate(), vo);
        }

        List<DailyVO> result = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            DailyVO hit = cached.get(day);
            result.add(hit != null ? hit : new DailyVO(day, 0L, 0L));
        }
        return result;
    }

    /** 跨字段校验（文档 6.2） */
    private void validateCrossField(SessionCreateReq req) {
        if ("countdown".equals(req.getMode()) && req.getPlannedSec() == null) {
            throw BizException.badRequest("倒计时必须传 plannedSec");
        }
        if (!req.getStartedAt().isBefore(req.getEndedAt())) {
            throw BizException.badRequest("startedAt 必须早于 endedAt");
        }
        long spanSec = Duration.between(req.getStartedAt(), req.getEndedAt()).getSeconds();
        int pausedSec = req.getPausedSec() == null ? 0 : req.getPausedSec();
        long expected = spanSec - pausedSec;
        if (Math.abs(req.getDurationSec() - expected) > 5) {
            throw BizException.badRequest("durationSec 与起止时间不一致");
        }
    }

    private SessionVO toVO(FocusSession entity) {
        SessionVO vo = new SessionVO();
        vo.setId(entity.getId());
        vo.setClientId(entity.getClientId());
        vo.setTaskTitle(entity.getTaskTitle());
        vo.setNote(entity.getNote());
        vo.setMode(entity.getMode());
        vo.setPlannedSec(entity.getPlannedSec());
        vo.setStartedAt(entity.getStartedAt());
        vo.setEndedAt(entity.getEndedAt());
        vo.setDurationSec(entity.getDurationSec());
        vo.setPausedSec(entity.getPausedSec());
        vo.setStatus(entity.getStatus());
        vo.setSessionDate(entity.getSessionDate());
        return vo;
    }
}
