package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.dto.FocusPresetReq;
import com.focus.entity.FocusPreset;
import com.focus.service.FocusPresetService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/presets")
public class FocusPresetController {
    private final FocusPresetService service;
    public FocusPresetController(FocusPresetService service) { this.service = service; }
    @GetMapping public CommonResult<List<FocusPreset>> list() { return CommonResult.ok(service.list()); }
    @PostMapping public CommonResult<FocusPreset> create(@Valid @RequestBody FocusPresetReq req) { return CommonResult.ok(service.create(req)); }
    @PutMapping("/{id}") public CommonResult<FocusPreset> update(@PathVariable Long id, @Valid @RequestBody FocusPresetReq req) { return CommonResult.ok(service.update(id, req)); }
    @DeleteMapping("/{id}") public CommonResult<Void> delete(@PathVariable Long id) { service.delete(id); return CommonResult.ok(); }
}
