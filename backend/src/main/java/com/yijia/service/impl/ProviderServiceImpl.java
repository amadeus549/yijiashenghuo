package com.yijia.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yijia.dto.Result;
import com.yijia.entity.Provider;
import com.yijia.mapper.ProviderMapper;
import com.yijia.service.IProviderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yijia.utils.CacheClient;
import com.yijia.utils.RedisConstants;
import com.yijia.utils.RedisData;
import com.yijia.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yijia.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ProviderServiceImpl extends ServiceImpl<ProviderMapper, Provider> implements IProviderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Provider provider = cacheClient.queryWithPassThrough(CACHE_PROVIDER_KEY, id, Provider.class, this::getById, CACHE_PROVIDER_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Provider provider = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Provider provider = cacheClient.queryWithLogicalExpire(CACHE_PROVIDER_KEY, id, Provider.class, this::getById, CACHE_PROVIDER_TTL, TimeUnit.MINUTES);
        if(provider == null){
            return Result.fail("服务商不存在");
        }


        return Result.ok(provider);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑过期解决缓存击穿
    /*public Provider queryWithLogicalExpire(Long id){
        String key = CACHE_PROVIDER_KEY + id;
        //从redis查询服务商缓存
        String providerJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(providerJson)) {
            //存在，直接返回
            RedisData redisData = JSONUtil.toBean(providerJson, RedisData.class);
            Provider provider = JSONUtil.toBean((JSONObject) redisData.getData(), Provider.class);
            LocalDateTime expireTime = redisData.getExpireTime();
            //判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                //未过期，直接返回服务商信息
                return provider;
            }
            //已过期，需要缓存重建
            String lockKey = RedisConstants.LOCK_PROVIDER_KEY + id;
            boolean islock = tryLock(lockKey);
            if (islock) {
                //获取锁成功，开启独立线程，重建缓存
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        //重建缓存
                        this.saveProvider2Redis(id, 20L);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }finally {
                        //释放锁
                        unLock(lockKey);
                    }
                });
            }
        }
        return null;
    }*/

    //互斥锁解决缓存击穿
    /*public Provider queryWithMutex(Long id){
        String key = CACHE_PROVIDER_KEY + id;
        //从redis查询服务商缓存
        String providerJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(providerJson)){
            //存在，直接返回
            Provider provider = JSONUtil.toBean(providerJson,Provider.class);
            return  provider;
        }
        //判断命中的是否为空值
        if (providerJson != null){
            //存在，直接返回
            return  null;
        }
        //判断锁是否获取成功
        String lockKey = null;
        Provider provider = null;
        try {
            lockKey = RedisConstants.LOCK_PROVIDER_KEY +id;
            boolean islock = tryLock(lockKey);
            if (!islock){
                //获取锁失败，休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功，根据id查询数据库
            provider = getById(id);
            //不存在，返回错误
            if(provider == null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return  null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(provider),
                    CACHE_SERVICE_TYPE_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放锁
            unLock(lockKey);
        }

        return provider;
    }*/

    //缓存穿透
    /*public Provider queryWithPassThrough(Long id){
        String key = CACHE_PROVIDER_KEY + id;
        //从redis查询服务商缓存
        String providerJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(providerJson)){
            //存在，直接返回
            Provider provider = JSONUtil.toBean(providerJson,Provider.class);
            return  provider;
        }
        //判断命中的是否为空值
        if (providerJson != null){
            //存在，直接返回
            return  null;
        }
        //不存在，根据id查询数据库
        Provider provider =getById(id);

        //不存在，返回错误
        if(provider == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return  null;
        }
        //存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(provider), CACHE_SERVICE_TYPE_TTL, TimeUnit.MINUTES);

        return provider;
    }*/


    /*//获取锁
    private boolean tryLock(String key){
    // 使用Redis的setIfAbsent方法尝试设置键值对，如果键不存在则设置成功，返回true；如果键已存在则设置失败，返回false
    // 同时设置键的过期时间，防止死锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_PROVIDER_TTL, TimeUnit.SECONDS);
    // 将Boolean结果转换为基本boolean类型，避免可能的NPE
        return Boolean.TRUE.equals(flag);
    }

    //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveProvider2Redis(Long id, Long expireSeconds){
        //查询服务商数据
        Provider provider = getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(provider);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_PROVIDER_KEY + id,
                JSONUtil.toJsonStr(redisData));
    }*/

    @Override
    @Transactional
    public Result update(Provider provider) {
        Long id = provider.getId();
        if(id == null){
            return Result.fail("服务商id不能为空");
        }
        //更新数据库
        updateById(provider);
        //删除缓存
        stringRedisTemplate.delete(CACHE_PROVIDER_KEY + provider.getId());
        //坐标或类型可能改了，同步 GEO，避免附近服务商里还停在老位置
        saveGeo(provider);
        return Result.ok();
    }

    @Override
    public Result saveProvider(Provider provider) {
        if (provider.getTypeId() == null) {
            return Result.fail("服务商类型不能为空");
        }
        save(provider);
        saveGeo(provider);
        return Result.ok(provider.getId());
    }

    @Override
    public Result queryProviderByType(Integer typeId, Integer current, Double x, Double y) {
        //1.没有坐标就退化成普通分页，不传 x、y 时接口行为和以前完全一致
        if (x == null || y == null) {
            return Result.ok(pageProviderByType(typeId, current));
        }

        //2.分页参数：GEO 的 COUNT 不支持 offset，先取到 end，再在内存里跳过前 from 条
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //3.先确认这个类型有没有坐标数据：没有才降级，5km 内查不到属于正常结果
        String key = PROVIDER_GEO_KEY + typeId;
        Long geoCount = stringRedisTemplate.opsForZSet().zCard(key);
        if (geoCount == null || geoCount == 0) {
            log.debug("Redis 中没有服务商坐标，typeId={}，退化为数据库分页", typeId);
            return Result.ok(pageProviderByType(typeId, current));
        }

        //4.以 (x,y) 为圆心、10km 为半径查询，按距离由近到远排序
        //  到家服务的技师通勤半径通常比到店餐饮大，5km 会把大部分服务商挡在外面
        Circle circle = new Circle(new Point(x, y), new Distance(10000));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeDistance()
                .sortAscending()
                .limit(end);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                stringRedisTemplate.opsForGeo().radius(key, circle, args);

        //5.坐标数据在、但 5km 内没有服务商，返回空列表而不是全部服务商
        if (results == null || results.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        //6.当前页已经超出查询范围
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        //7.截取当前页，同时记下每个服务商到当前位置的距离（单位米）
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Double> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String providerId = result.getContent().getName();
            ids.add(Long.valueOf(providerId));
            distanceMap.put(providerId, result.getDistance().getValue());
        });

        //8.按 id 查服务商，用 FIELD 保持 GEO 返回的由近到远顺序
        List<Provider> providers = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + StrUtil.join(",", ids) + ")")
                .list();
        //9.距离塞进实体，前端用 s.distance 显示 xx m / xx km
        providers.forEach(provider -> provider.setDistance(distanceMap.get(provider.getId().toString())));
        return Result.ok(providers);
    }

    @Override
    public void loadProviderGeoData() {
        try {
            List<Provider> providers = query()
                    .isNotNull("x")
                    .isNotNull("y")
                    .list();
            Map<Long, List<Provider>> byType = providers.stream()
                    .collect(Collectors.groupingBy(Provider::getTypeId));
            for (Map.Entry<Long, List<Provider>> entry : byType.entrySet()) {
                String key = PROVIDER_GEO_KEY + entry.getKey();
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                    //已经有坐标数据就不覆盖，避免把运行期间新增的服务商冲掉
                    continue;
                }
                List<RedisGeoCommands.GeoLocation<String>> locations = entry.getValue().stream()
                        .map(provider -> new RedisGeoCommands.GeoLocation<>(
                                provider.getId().toString(),
                                new Point(provider.getX(), provider.getY())))
                        .collect(Collectors.toList());
                stringRedisTemplate.opsForGeo().add(key, locations);
                log.info("服务商坐标写入 Redis，typeId={}，数量={}", entry.getKey(), locations.size());
            }
        } catch (Exception e) {
            //坐标初始化失败不影响应用启动，附近服务商会退化为普通分页
            log.error("服务商坐标初始化失败，附近服务商将退化为数据库分页", e);
        }
    }

    /**
     * 把单个服务商的坐标同步到 Redis GEO，坐标或类型为空时跳过
     */
    private void saveGeo(Provider provider) {
        if (provider.getTypeId() == null || provider.getX() == null || provider.getY() == null) {
            return;
        }
        stringRedisTemplate.opsForGeo().add(
                PROVIDER_GEO_KEY + provider.getTypeId(),
                new Point(provider.getX(), provider.getY()),
                provider.getId().toString());
    }

    /**
     * 不带坐标时的普通分页查询
     */
    private List<Provider> pageProviderByType(Integer typeId, Integer current) {
        Page<Provider> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return page.getRecords();
    }
}
