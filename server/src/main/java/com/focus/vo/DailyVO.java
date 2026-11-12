package com.focus.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/** 每日趋势（GET /api/stats/daily），缺失日期由服务层补零 */
public class DailyVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private Long totalSec;
    private Long pomodoroCount;

    public DailyVO() {
    }

    public DailyVO(LocalDate date, Long totalSec, Long pomodoroCount) {
        this.date = date;
        this.totalSec = totalSec;
        this.pomodoroCount = pomodoroCount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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
}
