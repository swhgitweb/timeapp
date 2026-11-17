package com.focus.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class AuthReq {
    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{3,50}$", message = "需为 3-50 位字母、数字或 _ . -")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(min = 1, max = 72, message = "长度需为 1-72 位")
    private String password;

    @Size(max = 50, message = "最长 50 字")
    private String displayName;

    private String timezone;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
