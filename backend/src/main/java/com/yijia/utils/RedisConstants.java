package com.yijia.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_PROVIDER_TTL = 30L;
    public static final String CACHE_PROVIDER_KEY = "cache:provider:";

    public static final String CACHE_SERVICE_TYPE_KEY = "cache:serviceType:";
    public static final Long CACHE_SERVICE_TYPE_TTL = 30L;

    public static final String LOCK_PROVIDER_KEY = "lock:provider:";
    public static final Long LOCK_PROVIDER_TTL = 10L;

    public static final String FLASH_STOCK_KEY = "flash:stock:";
    /** 已下单用户集合，与 flash.lua 里的 orderKey 保持一致 */
    public static final String FLASH_ORDER_KEY = "flash:order:";
    /** 验证码发送频控：同一手机号 60 秒一次 */
    public static final String LOGIN_CODE_LIMIT_KEY = "login:code:limit:";
    public static final Long LOGIN_CODE_LIMIT_TTL = 60L;
    public static final String NOTE_LIKED_KEY = "note:liked:";
    public static final String FOLLOW_KEY = "follows:";
    public static final String FEED_KEY = "feed:";
    public static final String PROVIDER_GEO_KEY = "provider:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    public static final String UV_KEY = "uv:";
    public static final String UV_RANGE_KEY = "uv:range:";
}
