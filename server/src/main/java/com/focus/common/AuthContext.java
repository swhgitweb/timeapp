package com.focus.common;

/** 当前请求用户；拦截器负责设置和清理，业务层统一从这里取得数据归属。 */
public final class AuthContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private AuthContext() {}

    public static void setUserId(Long userId) { USER_ID.set(userId); }

    public static Long userId() {
        Long userId = USER_ID.get();
        if (userId == null) throw BizException.unauthorized();
        return userId;
    }

    public static void clear() { USER_ID.remove(); }
}
