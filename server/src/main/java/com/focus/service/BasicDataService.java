package com.focus.service;

import com.focus.common.AuthContext;
import com.focus.common.BizException;
import com.focus.dto.HabitUpsertReq;
import com.focus.dto.ListUpsertReq;
import com.focus.dto.TaskUpsertReq;
import com.focus.entity.Habit;
import com.focus.entity.TaskItem;
import com.focus.entity.TaskList;
import com.focus.mapper.BasicDataMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BasicDataService {
    private final BasicDataMapper mapper;

    public BasicDataService(BasicDataMapper mapper) { this.mapper = mapper; }

    public List<TaskList> lists() { return mapper.selectLists(AuthContext.userId()); }

    public TaskList createList(ListUpsertReq req) {
        TaskList list = new TaskList();
        list.setUserId(AuthContext.userId());
        apply(list, req);
        mapper.insertList(list);
        return list;
    }

    public TaskList updateList(Long id, ListUpsertReq req) {
        TaskList list = requireList(id);
        apply(list, req);
        if (mapper.updateList(list) == 0) throw BizException.notFound("清单不存在");
        return list;
    }

    public void deleteList(Long id) {
        requireList(id);
        mapper.archiveList(AuthContext.userId(), id);
    }

    public List<TaskItem> tasks() { return mapper.selectTasks(AuthContext.userId()); }

    public TaskItem createTask(TaskUpsertReq req) {
        TaskItem task = new TaskItem();
        task.setUserId(AuthContext.userId());
        task.setVersion(1);
        apply(task, req);
        mapper.insertTask(task);
        return task;
    }

    public TaskItem updateTask(Long id, TaskUpsertReq req) {
        TaskItem current = requireTask(id);
        if (req.getVersion() == null) throw BizException.badRequest("version 不能为空");
        current.setVersion(req.getVersion());
        apply(current, req);
        if (mapper.updateTask(current) == 0) throw BizException.conflict("任务已被修改，请刷新后重试");
        current.setVersion(req.getVersion() + 1);
        return current;
    }

    public void deleteTask(Long id) {
        if (mapper.softDeleteTask(AuthContext.userId(), id) == 0) throw BizException.notFound("任务不存在");
    }

    public List<Habit> habits() { return mapper.selectHabits(AuthContext.userId()); }

    public Habit createHabit(HabitUpsertReq req) {
        Habit habit = new Habit();
        habit.setUserId(AuthContext.userId());
        apply(habit, req);
        mapper.insertHabit(habit);
        return habit;
    }

    public Habit updateHabit(Long id, HabitUpsertReq req) {
        Habit habit = requireHabit(id);
        apply(habit, req);
        if (mapper.updateHabit(habit) == 0) throw BizException.notFound("习惯不存在");
        return habit;
    }

    public void deleteHabit(Long id) {
        requireHabit(id);
        mapper.archiveHabit(AuthContext.userId(), id);
    }

    public TaskList requireList(Long id) {
        TaskList list = mapper.selectListById(AuthContext.userId(), id);
        if (list == null) throw BizException.badRequest("清单不存在");
        return list;
    }

    public TaskItem requireTask(Long id) {
        TaskItem task = id == null ? null : mapper.selectTaskById(AuthContext.userId(), id);
        if (task == null) throw BizException.badRequest("任务不存在");
        return task;
    }

    public void completeTask(Long id) {
        if (mapper.completeTask(AuthContext.userId(), id) == 0) throw BizException.badRequest("任务不存在");
    }

    public void moveTaskList(Long id, Long listId) {
        if (mapper.moveTaskList(AuthContext.userId(), id, listId) == 0) throw BizException.badRequest("任务不存在");
    }

    public Habit requireHabit(Long id) {
        Habit habit = id == null ? null : mapper.selectHabitById(AuthContext.userId(), id);
        if (habit == null) throw BizException.badRequest("习惯不存在");
        return habit;
    }

    private void apply(TaskList list, ListUpsertReq req) {
        list.setName(req.getName().trim());
        list.setColor(req.getColor() == null ? "#3b6ef6" : req.getColor());
        list.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        list.setArchived(req.getArchived() != null && req.getArchived());
    }

    private void apply(TaskItem task, TaskUpsertReq req) {
        if (req.getListId() != null) requireList(req.getListId());
        task.setListId(req.getListId());
        task.setTitle(req.getTitle().trim());
        task.setNote(req.getNote() == null ? "" : req.getNote().trim());
        task.setCompleted(req.getCompleted() != null && req.getCompleted());
    }

    private void apply(Habit habit, HabitUpsertReq req) {
        habit.setName(req.getName().trim());
        habit.setColor(req.getColor() == null ? "#22a06b" : req.getColor());
        habit.setArchived(req.getArchived() != null && req.getArchived());
    }
}
