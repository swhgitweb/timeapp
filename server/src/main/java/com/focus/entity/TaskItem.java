package com.focus.entity;

/** 轻量任务，不包含独立提醒、子任务等完整任务管理能力。 */
public class TaskItem {
    private Long id;
    private Long userId;
    private Long listId;
    private String title;
    private String note;
    private Boolean completed;
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getListId() { return listId; }
    public void setListId(Long listId) { this.listId = listId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
