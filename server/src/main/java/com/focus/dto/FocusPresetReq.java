package com.focus.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class FocusPresetReq {
    @NotBlank @Size(max = 100) private String name;
    @NotBlank @Pattern(regexp = "countup|countdown") private String mode;
    @Min(1) @Max(10800) private Integer plannedSec;
    private Long taskId;
    private Long habitId;
    @Size(max = 2000) private String noteTemplate;
    private Integer sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Integer getPlannedSec() { return plannedSec; }
    public void setPlannedSec(Integer plannedSec) { this.plannedSec = plannedSec; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getHabitId() { return habitId; }
    public void setHabitId(Long habitId) { this.habitId = habitId; }
    public String getNoteTemplate() { return noteTemplate; }
    public void setNoteTemplate(String noteTemplate) { this.noteTemplate = noteTemplate; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
