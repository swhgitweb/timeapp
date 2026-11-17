package com.focus.service;

import com.focus.common.AuthContext;
import com.focus.entity.AppUser;
import com.focus.entity.CalendarEvent;
import com.focus.mapper.CalendarMapper;
import com.focus.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IcsExportService {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserMapper userMapper;
    private final CalendarMapper calendarMapper;

    public IcsExportService(UserMapper userMapper, CalendarMapper calendarMapper) {
        this.userMapper = userMapper;
        this.calendarMapper = calendarMapper;
    }

    public Map<String, Object> status() {
        return tokenVO(userMapper.selectById(AuthContext.userId()));
    }

    public Map<String, Object> enable() {
        Long userId = AuthContext.userId();
        AppUser user = userMapper.selectById(userId);
        if (user.getIcsToken() == null || user.getIcsToken().trim().isEmpty()) {
            String token = UUID.randomUUID().toString();
            userMapper.updateIcsToken(userId, token);
            user.setIcsToken(token);
        }
        return tokenVO(user);
    }

    public void revoke() {
        userMapper.updateIcsToken(AuthContext.userId(), null);
    }

    /** 公开导出只按不可预测的令牌读取，不包含任何专注记录。 */
    public byte[] export(String token) {
        if (token == null || !token.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            return null;
        }
        AppUser user = userMapper.selectByIcsToken(token.toLowerCase());
        if (user == null) return null;
        List<CalendarEvent> events = calendarMapper.selectExport(user.getId());
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//Focus App//Calendar Export//ZH-CN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escape(user.getDisplayName() + "的日程"));
        String timestamp = UTC.format(Instant.now());
        for (CalendarEvent event : events) appendEvent(out, event, timestamp);
        line(out, "END:VCALENDAR");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendEvent(StringBuilder out, CalendarEvent event, String timestamp) {
        ZoneId zone;
        try { zone = ZoneId.of(event.getTimezone()); }
        catch (Exception e) { zone = ZoneOffset.UTC; }
        Instant start = event.getStartAt().toInstant(ZoneOffset.UTC);
        Instant end = event.getEndAt().toInstant(ZoneOffset.UTC);
        line(out, "BEGIN:VEVENT");
        line(out, "UID:focus-event-" + event.getId() + "@focus.local");
        line(out, "DTSTAMP:" + timestamp);
        if (Boolean.TRUE.equals(event.getAllDay())) {
            line(out, "DTSTART;VALUE=DATE:" + DATE.format(start.atZone(zone)));
            line(out, "DTEND;VALUE=DATE:" + DATE.format(end.atZone(zone)));
        } else {
            line(out, "DTSTART:" + UTC.format(start));
            line(out, "DTEND:" + UTC.format(end));
        }
        line(out, "SUMMARY:" + escape(event.getTitle()));
        if (event.getNote() != null && !event.getNote().isEmpty()) {
            line(out, "DESCRIPTION:" + escape(event.getNote()));
        }
        if (event.getRrule() != null && !event.getRrule().trim().isEmpty()) {
            String rrule = event.getRrule().trim();
            line(out, (rrule.toUpperCase().startsWith("RRULE:") ? "" : "RRULE:") + rrule);
        }
        line(out, "END:VEVENT");
    }

    private Map<String, Object> tokenVO(AppUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        String token = user == null ? null : user.getIcsToken();
        result.put("enabled", token != null && !token.isEmpty());
        result.put("url", token == null || token.isEmpty() ? null : "/calendars/" + token + ".ics");
        return result;
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
                .replace(";", "\\;").replace(",", "\\,");
    }

    /** 按 UTF-8 字节折叠到 75 字节，并避免截断多字节字符。 */
    private void line(StringBuilder out, String value) {
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String part = new String(Character.toChars(codePoint));
            int length = part.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + length > 75) {
                out.append("\r\n ");
                bytes = 1;
            }
            out.append(part);
            bytes += length;
            offset += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }
}
