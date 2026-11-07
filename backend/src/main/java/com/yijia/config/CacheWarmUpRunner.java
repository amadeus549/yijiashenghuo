package com.yijia.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yijia.entity.Provider;
import com.yijia.mapper.ProviderMapper;
import com.yijia.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.yijia.utils.RedisConstants.CACHE_PROVIDER_KEY;
import static com.yijia.utils.RedisConstants.CACHE_PROVIDER_TTL;

/**
 * 启动时把热门服务商写入 Redis（逻辑过期），避免冷启动瞬间大量请求直接击穿到数据库。
 * 预热失败不影响应用启动，只是少了一层保护。
 */
@Slf4j
@Component
public class CacheWarmUpRunner implements ApplicationRunner {

    /** 预热条数：按销量取前 N 个，覆盖首页与列表页的热门入口 */
    private static final int WARM_UP_LIMIT = 200;

    @Resource
    private ProviderMapper providerMapper;
    @Resource
    private CacheClient cacheClient;

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        try {
            List<Provider> providers = providerMapper.selectList(
                    new LambdaQueryWrapper<Provider>()
                            .orderByDesc(Provider::getSold)
                            .last("limit " + WARM_UP_LIMIT));
            for (Provider provider : providers) {
                cacheClient.setWithLogicalExpire(
                        CACHE_PROVIDER_KEY + provider.getId(),
                        provider,
                        CACHE_PROVIDER_TTL,
                        TimeUnit.MINUTES);
            }
            log.info("缓存预热完成：{} 个服务商已写入 Redis，耗时 {} ms",
                    providers.size(), System.currentTimeMillis() - start);
        } catch (Throwable e) {
            // 预热只是加速手段，任何异常（含 JDK 高版本下 LambdaWrapper 的 InaccessibleObjectException）
            // 都不应该把应用拖死；这里兜住 Throwable，Error 也一样。
            log.warn("缓存预热失败，不影响启动：{}。若为 InaccessibleObjectException，"
                    + "请在启动参数加 --add-opens java.base/java.lang.invoke=ALL-UNNAMED", e.toString());
        }
    }
}
