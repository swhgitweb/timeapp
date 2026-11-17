package com.focus.entity;

public class FocusPreset {
    private Long id;
    private Long userId;
    private String name;
    private String mode;
    private Integer plannedSec;
    private Long taskId;
    private Long habitId;
    private String noteTemplate;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
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
