package com.yijia.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yijia.dto.Result;
import com.yijia.entity.ServiceType;
import com.yijia.mapper.ServiceTypeMapper;
import com.yijia.service.IServiceTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.yijia.utils.RedisConstants.CACHE_SERVICE_TYPE_KEY;
import static com.yijia.utils.RedisConstants.CACHE_SERVICE_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ServiceTypeServiceImpl extends ServiceImpl<ServiceTypeMapper, ServiceType> implements IServiceTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.从redis查询服务商类型缓存
        String key = CACHE_SERVICE_TYPE_KEY;
        String typeListJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(typeListJson)) {
            // 3.命中，把JSON反序列化成List直接返回
            List<ServiceType> typeList = JSONUtil.toList(typeListJson, ServiceType.class);
            return Result.ok(typeList);
        }

        // 4.未命中，查询数据库（按 sort 升序）
        List<ServiceType> typeList = query().orderByAsc("sort").list();

        // 5.数据库也没有，返回错误信息
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("服务商类型不存在");
        }

        // 6.存在，写入redis，并设置过期时间
        stringRedisTemplate.opsForValue()
                .set(key, JSONUtil.toJsonStr(typeList), CACHE_SERVICE_TYPE_TTL, TimeUnit.MINUTES);

        // 7.返回结果
        return Result.ok(typeList);
    }
}
