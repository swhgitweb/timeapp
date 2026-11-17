package com.focus.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class TaskUpsertReq {
    private Long listId;
    @NotBlank @Size(max = 200)
    private String title;
    @Size(max = 2000)
    private String note;
    private Boolean completed;
    private Integer version;

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
