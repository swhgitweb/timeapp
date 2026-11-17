package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.dto.SubscriptionReq;
import com.focus.entity.CalendarSubscription;
import com.focus.service.IcsSubscriptionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar/subscriptions")
public class CalendarSubscriptionController {
    private final IcsSubscriptionService service;

    public CalendarSubscriptionController(IcsSubscriptionService service) { this.service = service; }

    @GetMapping
    public CommonResult<List<CalendarSubscription>> list() { return CommonResult.ok(service.list()); }

    @PostMapping
    public CommonResult<CalendarSubscription> create(@Valid @RequestBody SubscriptionReq req) {
        return CommonResult.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public CommonResult<CalendarSubscription> update(@PathVariable Long id, @Valid @RequestBody SubscriptionReq req) {
        return CommonResult.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.ok();
    }

    @PostMapping("/{id}/refresh")
    public CommonResult<Map<String, Object>> refresh(@PathVariable Long id) {
        return CommonResult.ok(service.refresh(id));
    }
}
