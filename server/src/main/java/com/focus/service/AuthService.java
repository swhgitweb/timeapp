package com.focus.service;

import com.focus.common.BizException;
import com.focus.dto.AuthReq;
import com.focus.entity.AppUser;
import com.focus.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper) { this.userMapper = userMapper; }

    public boolean initialized() { return userMapper.count() > 0; }

    @Transactional
    public synchronized AppUser initialize(AuthReq req) {
        if (initialized()) throw BizException.badRequest("账号已初始化，请直接登录");
        validateTimezone(req.getTimezone());
        AppUser user = new AppUser();
        user.setUsername(req.getUsername().trim());
        user.setPasswordHash(encoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName() == null || req.getDisplayName().trim().isEmpty()
                ? user.getUsername() : req.getDisplayName().trim());
        user.setTimezone(req.getTimezone() == null || req.getTimezone().trim().isEmpty()
                ? "Asia/Shanghai" : req.getTimezone().trim());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw BizException.badRequest("账号已初始化，请直接登录");
        }
        userMapper.claimLegacySessions(user.getId());
        return user;
    }

    public AppUser login(AuthReq req) {
        AppUser user = userMapper.selectByUsername(req.getUsername().trim());
        if (user == null || !encoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BizException.badRequest("用户名或密码错误");
        }
        return user;
    }

    public AppUser requireUser(Long id) {
        AppUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) throw BizException.unauthorized();
        return user;
    }

    public AppUser currentUser(Long id) { return requireUser(id); }

    public ZoneId zoneOf(Long userId) {
        return ZoneId.of(requireUser(userId).getTimezone());
    }

    public Map<String, Object> toVO(AppUser user) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", user.getId());
        vo.put("username", user.getUsername());
        vo.put("displayName", user.getDisplayName());
        vo.put("timezone", user.getTimezone());
        return vo;
    }

    private void validateTimezone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) return;
        try { ZoneId.of(timezone.trim()); }
        catch (DateTimeException e) { throw BizException.badRequest("无效时区"); }
    }
}
