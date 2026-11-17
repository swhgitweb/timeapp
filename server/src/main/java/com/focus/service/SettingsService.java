package com.focus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focus.common.AuthContext;
import com.focus.common.BizException;
import com.focus.mapper.UserSettingMapper;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SettingsService {
    private final UserSettingMapper mapper;
    private final ObjectMapper objectMapper;

    public SettingsService(UserSettingMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> get() {
        Map<String, Object> result = defaults();
        String json = mapper.findJson(AuthContext.userId());
        if (json == null || json.trim().isEmpty()) return result;
        try {
            JsonNode saved = objectMapper.readTree(json);
            for (String key : result.keySet()) {
                if (saved.has(key)) result.put(key, objectMapper.convertValue(saved.get(key), Object.class));
            }
            return result;
        } catch (Exception e) {
            throw BizException.badRequest("设置数据格式错误");
        }
    }

    public Map<String, Object> update(JsonNode settings) {
        if (settings == null || !settings.isObject()) throw BizException.badRequest("设置必须是 JSON 对象");
        try {
            if (objectMapper.writeValueAsString(settings).length() > 20000) {
                throw BizException.badRequest("设置内容过长");
            }
            validate(settings);
            Map<String, Object> merged = get();
            settings.fields().forEachRemaining(entry -> {
                if (merged.containsKey(entry.getKey())) {
                    merged.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
                }
            });
            mapper.upsert(AuthContext.userId(), objectMapper.writeValueAsString(merged));
            return get();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.badRequest("设置数据格式错误");
        }
    }

    private Map<String, Object> defaults() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("defaultView", "month");
        values.put("weekStart", 1);
        values.put("visibleListIds", Arrays.asList());
        values.put("visibleKinds", Arrays.asList("task", "habit", "anniversary", "course", "ics"));
        values.put("showFocus", true);
        values.put("showLunar", true);
        values.put("showHolidays", true);
        values.put("showHabits", true);
        values.put("showAnniversaries", true);
        values.put("showCourses", true);
        values.put("auxiliaryTimezone", "");
        return values;
    }

    private void validate(JsonNode settings) {
        if (settings.has("defaultView")) {
            String view = settings.get("defaultView").asText();
            if (!Arrays.asList("year", "month", "week", "agenda", "multi-day", "multi-week").contains(view)) {
                throw BizException.badRequest("无效默认日历视图");
            }
        }
        if (settings.has("weekStart")) {
            int weekStart = settings.get("weekStart").asInt(-1);
            if (weekStart != 0 && weekStart != 1) throw BizException.badRequest("周起始日只能是周日或周一");
        }
        for (String key : Arrays.asList("showFocus", "showLunar", "showHolidays", "showHabits",
                "showAnniversaries", "showCourses")) {
            if (settings.has(key) && !settings.get(key).isBoolean()) throw BizException.badRequest(key + " 必须是布尔值");
        }
        for (String key : Arrays.asList("visibleListIds", "visibleKinds")) {
            if (settings.has(key) && !settings.get(key).isArray()) throw BizException.badRequest(key + " 必须是数组");
        }
        if (settings.has("auxiliaryTimezone")) {
            String timezone = settings.get("auxiliaryTimezone").asText();
            if (!timezone.isEmpty()) {
                try { ZoneId.of(timezone); }
                catch (DateTimeException e) { throw BizException.badRequest("无效辅助时区"); }
            }
        }
    }
}
