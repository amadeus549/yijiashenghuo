package com.yijia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yijia.dto.NoteCommentForm;
import com.yijia.dto.NoteCommentVO;
import com.yijia.dto.Result;
import com.yijia.dto.UserDTO;
import com.yijia.entity.Note;
import com.yijia.entity.NoteComment;
import com.yijia.entity.User;
import com.yijia.mapper.NoteCommentMapper;
import com.yijia.mapper.NoteMapper;
import com.yijia.mapper.UserMapper;
import com.yijia.service.INoteCommentService;
import com.yijia.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔记评价：查询时一次性把评论人和被回复人查出来，避免 N+1。
 */
@Service
public class NoteCommentServiceImpl extends ServiceImpl<NoteCommentMapper, NoteComment>
        implements INoteCommentService {

    /** 每页条数，注释页只展示最近几条，点「查看全部」再翻页 */
    private static final int PAGE_SIZE = 5;
    /** 同一用户两次评价的最小间隔（秒） */
    private static final long COMMENT_INTERVAL_SECONDS = 5L;
    private static final String COMMENT_LIMIT_KEY = "comment:limit:";
    private static final int MAX_CONTENT_LENGTH = 200;

    @Resource
    private NoteMapper noteMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByNoteId(Long noteId, Integer current) {
        if (noteId == null) {
            return Result.fail("笔记不存在");
        }
        int pageNo = (current == null || current < 1) ? 1 : current;
        Page<NoteComment> page = lambdaQuery()
                .eq(NoteComment::getNoteId, noteId)
                .orderByDesc(NoteComment::getCreateTime)
                .page(new Page<>(pageNo, PAGE_SIZE));
        return Result.ok(toVOList(page.getRecords()), page.getTotal());
    }

    @Override
    public Result queryByProviderId(Long providerId, Integer current) {
        if (providerId == null) {
            return Result.fail("服务商不存在");
        }
        // 先拿到该服务商名下所有笔记 id，再按 noteId 批量取评价
        List<Long> noteIds = noteMapper.selectList(new LambdaQueryWrapper<Note>()
                        .eq(Note::getProviderId, providerId)
                        .select(Note::getId))
                .stream().map(Note::getId).collect(Collectors.toList());
        if (noteIds.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }
        int pageNo = (current == null || current < 1) ? 1 : current;
        Page<NoteComment> page = lambdaQuery()
                .in(NoteComment::getNoteId, noteIds)
                .orderByDesc(NoteComment::getCreateTime)
                .page(new Page<>(pageNo, PAGE_SIZE));
        return Result.ok(toVOList(page.getRecords()), page.getTotal());
    }

    @Override
    public Result queryByUser(Integer current) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        int pageNo = (current == null || current < 1) ? 1 : current;
        Page<NoteComment> page = lambdaQuery()
                .eq(NoteComment::getUserId, currentUser.getId())
                .orderByDesc(NoteComment::getCreateTime)
                .page(new Page<>(pageNo, PAGE_SIZE));
        List<NoteCommentVO> vos = toVOList(page.getRecords());
        // 补上笔记标题，前端要知道这条评价是针对哪篇笔记
        if (!vos.isEmpty()) {
            Set<Long> noteIds = vos.stream().map(NoteCommentVO::getNoteId).collect(Collectors.toSet());
            Map<Long, String> titleMap = noteMapper.selectBatchIds(noteIds).stream()
                    .collect(Collectors.toMap(Note::getId, Note::getTitle, (a, b) -> a));
            vos.forEach(vo -> vo.setNoteTitle(titleMap.get(vo.getNoteId())));
        }
        return Result.ok(vos, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result add(NoteCommentForm form) {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("请先登录");
        }
        if (form == null || form.getNoteId() == null) {
            return Result.fail("笔记不存在");
        }
        String content = form.getContent() == null ? "" : form.getContent().trim();
        if (content.isEmpty()) {
            return Result.fail("评价内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return Result.fail("评价内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        Note note = noteMapper.selectById(form.getNoteId());
        if (note == null) {
            return Result.fail("笔记不存在");
        }

        // 频控：SETNX 5 秒过期，等价于一把自动释放的锁，挡住连点
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(COMMENT_LIMIT_KEY + current.getId(), "1",
                        COMMENT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            return Result.fail("评论太频繁，请稍后再试");
        }

        NoteComment comment = new NoteComment();
        comment.setNoteId(form.getNoteId());
        comment.setUserId(current.getId());
        comment.setContent(content);
        comment.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        comment.setAnswerId(form.getAnswerId() == null ? 0L : form.getAnswerId());
        comment.setLiked(0);
        comment.setStatus(Boolean.TRUE);
        save(comment);

        // 评价数落到笔记表，详情页不用每次 count(*)
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, form.getNoteId())
                .setSql("comments = comments + 1"));

        return Result.ok(toVO(comment));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result delete(Long id) {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("请先登录");
        }
        NoteComment comment = getById(id);
        if (comment == null) {
            return Result.fail("评价不存在");
        }
        if (!comment.getUserId().equals(current.getId())) {
            return Result.fail("只能删除自己的评价");
        }
        removeById(id);
        // 挂在它下面的回复一起删掉，避免留下没有父级的孤儿数据
        remove(new LambdaQueryWrapper<NoteComment>().eq(NoteComment::getParentId, id));
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, comment.getNoteId())
                .gt(Note::getComments, 0)
                .setSql("comments = comments - 1"));
        return Result.ok();
    }

    private List<NoteCommentVO> toVOList(List<NoteComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new HashSet<>();
        for (NoteComment c : comments) {
            userIds.add(c.getUserId());
            if (c.getAnswerId() != null && c.getAnswerId() > 0) {
                userIds.add(c.getAnswerId());
            }
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        UserDTO current = UserHolder.getUser();
        return comments.stream().map(c -> toVO(c, userMap, current)).collect(Collectors.toList());
    }

    private NoteCommentVO toVO(NoteComment comment) {
        User user = userMapper.selectById(comment.getUserId());
        Long answerId = comment.getAnswerId();
        User answer = (answerId != null && answerId > 0) ? userMapper.selectById(answerId) : null;
        NoteCommentVO vo = new NoteCommentVO();
        fill(vo, comment, user, answer, UserHolder.getUser());
        return vo;
    }

    private NoteCommentVO toVO(NoteComment comment, Map<Long, User> userMap, UserDTO current) {
        User user = userMap.get(comment.getUserId());
        Long answerId = comment.getAnswerId();
        User answer = (answerId != null && answerId > 0) ? userMap.get(answerId) : null;
        NoteCommentVO vo = new NoteCommentVO();
        fill(vo, comment, user, answer, current);
        return vo;
    }

    private void fill(NoteCommentVO vo, NoteComment comment, User user, User answer, UserDTO current) {
        vo.setId(comment.getId());
        vo.setNoteId(comment.getNoteId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setAnswerId(comment.getAnswerId());
        vo.setContent(comment.getContent());
        vo.setLiked(comment.getLiked() == null ? 0 : comment.getLiked());
        vo.setCreateTime(comment.getCreateTime());
        vo.setName(user == null ? "已注销用户" : user.getNickName());
        vo.setIcon(user == null ? "" : user.getIcon());
        vo.setAnswerName(answer == null ? null : answer.getNickName());
        vo.setMine(current != null && current.getId().equals(comment.getUserId()));
    }
}
