package com.focus.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class SubscriptionReq {
    @NotBlank @Size(max = 2000) private String url;
    @NotBlank @Size(max = 100) private String name;
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$") private String color;
    private Boolean enabled;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
