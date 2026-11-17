package com.focus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focus.common.BizException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 农历由历法库计算；法定节假日只展示带官方来源的年份数据。 */
@Service
public class DayInfoService {
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, String>> holidays = new HashMap<>();
    private final Map<Integer, String> sources = new HashMap<>();

    public DayInfoService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("holidays.json");
        JsonNode years = objectMapper.readTree(resource.getInputStream());
        for (JsonNode year : years) {
            int yearNumber = year.get("year").asInt();
            sources.put(yearNumber, year.get("source").asText());
            JsonNode days = year.get("days");
            days.fields().forEachRemaining(entry -> {
                Map<String, String> value = new HashMap<>();
                value.put("name", entry.getValue().get("name").asText());
                value.put("type", entry.getValue().get("type").asText());
                holidays.put(entry.getKey(), value);
            });
        }
    }

    public List<Map<String, Object>> range(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw BizException.badRequest("无效日期范围");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw BizException.badRequest("日期范围不能超过 366 天");
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            LunarUtil.Info lunar = LunarUtil.from(day);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("lunar", lunar.getText());
            item.put("lunarFestivals", lunar.getFestivals());
            Map<String, String> holiday = holidays.get(day.toString());
            item.put("holiday", holiday == null ? null : holiday.get("name"));
            item.put("dayType", holiday == null ? null : holiday.get("type"));
            item.put("holidaySource", sources.get(day.getYear()));
            result.add(item);
        }
        return result;
    }
}
