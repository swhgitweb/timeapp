package com.focus.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ListUpsertReq {
    @NotBlank @Size(max = 100)
    private String name;
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "必须是六位颜色值")
    private String color;
    private Integer sortOrder;
    private Boolean archived;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
}
