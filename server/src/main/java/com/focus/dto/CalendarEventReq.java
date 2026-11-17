package com.focus.dto;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.Instant;

/** 本地日程完整编辑请求；时间必须带时区偏移。 */
public class CalendarEventReq {
    @Pattern(regexp = "task|habit|anniversary|course", message = "不支持的日程类型")
    private String kind;
    private Long taskId;
    private Long habitId;
    @Size(max = 200)
    private String title;
    @Size(max = 2000)
    private String note;
    private Instant startAt;
    private Instant endAt;
    private Boolean allDay;
    private String timezone;
    @Size(max = 500)
    private String rrule;
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "必须是六位颜色值")
    private String color;
    private Integer version;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getHabitId() { return habitId; }
    public void setHabitId(Long habitId) { this.habitId = habitId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public Boolean getAllDay() { return allDay; }
    public void setAllDay(Boolean allDay) { this.allDay = allDay; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getRrule() { return rrule; }
    public void setRrule(String rrule) { this.rrule = rrule; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
