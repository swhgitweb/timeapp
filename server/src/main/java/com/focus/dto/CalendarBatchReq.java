package com.focus.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 批量操作只接受本站日程，并以每项版本号防止并发覆盖。 */
public class CalendarBatchReq {
    @NotBlank
    @Pattern(regexp = "complete|delete|move-list|reschedule", message = "不支持的批量操作")
    private String action;
    @NotEmpty @Size(max = 100) @Valid
    private List<Item> items;
    private Long listId;
    private LocalDate date;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public Long getListId() { return listId; }
    public void setListId(Long listId) { this.listId = listId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public static class Item {
        @NotNull private Long id;
        @NotNull private Integer version;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }
}
