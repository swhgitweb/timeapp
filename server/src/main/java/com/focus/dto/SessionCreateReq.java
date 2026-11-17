package com.focus.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.Instant;

public class SessionCreateReq {
    @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "必须是 UUID")
    private String clientId;
    private Long taskId;
    private Long habitId;
    @Size(max = 200) private String taskTitle;
    @Size(max = 2000) private String note;
    @NotBlank @Pattern(regexp = "countup|countdown", message = "必须是 countup 或 countdown")
    private String mode;
    @Min(1) @Max(10800) private Integer plannedSec;
    @NotNull private Instant startedAt;
    @NotNull private Instant endedAt;
    @NotNull @Min(1) @Max(43200) private Integer durationSec;
    @Min(0) private Integer pausedSec;
    @NotBlank @Pattern(regexp = "completed|aborted", message = "必须是 completed 或 aborted")
    private String status;
    @Pattern(regexp = "timer|manual", message = "必须是 timer 或 manual")
    private String source;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getHabitId() { return habitId; }
    public void setHabitId(Long habitId) { this.habitId = habitId; }
    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Integer getPlannedSec() { return plannedSec; }
    public void setPlannedSec(Integer plannedSec) { this.plannedSec = plannedSec; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public Integer getPausedSec() { return pausedSec; }
    public void setPausedSec(Integer pausedSec) { this.pausedSec = pausedSec; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
