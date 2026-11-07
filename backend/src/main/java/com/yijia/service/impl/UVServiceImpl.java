package com.yijia.service.impl;

import com.yijia.service.IUVService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.yijia.utils.RedisConstants.UV_KEY;
import static com.yijia.utils.RedisConstants.UV_RANGE_KEY;

/**
 * <p>
 * UV 统计实现：HyperLogLog
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UVServiceImpl implements IUVService {

    /** 日期统一格式，既是 key 的后缀，也是接口入参格式 */
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 区间合并出来的临时 key 只留一小时，没必要长期占空间 */
    private static final long RANGE_KEY_TTL_HOURS = 1L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void record(Long userId) {
        if (userId == null) {
            return;
        }
        //一天一个 key：uv:2026-11-04，PFADD 本身幂等，重复访问不会算两次
        String key = UV_KEY + LocalDate.now().format(DATE_FORMAT);
        stringRedisTemplate.opsForHyperLogLog().add(key, userId.toString());
    }

    @Override
    public long todayCount() {
        return countByDate(LocalDate.now().format(DATE_FORMAT));
    }

    @Override
    public long countByDate(String date) {
        //key 不存在时 PFCOUNT 返回 0，size 不会返回 null，这里仍兜一层
        Long size = stringRedisTemplate.opsForHyperLogLog().size(UV_KEY + date);
        return size == null ? 0L : size;
    }

    @Override
    public long countRange(String start, String end) {
        LocalDate from = LocalDate.parse(start, DATE_FORMAT);
        LocalDate to = LocalDate.parse(end, DATE_FORMAT);
        //允许传反，自动纠正
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        List<String> keys = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            keys.add(UV_KEY + day.format(DATE_FORMAT));
        }
        if (keys.isEmpty()) {
            return 0L;
        }
        //PFMERGE 把多天合成一个 HLL，再 PFCOUNT，跨天访问的同一个人只算一次
        String destKey = UV_RANGE_KEY + start + ":" + end;
        stringRedisTemplate.opsForHyperLogLog().union(destKey, keys.toArray(new String[0]));
        Long size = stringRedisTemplate.opsForHyperLogLog().size(destKey);
        stringRedisTemplate.expire(destKey, RANGE_KEY_TTL_HOURS, TimeUnit.HOURS);
        return size == null ? 0L : size;
    }
}
