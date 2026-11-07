package com.yijia.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yijia.entity.Provider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.yijia.utils.RedisConstants.*;

@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存穿透
    public <R , ID> R queryWithPassThrough(String keyPrefix , ID id , Class<R> type,
                                           Function<ID, R> dbFallback,Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis查询服务商缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(json)){
            //存在，直接返回
            return  JSONUtil.toBean(json,type);
        }
        //判断命中的是否为空值
        if (json != null){
            //存在，直接返回
            return  null;
        }
        //不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        //不存在，返回错误
        if(r == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return  null;
        }
        //存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);

        return r;
    }


    //缓存击穿
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑过期解决缓存击穿
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type
            , Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(json)) {
            // 空字符串是「回源确认过不存在」的标记，直接返回，防止缓存穿透
            if (json != null) {
                return null;
            }
            // 缓存缺失（冷启动 / 被淘汰）：同步回源一次并写入缓存。
            // 逻辑过期方案的前提是 key 一定存在，不做这一步会把正常数据误判成「不存在」
            R dbData = dbFallback.apply(id);
            if (dbData == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.setWithLogicalExpire(key, dbData, time, unit);
            return dbData;
        }

        // 1. 将 json 解析为 RedisData 对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);

        // 2. 判空防护：防止解析失败，或者里面存的是旧格式的脏数据
        if (redisData == null || redisData.getData() == null) {
            return null;
        }

        // 3. 获取真正的数据对象（直接转换即可，不需要中间转成字符串再转回来）
        R r = JSONUtil.toBean(JSONUtil.parseObj(redisData.getData()), type);

        // 4. 获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5. 判空防护：防止 expireTime 为 null 导致下面报空指针
        if (expireTime != null && expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回
            return r;
        }

        //已过期，需要缓存重建
        String lockKey = RedisConstants.LOCK_PROVIDER_KEY + id;
        boolean islock = tryLock(lockKey);
        if (islock) {
            //获取锁成功，开启独立线程，重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //无论是否获取到锁，都返回旧的缓存数据兜底
        return r;
    }

    //获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_PROVIDER_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

        //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


}
