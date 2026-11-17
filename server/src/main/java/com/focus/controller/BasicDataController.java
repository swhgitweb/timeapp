package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.dto.HabitUpsertReq;
import com.focus.dto.ListUpsertReq;
import com.focus.dto.TaskUpsertReq;
import com.focus.entity.Habit;
import com.focus.entity.TaskItem;
import com.focus.entity.TaskList;
import com.focus.service.BasicDataService;
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

@RestController
@RequestMapping("/api")
public class BasicDataController {
    private final BasicDataService service;
    public BasicDataController(BasicDataService service) { this.service = service; }

    @GetMapping("/lists") public CommonResult<List<TaskList>> lists() { return CommonResult.ok(service.lists()); }
    @PostMapping("/lists") public CommonResult<TaskList> createList(@Valid @RequestBody ListUpsertReq req) { return CommonResult.ok(service.createList(req)); }
    @PutMapping("/lists/{id}") public CommonResult<TaskList> updateList(@PathVariable Long id, @Valid @RequestBody ListUpsertReq req) { return CommonResult.ok(service.updateList(id, req)); }
    @DeleteMapping("/lists/{id}") public CommonResult<Void> deleteList(@PathVariable Long id) { service.deleteList(id); return CommonResult.ok(); }

    @GetMapping("/tasks") public CommonResult<List<TaskItem>> tasks() { return CommonResult.ok(service.tasks()); }
    @PostMapping("/tasks") public CommonResult<TaskItem> createTask(@Valid @RequestBody TaskUpsertReq req) { return CommonResult.ok(service.createTask(req)); }
    @PutMapping("/tasks/{id}") public CommonResult<TaskItem> updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpsertReq req) { return CommonResult.ok(service.updateTask(id, req)); }
    @DeleteMapping("/tasks/{id}") public CommonResult<Void> deleteTask(@PathVariable Long id) { service.deleteTask(id); return CommonResult.ok(); }

    @GetMapping("/habits") public CommonResult<List<Habit>> habits() { return CommonResult.ok(service.habits()); }
    @PostMapping("/habits") public CommonResult<Habit> createHabit(@Valid @RequestBody HabitUpsertReq req) { return CommonResult.ok(service.createHabit(req)); }
    @PutMapping("/habits/{id}") public CommonResult<Habit> updateHabit(@PathVariable Long id, @Valid @RequestBody HabitUpsertReq req) { return CommonResult.ok(service.updateHabit(id, req)); }
    @DeleteMapping("/habits/{id}") public CommonResult<Void> deleteHabit(@PathVariable Long id) { service.deleteHabit(id); return CommonResult.ok(); }
}
