package com.yijia.dto;

import lombok.Data;

/**
 * 发布评价的请求体
 */
@Data
public class NoteCommentForm {

    private Long noteId;

    private String content;

    /** 一级评论传 0，回复某条评论时传该评论 id */
    private Long parentId;

    /** 回复的目标用户，一级评论可为空 */
    private Long answerId;
}
