package com.yijia.service;

import com.yijia.dto.NoteCommentForm;
import com.yijia.dto.Result;
import com.yijia.entity.NoteComment;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 笔记评价服务
 */
public interface INoteCommentService extends IService<NoteComment> {

    /** 分页查询某篇笔记的评价 */
    Result queryByNoteId(Long noteId, Integer current);

    /** 分页查询某个服务商名下所有笔记的评价 */
    Result queryByProviderId(Long providerId, Integer current);

    /** 我发表过的评价 */
    Result queryByUser(Integer current);

    /** 发布评价 / 回复 */
    Result add(NoteCommentForm form);

    /** 删除自己的评价 */
    Result delete(Long id);
}
