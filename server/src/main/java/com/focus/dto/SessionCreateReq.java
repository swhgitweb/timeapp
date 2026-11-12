package com.focus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/** POST /api/sessions 请求体（文档 6.2） */
public class SessionCreateReq {

    @NotBlank(message = "不能为空")
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "必须是 UUID"
    )
    private String clientId;

    @Size(max = 100, message = "最长 100 字")
    private String taskTitle;

    @Size(max = 200, message = "最长 200 字")
    private String note;

    @NotBlank(message = "不能为空")
    @Pattern(regexp = "countup|countdown", message = "必须是 countup 或 countdown")
    private String mode;

    /** 倒计时预设秒数，1–10800；正计时可为空 */
    @Min(value = 1, message = "最小 1 秒")
    @Max(value = 10800, message = "最大 10800 秒")
    private Integer plannedSec;

    @NotNull(message = "不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @NotNull(message = "不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endedAt;

    @NotNull(message = "不能为空")
    @Min(value = 1, message = "最小 1 秒")
    @Max(value = 43200, message = "最大 43200 秒")
    private Integer durationSec;

    @Min(value = 0, message = "不能为负")
    private Integer pausedSec;

    @NotBlank(message = "不能为空")
    @Pattern(regexp = "completed|aborted", message = "必须是 completed 或 aborted")
    private String status;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getPlannedSec() {
        return plannedSec;
    }

    public void setPlannedSec(Integer plannedSec) {
        this.plannedSec = plannedSec;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public Integer getPausedSec() {
        return pausedSec;
    }

    public void setPausedSec(Integer pausedSec) {
        this.pausedSec = pausedSec;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
