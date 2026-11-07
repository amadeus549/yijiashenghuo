package com.yijia.controller;

import com.yijia.dto.NoteCommentForm;
import com.yijia.dto.Result;
import com.yijia.service.INoteCommentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 笔记评价
 */
@RestController
@RequestMapping("/note-comments")
public class NoteCommentController {

    @Resource
    private INoteCommentService noteCommentService;

    /** 某篇笔记的评价列表，按时间倒序，每页 5 条 */
    @GetMapping("/of/note/{noteId}")
    public Result list(@PathVariable("noteId") Long noteId,
                       @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteCommentService.queryByNoteId(noteId, current);
    }

    /** 某个服务商名下所有笔记的评价 */
    @GetMapping("/of/provider/{providerId}")
    public Result byProvider(@PathVariable("providerId") Long providerId,
                             @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteCommentService.queryByProviderId(providerId, current);
    }

    /** 我发表过的评价 */
    @GetMapping("/of/user")
    public Result ofUser(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteCommentService.queryByUser(current);
    }

    /** 发布评价，parentId 不为 0 时是回复 */
    @PostMapping
    public Result add(@RequestBody NoteCommentForm form) {
        return noteCommentService.add(form);
    }

    /** 删除自己的评价 */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") Long id) {
        return noteCommentService.delete(id);
    }
}
