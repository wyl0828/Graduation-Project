package cn.communityelderly.common.security;

import cn.communityelderly.common.exception.UnauthorizedException;

public final class UserContext {
    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(CurrentUser user) {
        CURRENT_USER.set(user);
    }

    public static CurrentUser get() {
        return CURRENT_USER.get();
    }

    public static CurrentUser requireCurrentUser() {
        CurrentUser user = CURRENT_USER.get();
        if (user == null) {
            throw new UnauthorizedException("未检测到有效登录身份");
        }
        return user;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
