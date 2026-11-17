package com.focus.service.impl;

import com.focus.common.AuthContext;
import com.focus.common.BizException;
import com.focus.dto.SessionCreateReq;
import com.focus.dto.SessionUpdateReq;
import com.focus.entity.FocusSession;
import com.focus.entity.Habit;
import com.focus.entity.TaskItem;
import com.focus.mapper.SessionMapper;
import com.focus.service.AuthService;
import com.focus.service.BasicDataService;
import com.focus.service.SessionService;
import com.focus.vo.DailyVO;
import com.focus.vo.SessionVO;
import com.focus.vo.SummaryVO;
import com.focus.vo.TaskStatVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SessionServiceImpl implements SessionService {
    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 500;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final SessionMapper sessionMapper;
    private final BasicDataService basicDataService;
    private final AuthService authService;

    public SessionServiceImpl(SessionMapper sessionMapper, BasicDataService basicDataService, AuthService authService) {
        this.sessionMapper = sessionMapper;
        this.basicDataService = basicDataService;
        this.authService = authService;
    }

    @Override
    public SessionVO create(SessionCreateReq req) {
        Long userId = AuthContext.userId();
        int pausedSec = req.getPausedSec() == null ? 0 : req.getPausedSec();
        long durationSec = validateTimes(req.getStartedAt(), req.getEndedAt(), req.getDurationSec(), pausedSec);
        if ("countdown".equals(req.getMode()) && req.getPlannedSec() == null) {
            throw BizException.badRequest("倒计时必须传 plannedSec");
        }
        FocusSession existing = sessionMapper.selectByClientId(userId, req.getClientId());
        if (existing != null) return toVO(existing);

        FocusSession session = new FocusSession();
        session.setUserId(userId);
        session.setClientId(req.getClientId());
        session.setTaskTitle(resolveTitle(req.getTaskId(), req.getHabitId(), req.getTaskTitle(), null));
        session.setTaskId(req.getTaskId());
        session.setHabitId(req.getHabitId());
        session.setNote(req.getNote() == null ? "" : req.getNote().trim());
        session.setMode(req.getMode());
        session.setPlannedSec("countdown".equals(req.getMode()) ? req.getPlannedSec() : null);
        session.setStartedAt(utc(req.getStartedAt()));
        session.setEndedAt(utc(req.getEndedAt()));
        session.setDurationSec((int) durationSec);
        session.setPausedSec(pausedSec);
        session.setStatus(req.getStatus());
        session.setSource(req.getSource() == null ? "timer" : req.getSource());
        session.setSessionDate(req.getStartedAt().atZone(authService.zoneOf(userId)).toLocalDate());
        session.setVersion(1);
        try {
            sessionMapper.insert(session);
        } catch (DuplicateKeyException e) {
            FocusSession duplicated = sessionMapper.selectByClientId(userId, req.getClientId());
            if (duplicated != null) return toVO(duplicated);
            throw e;
        }
        return toVO(session);
    }

    @Override
    public SessionVO update(Long id, SessionUpdateReq req) {
        Long userId = AuthContext.userId();
        FocusSession current = sessionMapper.selectById(userId, id);
        if (current == null) throw BizException.notFound("记录不存在");
        int pausedSec = req.getPausedSec() == null ? 0 : req.getPausedSec();
        long durationSec = validateTimes(req.getStartedAt(), req.getEndedAt(), req.getDurationSec(), pausedSec);
        current.setTaskTitle(resolveTitle(req.getTaskId(), req.getHabitId(), null, current.getTaskTitle()));
        current.setTaskId(req.getTaskId());
        current.setHabitId(req.getHabitId());
        current.setNote(req.getNote() == null ? "" : req.getNote().trim());
        current.setStartedAt(utc(req.getStartedAt()));
        current.setEndedAt(utc(req.getEndedAt()));
        current.setDurationSec((int) durationSec);
        current.setPausedSec(pausedSec);
        current.setSessionDate(req.getStartedAt().atZone(authService.zoneOf(userId)).toLocalDate());
        current.setVersion(req.getVersion());
        if (sessionMapper.update(current) == 0) throw BizException.conflict("记录已被修改，请刷新后重试");
        return toVO(sessionMapper.selectById(userId, id));
    }

    @Override
    public SessionVO get(Long id) {
        FocusSession session = sessionMapper.selectById(AuthContext.userId(), id);
        if (session == null) throw BizException.notFound("记录不存在");
        return toVO(session);
    }

    @Override
    public List<SessionVO> list(LocalDate from, LocalDate to, Integer limit) {
        ZoneId zone = authService.zoneOf(AuthContext.userId());
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_RANGE_DAYS - 1) : from;
        LocalDate[] range = range(start, end);
        int size = limit == null ? DEFAULT_LIST_LIMIT : Math.max(1, Math.min(MAX_LIST_LIMIT, limit));
        List<FocusSession> rows = sessionMapper.selectList(AuthContext.userId(), range[0], range[1], size);
        List<SessionVO> result = new ArrayList<>(rows.size());
        for (FocusSession row : rows) result.add(toVO(row));
        return result;
    }

    @Override
    public List<String> taskTitles(Integer limit) {
        int size = limit == null ? 10 : Math.max(1, Math.min(50, limit));
        return sessionMapper.selectTaskTitles(AuthContext.userId(), size);
    }

    @Override
    public void delete(Long id) {
        if (sessionMapper.softDelete(AuthContext.userId(), id) == 0) throw BizException.notFound("记录不存在");
    }

    @Override
    public SummaryVO summary(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(authService.zoneOf(AuthContext.userId()));
        // 两个参数都不传表示统计全部数据
        SummaryVO vo = from == null && to == null
                ? sessionMapper.summary(AuthContext.userId(), null, null)
                : summaryOfRange(from == null ? today : from, to == null ? today : to);
        if (vo == null) {
            vo = new SummaryVO(); vo.setTotalSec(0L); vo.setPomodoroCount(0L);
            vo.setAbortedCount(0L); vo.setActiveDays(0L);
        }
        return vo;
    }

    private SummaryVO summaryOfRange(LocalDate start, LocalDate end) {
        LocalDate[] range = range(start, end);
        return sessionMapper.summary(AuthContext.userId(), range[0], range[1]);
    }

    @Override
    public List<DailyVO> daily(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(authService.zoneOf(AuthContext.userId()));
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        LocalDate[] range = range(start, end);
        Map<LocalDate, DailyVO> cached = new HashMap<>();
        for (DailyVO vo : sessionMapper.daily(AuthContext.userId(), range[0], range[1])) cached.put(vo.getDate(), vo);
        List<DailyVO> result = new ArrayList<>();
        for (LocalDate day = range[0]; !day.isAfter(range[1]); day = day.plusDays(1)) {
            DailyVO hit = cached.get(day);
            result.add(hit != null ? hit : new DailyVO(day, 0L, 0L));
        }
        return result;
    }

    @Override
    public List<DailyVO> yearly(Integer year) {
        int target = year == null ? LocalDate.now(authService.zoneOf(AuthContext.userId())).getYear() : year;
        if (target < 1900 || target > 2100) throw BizException.badRequest("年份超出范围");
        Map<LocalDate, DailyVO> cached = new HashMap<>();
        for (DailyVO vo : sessionMapper.monthly(AuthContext.userId(), target)) cached.put(vo.getDate(), vo);
        List<DailyVO> result = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            LocalDate day = LocalDate.of(target, month, 1);
            DailyVO hit = cached.get(day);
            result.add(hit == null ? new DailyVO(day, 0L, 0L) : hit);
        }
        return result;
    }

    @Override
    public List<TaskStatVO> byTask(LocalDate from, LocalDate to, Integer limit) {
        LocalDate today = LocalDate.now(authService.zoneOf(AuthContext.userId()));
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        LocalDate[] range = range(start, end);
        int size = limit == null ? 10 : Math.max(1, Math.min(20, limit));
        return sessionMapper.selectByTask(AuthContext.userId(), range[0], range[1], size);
    }

    private long validateTimes(Instant startedAt, Instant endedAt, Integer durationSec, int pausedSec) {
        if (startedAt == null || endedAt == null || !startedAt.isBefore(endedAt)) {
            throw BizException.badRequest("startedAt 必须早于 endedAt");
        }
        long spanSec = Duration.between(startedAt, endedAt).getSeconds();
        if (pausedSec < 0 || pausedSec > spanSec) throw BizException.badRequest("暂停时长不能超过总时间跨度");
        long expected = spanSec - pausedSec;
        if (expected < 1 || expected > 43200) throw BizException.badRequest("有效专注时长需在 1—43200 秒之间");
        if (durationSec == null || Math.abs(durationSec - expected) > 5) {
            throw BizException.badRequest("durationSec 与起止时间不一致");
        }
        return expected;
    }

    private String resolveTitle(Long taskId, Long habitId, String requestedTitle, String fallback) {
        if (taskId != null && habitId != null) throw BizException.badRequest("任务和习惯最多关联一个");
        if (taskId != null) {
            TaskItem task = basicDataService.requireTask(taskId);
            return task.getTitle();
        }
        if (habitId != null) {
            Habit habit = basicDataService.requireHabit(habitId);
            return habit.getName();
        }
        String title = requestedTitle == null ? fallback : requestedTitle;
        return title == null ? "" : title.trim();
    }

    private LocalDate[] range(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) { LocalDate tmp = start; start = end; end = tmp; }
        if (Duration.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays() > 366) {
            throw BizException.badRequest("统计范围不能超过 366 天");
        }
        return new LocalDate[]{start, end};
    }

    private LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }

    private SessionVO toVO(FocusSession entity) {
        SessionVO vo = new SessionVO();
        vo.setId(entity.getId()); vo.setClientId(entity.getClientId());
        vo.setTaskId(entity.getTaskId()); vo.setHabitId(entity.getHabitId());
        vo.setTaskTitle(entity.getTaskTitle()); vo.setNote(entity.getNote()); vo.setMode(entity.getMode());
        vo.setPlannedSec(entity.getPlannedSec());
        vo.setStartedAt(entity.getStartedAt().toInstant(ZoneOffset.UTC));
        vo.setEndedAt(entity.getEndedAt().toInstant(ZoneOffset.UTC));
        vo.setDurationSec(entity.getDurationSec()); vo.setPausedSec(entity.getPausedSec());
        vo.setStatus(entity.getStatus()); vo.setSource(entity.getSource()); vo.setSessionDate(entity.getSessionDate());
        vo.setVersion(entity.getVersion() == null ? 1 : entity.getVersion());
        return vo;
    }
}
