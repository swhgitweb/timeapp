package com.focus.entity;

/** 可关联专注并配置重复日程的轻量习惯。 */
public class Habit {
    private Long id;
    private Long userId;
    private String name;
    private String color;
    private Boolean archived;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
}
