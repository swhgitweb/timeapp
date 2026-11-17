package com.focus.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;

public class SessionUpdateReq {
    private Long taskId;
    private Long habitId;
    @Size(max = 2000) private String note;
    @NotNull private Instant startedAt;
    @NotNull private Instant endedAt;
    @NotNull @Min(1) @Max(43200) private Integer durationSec;
    @Min(0) private Integer pausedSec;
    @NotNull private Integer version;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getHabitId() { return habitId; }
    public void setHabitId(Long habitId) { this.habitId = habitId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public Integer getPausedSec() { return pausedSec; }
    public void setPausedSec(Integer pausedSec) { this.pausedSec = pausedSec; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
