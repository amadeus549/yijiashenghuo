package com.yijia.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yijia.dto.Result;
import com.yijia.dto.UserDTO;
import com.yijia.entity.Follow;
import com.yijia.mapper.FollowMapper;
import com.yijia.service.IFollowService;
import com.yijia.service.IUserService;
import com.yijia.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yijia.utils.RedisConstants.FOLLOW_KEY;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        //不能关注自己，否则共同关注里会出现自己
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }
        //每个用户的关注列表放在一个 Set 里，key 形如 follows:1024
        String key = FOLLOW_KEY + userId;

        if (BooleanUtil.isTrue(isFollow)) {
            //目标用户必须存在，避免关注一个不存在的 id
            if (userService.getById(followUserId) == null) {
                return Result.fail("用户不存在");
            }
            //幂等：重复点关注不应该插入第二条记录
            long count = query()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId)
                    .count();
            if (count > 0) {
                return Result.ok();
            }
            boolean isSuccess = save(new Follow()
                    .setUserId(userId)
                    .setFollowUserId(followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            //数据库里没有记录也要清 Redis，避免取关后共同关注里还残留
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            if (!isSuccess) {
                log.debug("取关时数据库无记录，userId={}, followUserId={}", userId, followUserId);
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        //自己和自己没有"共同关注"的概念；目标用户不存在时直接返回空
        if (userId.equals(id) || userService.getById(id) == null) {
            return Result.ok(Collections.emptyList());
        }
        //历史数据、Redis 重启都会导致 Set 缺失，先按需从数据库回填
        ensureFollowSet(userId);
        ensureFollowSet(id);

        //两个 Set 求交集，在 Redis 内一次算完，不需要把两个列表取回 Java 再比
        Set<String> intersect = stringRedisTemplate.opsForSet()
                .intersect(FOLLOW_KEY + userId, FOLLOW_KEY + id);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //前端 other-info.html 直接把 data 当数组 v-for，这里必须返回数组
        return Result.ok(users);
    }

    /**
     * 保证指定用户的关注集合在 Redis 里存在。
     * 关注动作会同步写 Redis，但历史数据、Redis 重启后 Set 会丢失，
     * 这里在缓存缺失时从 t_follow 重建，避免"共同关注"永远是空的。
     */
    private void ensureFollowSet(Long userId) {
        String key = FOLLOW_KEY + userId;
        if (BooleanUtil.isTrue(stringRedisTemplate.hasKey(key))) {
            return;
        }
        List<Follow> follows = query().eq("user_id", userId).list();
        if (follows.isEmpty()) {
            return;
        }
        String[] ids = follows.stream()
                .map(follow -> follow.getFollowUserId().toString())
                .toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(key, ids);
        log.debug("从数据库回填关注集合，userId={}，数量={}", userId, ids.length);
    }
}
