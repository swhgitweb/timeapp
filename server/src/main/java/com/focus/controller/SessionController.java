package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.dto.SessionCreateReq;
import com.focus.dto.SessionUpdateReq;
import com.focus.service.SessionService;
import com.focus.vo.SessionVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 创建会话（幂等） */
    @PostMapping
    public CommonResult<SessionVO> create(@Valid @RequestBody SessionCreateReq req) {
        return CommonResult.ok(sessionService.create(req));
    }

    /** 单条详情用于日历深链编辑。 */
    @GetMapping("/{id}")
    public CommonResult<SessionVO> get(@PathVariable Long id) {
        return CommonResult.ok(sessionService.get(id));
    }

    /** 编辑关联、笔记和时间；version 用于乐观锁。 */
    @PatchMapping("/{id}")
    public CommonResult<SessionVO> update(@PathVariable Long id, @Valid @RequestBody SessionUpdateReq req) {
        return CommonResult.ok(sessionService.update(id, req));
    }

    /** 记录列表，按开始时间倒序 */
    @GetMapping
    public CommonResult<List<SessionVO>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return CommonResult.ok(sessionService.list(from, to, limit));
    }

    /** 任务名候选 */
    @GetMapping("/task-titles")
    public CommonResult<List<String>> taskTitles(@RequestParam(required = false) Integer limit) {
        return CommonResult.ok(sessionService.taskTitles(limit));
    }

    /** 删除记录（软删除） */
    @DeleteMapping("/{id}")
    public CommonResult<Void> delete(@PathVariable Long id) {
        sessionService.delete(id);
        return CommonResult.ok();
    }
}
