package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.service.IcsExportService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class CalendarExportController {
    private final IcsExportService service;

    public CalendarExportController(IcsExportService service) { this.service = service; }

    @GetMapping("/api/calendar/export")
    public CommonResult<Map<String, Object>> status() { return CommonResult.ok(service.status()); }

    @PostMapping("/api/calendar/export")
    public CommonResult<Map<String, Object>> enable() { return CommonResult.ok(service.enable()); }

    @DeleteMapping("/api/calendar/export")
    public CommonResult<Void> revoke() {
        service.revoke();
        return CommonResult.ok();
    }

    @GetMapping("/calendars/{token}.ics")
    public ResponseEntity<byte[]> download(@PathVariable String token) {
        byte[] body = service.export(token);
        if (body == null) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "calendar", StandardCharsets.UTF_8));
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                .filename("focus-calendar.ics", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
