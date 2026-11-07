package com.yijia.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记评价的展示对象：评论本身 + 评论人信息（避免前端二次请求）
 */
@Data
public class NoteCommentVO {

    private Long id;
    private Long noteId;
    private Long userId;
    private Long parentId;
    private Long answerId;
    private String content;
    private Integer liked;
    private LocalDateTime createTime;

    /** 评论人昵称 / 头像 */
    private String name;
    private String icon;

    /** 被回复人昵称，一级评论为空 */
    private String answerName;

    /** 这条评价挂在哪个笔记下（"我的评价"列表要显示） */
    private String noteTitle;

    /** 是否是自己发的，前端据此显示删除按钮 */
    private Boolean mine;
}
