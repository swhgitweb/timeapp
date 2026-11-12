package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.service.SessionService;
import com.focus.vo.DailyVO;
import com.focus.vo.SummaryVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final SessionService sessionService;

    public StatsController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 汇总：总时长、番茄数、中断数、活跃天数 */
    @GetMapping("/summary")
    public CommonResult<SummaryVO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CommonResult.ok(sessionService.summary(from, to));
    }

    /** 每日趋势，缺失日期补零 */
    @GetMapping("/daily")
    public CommonResult<List<DailyVO>> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CommonResult.ok(sessionService.daily(from, to));
    }
}
