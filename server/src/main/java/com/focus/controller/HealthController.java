package com.focus.controller;

import com.focus.common.CommonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public CommonResult<Map<String, String>> health() {
        Map<String, String> data = new HashMap<>();
        data.put("status", "UP");
        return CommonResult.ok(data);
    }
}
