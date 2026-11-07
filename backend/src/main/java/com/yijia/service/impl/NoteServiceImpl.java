package com.yijia.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yijia.dto.Result;
import com.yijia.dto.ScrollResult;
import com.yijia.dto.UserDTO;
import com.yijia.entity.Note;
import com.yijia.entity.Follow;
import com.yijia.entity.User;
import com.yijia.mapper.NoteMapper;
import com.yijia.service.INoteService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yijia.service.IFollowService;
import com.yijia.service.IUserService;
import com.yijia.utils.SystemConstants;
import com.yijia.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yijia.utils.RedisConstants.NOTE_LIKED_KEY;
import static com.yijia.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements INoteService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotNote(Integer current) {
        Page<Note> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Note> records = page.getRecords();

        records.forEach(note -> {
            this.queryNoteUser(note);
            this.isNoteLiked(note);
        });
        //前端首页直接对 data 调用 forEach，所以这里必须返回 records 数组，
        //返回 Page 对象会让前端拿到 {records:[...]}，forEach 不是函数，列表渲染不出来
        return Result.ok(records);
    }

    @Override
    public Result queryNoteById(Long id) {
        //查询note
        Note note = getById(id);
        //查询note作者
        if (note == null) {
            return Result.fail("博客不存在");
        }
        queryNoteUser(note);
        //查询note是否被点过赞
        isNoteLiked(note);

        return Result.ok(note);
    }

    @Override
    public Result queryNoteByUserId(Long userId, Integer current) {
        Page<Note> page = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //other-info.html 直接对 data 做 v-for，所以返回数组而不是 Page
        return Result.ok(page.getRecords());
    }

    private void isNoteLiked(Note note) {
        // /note/hot 在拦截器白名单里，未登录时 UserHolder 里没有用户，直接取 id 会空指针
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = user.getId();

        String key = NOTE_LIKED_KEY + note.getId();
        //StringRedisTemplate 的 value 序列化器只接受 String，传 Long 会 ClassCastException
        Double  score= stringRedisTemplate.opsForZSet().score(key, userId.toString());
        note.setIsLike(score != null);
    }

    @Override
    public Result likeNote(Long id) {
        Long userId = UserHolder.getUser().getId();

        String key = NOTE_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if(score == null){
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(), System.currentTimeMillis());
            }
        }else{
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryNoteLikes(Long id) {
        String key = NOTE_LIKED_KEY + id;
        //查询top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idString = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idString + ")").list()
                .stream().map(user -> {
                    UserDTO userDTO = new UserDTO();
                    BeanUtils.copyProperties(user, userDTO);
                    return userDTO;
                }).collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveNote(Note note) {
        UserDTO user = UserHolder.getUser();
        note.setUserId(user.getId());
        boolean isSuccess = save(note);
        if (!isSuccess) {
            return Result.fail("新增博客失败");
        }
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            String key = FEED_KEY + note.getId();
            stringRedisTemplate.opsForZSet().add(key, note.getId().toString(), System.currentTimeMillis());

        }

        return Result.ok(note.getId());
    }

    @Override
    public Result queryNoteOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples){
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));
            long time = tuple.getScore().longValue();
            if(minTime == time){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        List<Note> notes = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Note note : notes) {
            queryNoteUser(note);
            isNoteLiked(note);
        }
        ScrollResult r = new ScrollResult();
        r.setList(notes);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    private void queryNoteUser(Note note){
        Long userId = note.getUserId();
        User user = userService.getById(userId);
        //作者可能已被删除，避免空指针
        if (user == null) {
            return;
        }
        note.setName(user.getNickName());
        note.setIcon(user.getIcon());
    }
}
