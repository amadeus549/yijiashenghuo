package com.yijia.service;

import com.yijia.dto.Result;
import com.yijia.entity.Note;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface INoteService extends IService<Note> {

    Result queryHotNote(Integer current);

    Result queryNoteById(Long id);

    Result queryNoteByUserId(Long userId, Integer current);

    Result likeNote(Long id);

    Result queryNoteLikes(Long id);

    Result saveNote(Note note);

    Result queryNoteOfFollow(Long max, Integer offset);
}
