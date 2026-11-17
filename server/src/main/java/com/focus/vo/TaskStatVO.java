package com.focus.vo;

/** 按任务聚合统计（GET /api/stats/by-task） */
public class TaskStatVO {

    /** 任务名，空标题归为「未填写任务」 */
    private String taskTitle;
    /** 该任务累计专注秒数 */
    private Long totalSec;
    /** 该任务完成的番茄数 */
    private Long pomodoroCount;
    /** 该任务的记录条数 */
    private Long sessionCount;

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public Long getTotalSec() {
        return totalSec;
    }

    public void setTotalSec(Long totalSec) {
        this.totalSec = totalSec;
    }

    public Long getPomodoroCount() {
        return pomodoroCount;
    }

    public void setPomodoroCount(Long pomodoroCount) {
        this.pomodoroCount = pomodoroCount;
    }

    public Long getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(Long sessionCount) {
        this.sessionCount = sessionCount;
    }
}
