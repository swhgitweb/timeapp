package com.focus.entity;

public class AppUser {
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String timezone;
    private String icsToken;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getIcsToken() { return icsToken; }
    public void setIcsToken(String icsToken) { this.icsToken = icsToken; }
}
