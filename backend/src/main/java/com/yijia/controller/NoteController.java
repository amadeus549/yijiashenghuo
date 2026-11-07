package com.yijia.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yijia.dto.Result;
import com.yijia.dto.UserDTO;
import com.yijia.entity.Note;
import com.yijia.entity.User;
import com.yijia.service.INoteService;
import com.yijia.service.IUserService;
import com.yijia.utils.SystemConstants;
import com.yijia.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/note")
public class NoteController {

    @Resource
    private INoteService noteService;

    @PostMapping
    public Result saveNote(@RequestBody Note note) {
        return noteService.saveNote(note);
    }

    @PutMapping("/like/{id}")
    public Result likeNote(@PathVariable("id") Long id) {

        return noteService.likeNote(id);
    }

    @GetMapping("/of/me")
    public Result queryMyNote(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Note> page = noteService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Note> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotNote(@RequestParam(value = "current", defaultValue = "1") Integer current) {
       return noteService.queryHotNote(current);
    }


    @GetMapping("/{id}")
    public Result queryNoteById(@PathVariable("id")Long id){
        return noteService.queryNoteById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryNoteLikes(@PathVariable("id")Long id){
        return noteService.queryNoteLikes(id);
    }

    @GetMapping("/of/user")
    public Result queryNoteByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 他人主页的"笔记"列表，业务逻辑放在 Service 层
        return noteService.queryNoteByUserId(id, current);
    }

    @GetMapping("/of/follow")
    public Result queryNoteOfFollow(@RequestParam("lastId") Long max,@RequestParam(value = "offset",defaultValue = "0") Integer offset) {

        return noteService.queryNoteOfFollow(max,offset);
    }
}
