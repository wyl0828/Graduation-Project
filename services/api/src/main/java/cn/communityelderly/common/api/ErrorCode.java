package cn.communityelderly.common.api;

public enum ErrorCode {
    // 成功
    OK(200, "操作成功"),

    // 客户端基础错误 (400 / 422)
    BAD_REQUEST(400, "请求参数格式错误"),
    VALIDATION_FAILED(422, "参数校验未通过"),
    VERIFICATION_FAILED(422, "手机号验证失败、过期或已失效"),
    ACTIVATION_CODE_INVALID(400, "激活码错误或不匹配"),

    // 认证与会话 (401)
    AUTHENTICATION_REQUIRED(401, "请先登录系统"),
    INVALID_CREDENTIALS(401, "手机号或密码错误"),
    SESSION_EXPIRED(401, "登录会话已过期，请重新登录"),
    ACCOUNT_LOCKED(401, "账号已被风控锁定，请稍后重试"),
    AUTH_COOLDOWN(429, "登录尝试过于频繁，请稍后重试"),
    AUTH_CONFIGURATION_UNAVAILABLE(503, "登录安全配置暂不可用，请联系系统管理员"),
    PHONE_VERIFICATION_UNAVAILABLE(503, "当前未接入可信手机号验证码渠道，注册与手机号验证暂不可用"),

    // 授权与权限 (403)
    ACCESS_DENIED(403, "当前角色无权访问该资源"),
    FORBIDDEN_ACCESS(403, "当前操作无权执行"),
    FORBIDDEN_SELF_REVIEW(403, "操作员不能审核自己执行结案的工单"),
    USER_DISABLED(403, "账号已停用，请联系社区管理员"),
    COMMUNITY_SCOPE_MISMATCH(403, "超出管辖社区数据范围"),
    CSRF_VALIDATION_FAILED(403, "Web 会话安全校验失败，请刷新后重试"),
    KINSHIP_NOT_ACTIVE(403, "亲情授权未生效或已被冻结"),

    // 资源不存在 (404 / 防枚举)
    RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE(404, "请求的资源不存在或无权查看"),
    USER_NOT_FOUND(404, "用户不存在或手机号未注册"),
    RECORD_NOT_FOUND(404, "指定的业务记录不存在"),

    // 冲突与并发 (409)
    OPTIMISTIC_LOCK_CONFLICT(409, "数据已被其他操作更新，请刷新重试"),
    FIELD_VALUE_CONFLICT(409, "老人档案在此期间已被更新，基线快照已失效"),
    IDEMPOTENCY_KEY_REUSED(409, "幂等键已被使用且请求内容不一致"),
    PHONE_NUMBER_EXISTS(409, "该手机号已被注册，请直接登录"),
    RECOVERY_UNAVAILABLE(503, "当前未配置可验证手机号控制权的恢复渠道，请联系有权管理员核验"),
    ACTIVATION_CODE_EXPIRED(409, "激活码已超过24小时有效期"),
    ACTIVATION_CODE_LOCKED(409, "连续输错次数超限，激活码已锁定"),
    ACTIVATION_CODE_USED(409, "该激活码已被消费使用"),
    RESERVATION_SLOT_CONFLICT(409, "老人该时段已预约过同类服务"),
    ORDER_STATUS_INVALID(409, "工单当前流转状态不支持此操作"),
    KINSHIP_UNDER_DISPUTE(409, "亲情关系正处于争议冻结状态，冻结期内禁止重新申请"),
    DUPLICATE_OPERATION(409, "重复操作已被拦截"),

    // 系统内部错误 (500)
    IDEMPOTENCY_UNAVAILABLE(503, "幂等保护暂不可用，请稍后重试"),
    INTERNAL_SERVER_ERROR(500, "系统繁忙，请稍后重试");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
