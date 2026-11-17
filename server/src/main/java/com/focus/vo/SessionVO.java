package com.focus.vo;

import java.time.Instant;
import java.time.LocalDate;

public class SessionVO {
    private Long id;
    private String clientId;
    private Long taskId;
    private Long habitId;
    private String taskTitle;
    private String note;
    private String mode;
    private Integer plannedSec;
    private Instant startedAt;
    private Instant endedAt;
    private Integer durationSec;
    private Integer pausedSec;
    private String status;
    private String source;
    private LocalDate sessionDate;
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate sessionDate) { this.sessionDate = sessionDate; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
