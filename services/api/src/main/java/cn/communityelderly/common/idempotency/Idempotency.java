package cn.communityelderly.common.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotency {

    /**
     * 是否强制要求客户端传递 Idempotency-Key 请求头
     * 为 false 时：若客户端传递了 Header 则强校验幂等防重；若未传则放行
     */
    boolean required() default false;

    /**
     * 幂等留存有效时间(秒)，默认 24 小时
     */
    int expireSeconds() default 86400;
}
