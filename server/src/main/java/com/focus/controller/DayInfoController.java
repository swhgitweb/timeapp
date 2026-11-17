package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.service.DayInfoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar/day-info")
public class DayInfoController {
    private final DayInfoService service;
    public DayInfoController(DayInfoService service) { this.service = service; }

    @GetMapping
    public CommonResult<List<Map<String, Object>>> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CommonResult.ok(service.range(from, to));
    }
}
