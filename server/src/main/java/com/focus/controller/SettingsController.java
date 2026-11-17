package com.focus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.focus.common.CommonResult;
import com.focus.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService service;
    public SettingsController(SettingsService service) { this.service = service; }

    @GetMapping public CommonResult<Map<String, Object>> get() { return CommonResult.ok(service.get()); }
    @PutMapping public CommonResult<Map<String, Object>> update(@RequestBody JsonNode settings) {
        return CommonResult.ok(service.update(settings));
    }
}
