package com.focus.controller;

import com.focus.common.CommonResult;
import com.focus.dto.AuthReq;
import com.focus.entity.AppUser;
import com.focus.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String SESSION_USER = "LOGIN_USER_ID";
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @GetMapping("/status")
    public CommonResult<Map<String, Object>> status(HttpSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("initialized", authService.initialized());
        Long userId = (Long) session.getAttribute(SESSION_USER);
        AppUser user = userId == null ? null : authService.currentUser(userId);
        result.put("authenticated", user != null);
        result.put("user", user == null ? null : authService.toVO(user));
        return CommonResult.ok(result);
    }

    @PostMapping("/initialize")
    public CommonResult<Map<String, Object>> initialize(@Valid @RequestBody AuthReq req, HttpSession session) {
        AppUser user = authService.initialize(req);
        session.setAttribute(SESSION_USER, user.getId());
        return CommonResult.ok(authService.toVO(user));
    }

    @PostMapping("/login")
    public CommonResult<Map<String, Object>> login(@Valid @RequestBody AuthReq req, HttpSession session) {
        AppUser user = authService.login(req);
        session.setAttribute(SESSION_USER, user.getId());
        return CommonResult.ok(authService.toVO(user));
    }

    @PostMapping("/logout")
    public CommonResult<Void> logout(HttpSession session) {
        session.invalidate();
        return CommonResult.ok();
    }

    @GetMapping("/me")
    public CommonResult<Map<String, Object>> me(HttpSession session) {
        return CommonResult.ok(authService.toVO(authService.requireUser((Long) session.getAttribute(SESSION_USER))));
    }
}
