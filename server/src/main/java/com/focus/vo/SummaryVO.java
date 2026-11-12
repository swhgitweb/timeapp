package com.focus.vo;

/** 汇总统计（GET /api/stats/summary） */
public class SummaryVO {

    /** 总专注秒数 */
    private Long totalSec;
    /** 番茄数：完成的倒计时会话数 */
    private Long pomodoroCount;
    /** 中断次数 */
    private Long abortedCount;
    /** 有记录的天数 */
    private Long activeDays;

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

    public Long getAbortedCount() {
        return abortedCount;
    }

    public void setAbortedCount(Long abortedCount) {
        this.abortedCount = abortedCount;
    }

    public Long getActiveDays() {
        return activeDays;
    }

    public void setActiveDays(Long activeDays) {
        this.activeDays = activeDays;
    }
}
