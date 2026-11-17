package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.dto.CalendarBatchReq;
import com.focus.dto.CalendarEventReq;
import com.focus.service.CalendarService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarService service;
    public CalendarController(CalendarService service) { this.service = service; }

    @GetMapping("/events")
    public CommonResult<List<Map<String, Object>>> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) List<String> kinds,
            @RequestParam(required = false) List<Long> listIds,
            @RequestParam(defaultValue = "false") boolean includeFocus) {
        return CommonResult.ok(service.events(from, to, kinds, listIds, includeFocus, false));
    }

    @PostMapping("/events")
    public CommonResult<Map<String, Object>> create(@Valid @RequestBody CalendarEventReq req) {
        return CommonResult.ok(service.create(req));
    }

    @GetMapping("/events/{id}")
    public CommonResult<Map<String, Object>> get(@PathVariable Long id) {
        return CommonResult.ok(service.get(id));
    }

    @PutMapping("/events/{id}")
    public CommonResult<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody CalendarEventReq req) {
        return CommonResult.ok(service.update(id, req));
    }

    @DeleteMapping("/events/{id}")
    public CommonResult<Void> delete(@PathVariable Long id, @RequestParam(required = false) Integer version) {
        service.delete(id, version);
        return CommonResult.ok();
    }

    @PostMapping("/events/batch")
    public CommonResult<Map<String, Object>> batch(@Valid @RequestBody CalendarBatchReq req) {
        return CommonResult.ok(service.batch(req));
    }

    @GetMapping("/daily")
    public CommonResult<List<Map<String, Object>>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) List<String> kinds,
            @RequestParam(required = false) List<Long> listIds,
            @RequestParam(defaultValue = "false") boolean includeFocus) {
        return CommonResult.ok(service.daily(from, to, kinds, listIds, includeFocus));
    }
}
