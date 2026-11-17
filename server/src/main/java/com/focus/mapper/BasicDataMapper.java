package com.focus.mapper;

import com.focus.entity.Habit;
import com.focus.entity.TaskItem;
import com.focus.entity.TaskList;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BasicDataMapper {
    List<TaskList> selectLists(@Param("userId") Long userId);
    TaskList selectListById(@Param("userId") Long userId, @Param("id") Long id);
    int insertList(TaskList list);
    int updateList(TaskList list);
    int archiveList(@Param("userId") Long userId, @Param("id") Long id);

    List<TaskItem> selectTasks(@Param("userId") Long userId);
    TaskItem selectTaskById(@Param("userId") Long userId, @Param("id") Long id);
    int insertTask(TaskItem task);
    int updateTask(TaskItem task);
    int softDeleteTask(@Param("userId") Long userId, @Param("id") Long id);
    int completeTask(@Param("userId") Long userId, @Param("id") Long id);
    int moveTaskList(@Param("userId") Long userId, @Param("id") Long id, @Param("listId") Long listId);

    List<Habit> selectHabits(@Param("userId") Long userId);
    Habit selectHabitById(@Param("userId") Long userId, @Param("id") Long id);
    int insertHabit(Habit habit);
    int updateHabit(Habit habit);
    int archiveHabit(@Param("userId") Long userId, @Param("id") Long id);
}
