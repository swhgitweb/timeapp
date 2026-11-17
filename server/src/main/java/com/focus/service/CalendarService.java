package com.focus.service;

import com.focus.common.AuthContext;
import com.focus.common.BizException;
import com.focus.dto.CalendarBatchReq;
import com.focus.dto.CalendarEventReq;
import com.focus.entity.CalendarEvent;
import com.focus.entity.FocusSession;
import com.focus.entity.Habit;
import com.focus.entity.TaskItem;
import com.focus.mapper.CalendarMapper;
import com.focus.mapper.SessionMapper;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.parameter.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CalendarService {
    private static final Set<String> KINDS = new HashSet<>(Arrays.asList(
            "task", "habit", "anniversary", "course", "ics", "focus"));
    private static final int MAX_EVENT_RESULTS = 2000;
    private static final int MAX_AGGREGATE_RESULTS = 20000;

    private final CalendarMapper calendarMapper;
    private final BasicDataService basicDataService;
    private final SessionMapper sessionMapper;
    private final AuthService authService;

    public CalendarService(CalendarMapper calendarMapper, BasicDataService basicDataService,
                           SessionMapper sessionMapper, AuthService authService) {
        this.calendarMapper = calendarMapper;
        this.basicDataService = basicDataService;
        this.sessionMapper = sessionMapper;
        this.authService = authService;
    }

    public List<Map<String, Object>> events(Instant from, Instant to, List<String> kinds,
                                             List<Long> listIds, boolean includeFocus, boolean aggregate) {
        validateRange(from, to, aggregate ? 366 : 93);
        normalizeFilters(kinds, listIds);
        Long userId = AuthContext.userId();
        LocalDateTime utcFrom = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        LocalDateTime utcTo = LocalDateTime.ofInstant(to, ZoneOffset.UTC);
        List<String> localKinds = localKinds(kinds);
        List<CalendarEvent> rows = localKinds.isEmpty() ? Collections.emptyList()
                : calendarMapper.selectRange(userId, utcFrom, utcTo, localKinds, listIds);
        if (rows.size() > 500) throw BizException.badRequest("日程过多，请缩小范围或增加筛选条件");

        int cap = aggregate ? MAX_AGGREGATE_RESULTS : MAX_EVENT_RESULTS;
        List<Map<String, Object>> result = new ArrayList<>();
        for (CalendarEvent row : rows) {
            expand(row, from, to, result, cap);
        }
        boolean focusAllowed = includeFocus && (kinds == null || kinds.isEmpty() || kinds.contains("focus"));
        if (focusAllowed && (listIds == null || listIds.isEmpty())) {
            List<FocusSession> sessions = sessionMapper.selectRange(userId, utcFrom, utcTo);
            for (FocusSession session : sessions) {
                if (result.size() >= cap) throw BizException.badRequest("范围内日程过多，请增加筛选条件");
                result.add(focusMap(session));
            }
        }
        result.sort(Comparator.comparing(item -> (String) item.get("startAt")));
        return result;
    }

    public Map<String, Object> get(Long id) {
        CalendarEvent event = requireEvent(id);
        return eventMap(event, instant(event.getStartAt()), instant(event.getEndAt()));
    }

    public Map<String, Object> create(CalendarEventReq req) {
        CalendarEvent event = buildEvent(req, null);
        calendarMapper.insert(event);
        return get(event.getId());
    }

    public Map<String, Object> update(Long id, CalendarEventReq req) {
        CalendarEvent current = requireEvent(id);
        ensureLocal(current);
        if (req.getVersion() == null) throw BizException.badRequest("version 不能为空");
        CalendarEvent event = buildEvent(req, id);
        event.setVersion(req.getVersion());
        if (calendarMapper.update(event) == 0) throw BizException.conflict("日程已被修改，请刷新后重试");
        return get(id);
    }

    public void delete(Long id, Integer version) {
        CalendarEvent current = requireEvent(id);
        ensureLocal(current);
        if (calendarMapper.softDelete(AuthContext.userId(), id, version) == 0) {
            throw BizException.conflict("日程已被修改，请刷新后重试");
        }
    }

    @Transactional
    public Map<String, Object> batch(CalendarBatchReq req) {
        Set<Long> unique = new HashSet<>();
        for (CalendarBatchReq.Item item : req.getItems()) {
            if (!unique.add(item.getId())) throw BizException.badRequest("批量日程不能重复");
        }
        if ("move-list".equals(req.getAction()) && req.getListId() != null) {
            basicDataService.requireList(req.getListId());
        }
        if ("reschedule".equals(req.getAction()) && req.getDate() == null) {
            throw BizException.badRequest("批量改期必须传 date");
        }
        Long userId = AuthContext.userId();
        int updated = 0;
        for (CalendarBatchReq.Item item : req.getItems()) {
            CalendarEvent event = requireEvent(item.getId());
            ensureLocal(event);
            if (!item.getVersion().equals(event.getVersion())) {
                throw BizException.conflict("部分日程已被修改，批量操作已取消");
            }
            switch (req.getAction()) {
                case "complete":
                    requireTaskEvent(event);
                    basicDataService.completeTask(event.getTaskId());
                    updateWithLock(event);
                    break;
                case "delete":
                    if (calendarMapper.softDelete(userId, event.getId(), item.getVersion()) == 0) {
                        throw BizException.conflict("部分日程已被修改，批量操作已取消");
                    }
                    break;
                case "move-list":
                    requireTaskEvent(event);
                    basicDataService.moveTaskList(event.getTaskId(), req.getListId());
                    event.setListId(req.getListId());
                    updateWithLock(event);
                    break;
                case "reschedule":
                    reschedule(event, req.getDate());
                    updateWithLock(event);
                    break;
                default:
                    throw BizException.badRequest("不支持的批量操作");
            }
            updated++;
        }
        return Collections.singletonMap("updated", updated);
    }

    public List<Map<String, Object>> daily(Instant from, Instant to, List<String> kinds,
                                            List<Long> listIds, boolean includeFocus) {
        ZoneId userZone = authService.zoneOf(AuthContext.userId());
        List<Map<String, Object>> events = events(from, to, kinds, listIds, includeFocus, true);
        Map<LocalDate, Map<String, Object>> days = new LinkedHashMap<>();
        LocalDate first = from.atZone(userZone).toLocalDate();
        LocalDate last = to.minusNanos(1).atZone(userZone).toLocalDate();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("date", day.toString());
            value.put("count", 0);
            value.put("focusSec", 0L);
            days.put(day, value);
        }
        for (Map<String, Object> event : events) {
            Instant start = Instant.parse((String) event.get("startAt"));
            ZoneId displayZone = userZone;
            if (!"focus".equals(event.get("kind")) && event.get("timezone") != null) {
                displayZone = ZoneId.of((String) event.get("timezone"));
            }
            LocalDate day = start.atZone(displayZone).toLocalDate();
            Map<String, Object> value = days.get(day);
            if (value == null) continue;
            value.put("count", (Integer) value.get("count") + 1);
            if ("focus".equals(event.get("kind"))) {
                value.put("focusSec", (Long) value.get("focusSec") + ((Number) event.get("durationSec")).longValue());
            }
        }
        return new ArrayList<>(days.values());
    }

    private CalendarEvent buildEvent(CalendarEventReq req, Long id) {
        if (req.getKind() == null || !KINDS.contains(req.getKind()) || "ics".equals(req.getKind())
                || "focus".equals(req.getKind())) {
            throw BizException.badRequest("不支持的日程类型");
        }
        if (req.getStartAt() == null || req.getEndAt() == null || !req.getStartAt().isBefore(req.getEndAt())) {
            throw BizException.badRequest("开始时间必须早于结束时间，且必须带时区偏移");
        }
        String timezone = req.getTimezone() == null || req.getTimezone().trim().isEmpty()
                ? authService.zoneOf(AuthContext.userId()).getId() : req.getTimezone().trim();
        ZoneId zone;
        try { zone = ZoneId.of(timezone); }
        catch (DateTimeException e) { throw BizException.badRequest("无效时区"); }

        CalendarEvent event = new CalendarEvent();
        event.setId(id);
        event.setUserId(AuthContext.userId());
        event.setKind(req.getKind());
        event.setStartAt(LocalDateTime.ofInstant(req.getStartAt(), ZoneOffset.UTC));
        event.setEndAt(LocalDateTime.ofInstant(req.getEndAt(), ZoneOffset.UTC));
        event.setAllDay(req.getAllDay() != null && req.getAllDay());
        event.setTimezone(zone.getId());
        event.setRrule(normalizeRrule(req.getRrule()));
        event.setColor(req.getColor());
        event.setNote(req.getNote() == null ? "" : req.getNote().trim());
        event.setVersion(1);

        if ("task".equals(req.getKind())) {
            TaskItem task = basicDataService.requireTask(req.getTaskId());
            event.setTaskId(task.getId());
            event.setListId(task.getListId());
            event.setTitle(req.getTitle() == null || req.getTitle().trim().isEmpty()
                    ? task.getTitle() : req.getTitle().trim());
        } else if ("habit".equals(req.getKind())) {
            Habit habit = basicDataService.requireHabit(req.getHabitId());
            event.setHabitId(habit.getId());
            event.setTitle(req.getTitle() == null || req.getTitle().trim().isEmpty()
                    ? habit.getName() : req.getTitle().trim());
        } else {
            if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
                throw BizException.badRequest("日程标题不能为空");
            }
            event.setTitle(req.getTitle().trim());
        }
        return event;
    }

    private void expand(CalendarEvent event, Instant from, Instant to,
                        List<Map<String, Object>> output, int cap) {
        Instant originalStart = instant(event.getStartAt());
        Instant originalEnd = instant(event.getEndAt());
        long durationSec = Math.max(1, Duration.between(originalStart, originalEnd).getSeconds());
        if (event.getRrule() == null || event.getRrule().trim().isEmpty()) {
            if (originalStart.isBefore(to) && originalEnd.isAfter(from)) {
                ensureCapacity(output.size(), cap);
                output.add(eventMap(event, originalStart, originalEnd));
            }
            return;
        }
        try {
            Recur recur = new Recur(event.getRrule());
            TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
            TimeZone timeZone = registry.getTimeZone(event.getTimezone());
            DateTime seed = new DateTime(originalStart.toEpochMilli());
            if (timeZone == null) seed.setUtc(true); else seed.setTimeZone(timeZone);
            DateTime periodStart = new DateTime(from.minusSeconds(durationSec).toEpochMilli());
            periodStart.setUtc(true);
            DateTime periodEnd = new DateTime(to.toEpochMilli());
            periodEnd.setUtc(true);
            DateList dates = recur.getDates(seed, periodStart, periodEnd, Value.DATE_TIME,
                    Math.max(1, cap - output.size()));
            for (Date date : dates) {
                Instant start = date.toInstant();
                Instant end = start.plusSeconds(durationSec);
                if (start.isBefore(to) && end.isAfter(from)) {
                    ensureCapacity(output.size(), cap);
                    output.add(eventMap(event, start, end));
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 外部 ICS 的坏规则不应拖垮整个日历；本地规则在保存时已校验。
            if (event.getSubscriptionId() == null) throw BizException.badRequest("无效重复规则");
        }
    }

    private String normalizeRrule(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String rule = value.trim();
        if (rule.toUpperCase().startsWith("RRULE:")) rule = rule.substring(6);
        try { new Recur(rule); }
        catch (Exception e) { throw BizException.badRequest("无效重复规则"); }
        return rule;
    }

    private Map<String, Object> eventMap(CalendarEvent event, Instant start, Instant end) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("eventId", event.getId());
        map.put("occurrenceId", event.getId() + "@" + start.toString());
        map.put("source", "ics".equals(event.getKind()) ? "ics" : "local");
        map.put("kind", event.getKind());
        map.put("taskId", event.getTaskId());
        map.put("habitId", event.getHabitId());
        map.put("listId", event.getListId());
        map.put("subscriptionId", event.getSubscriptionId());
        map.put("title", event.getTitle());
        map.put("note", event.getNote());
        map.put("startAt", start.toString());
        map.put("endAt", end.toString());
        map.put("allDay", event.getAllDay());
        map.put("timezone", event.getTimezone());
        map.put("rrule", event.getRrule());
        map.put("color", event.getDisplayColor() == null ? event.getColor() : event.getDisplayColor());
        map.put("completed", Boolean.TRUE.equals(event.getCompleted()));
        map.put("version", event.getVersion());
        map.put("readOnly", "ics".equals(event.getKind()));
        return map;
    }

    private Map<String, Object> focusMap(FocusSession session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", session.getId());
        map.put("sessionId", session.getId());
        map.put("occurrenceId", "focus-" + session.getId());
        map.put("source", "focus");
        map.put("kind", "focus");
        map.put("title", session.getTaskTitle() == null || session.getTaskTitle().isEmpty()
                ? "专注" : session.getTaskTitle());
        map.put("taskTitle", session.getTaskTitle());
        map.put("taskId", session.getTaskId());
        map.put("habitId", session.getHabitId());
        map.put("note", session.getNote());
        map.put("startAt", instant(session.getStartedAt()).toString());
        map.put("endAt", instant(session.getEndedAt()).toString());
        map.put("allDay", false);
        map.put("color", "countdown".equals(session.getMode()) ? "#ee7b30" : "#3b6ef6");
        map.put("durationSec", session.getDurationSec());
        map.put("status", session.getStatus());
        map.put("mode", session.getMode());
        map.put("version", session.getVersion() == null ? 1 : session.getVersion());
        map.put("readOnly", false);
        return map;
    }

    private CalendarEvent requireEvent(Long id) {
        CalendarEvent event = calendarMapper.selectById(AuthContext.userId(), id);
        if (event == null) throw BizException.notFound("日程不存在");
        return event;
    }

    private void ensureLocal(CalendarEvent event) {
        if ("ics".equals(event.getKind()) || event.getSubscriptionId() != null) {
            throw BizException.badRequest("订阅日历为只读，不能修改");
        }
    }

    private void requireTaskEvent(CalendarEvent event) {
        if (!"task".equals(event.getKind()) || event.getTaskId() == null) {
            throw BizException.badRequest("此操作仅支持任务日程");
        }
    }

    private void updateWithLock(CalendarEvent event) {
        int oldVersion = event.getVersion();
        if (calendarMapper.update(event) == 0) throw BizException.conflict("部分日程已被修改，批量操作已取消");
        event.setVersion(oldVersion + 1);
    }

    private void reschedule(CalendarEvent event, LocalDate targetDate) {
        ZoneId zone;
        try { zone = ZoneId.of(event.getTimezone()); }
        catch (DateTimeException e) { zone = authService.zoneOf(AuthContext.userId()); }
        Instant oldStart = instant(event.getStartAt());
        long durationSec = Math.max(1, Duration.between(oldStart, instant(event.getEndAt())).getSeconds());
        java.time.LocalTime localTime = oldStart.atZone(zone).toLocalTime();
        Instant newStart = targetDate.atTime(localTime).atZone(zone).toInstant();
        event.setStartAt(LocalDateTime.ofInstant(newStart, ZoneOffset.UTC));
        event.setEndAt(LocalDateTime.ofInstant(newStart.plusSeconds(durationSec), ZoneOffset.UTC));
    }

    private void validateRange(Instant from, Instant to, int maxDays) {
        if (from == null || to == null || !from.isBefore(to)) throw BizException.badRequest("无效时间范围");
        if (Duration.between(from, to).toDays() > maxDays) {
            throw BizException.badRequest("时间范围不能超过 " + maxDays + " 天");
        }
    }

    private void normalizeFilters(List<String> kinds, List<Long> listIds) {
        if (kinds != null) {
            if (kinds.size() > KINDS.size() || !KINDS.containsAll(kinds)) {
                throw BizException.badRequest("无效日程类型筛选");
            }
        }
        if (listIds != null && listIds.size() > 50) throw BizException.badRequest("清单筛选不能超过 50 项");
    }

    private List<String> localKinds(List<String> kinds) {
        if (kinds == null || kinds.isEmpty()) return Arrays.asList("task", "habit", "anniversary", "course", "ics");
        List<String> result = new ArrayList<>();
        for (String kind : kinds) if (!"focus".equals(kind)) result.add(kind);
        return result;
    }

    private Instant instant(LocalDateTime utc) { return utc.toInstant(ZoneOffset.UTC); }

    private void ensureCapacity(int size, int cap) {
        if (size >= cap) throw BizException.badRequest("范围内日程过多，请缩小范围或增加筛选条件");
    }
}
