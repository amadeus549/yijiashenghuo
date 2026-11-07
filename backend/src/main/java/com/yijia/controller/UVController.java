package com.yijia.controller;

import com.yijia.dto.Result;
import com.yijia.dto.UserDTO;
import com.yijia.service.IUVService;
import com.yijia.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * <p>
 * UV 统计接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/uv")
public class UVController {

    @Resource
    private IUVService uvService;

    /**
     * 手动打点：把当前登录用户记入今天的 UV
     */
    @PostMapping("/record")
    public Result record() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        uvService.record(user.getId());
        //返回记录后今天的 UV，方便直接看到效果
        return Result.ok(uvService.todayCount());
    }

    /**
     * 今天的 UV
     */
    @GetMapping("/today")
    public Result today() {
        return Result.ok(uvService.todayCount());
    }

    /**
     * 指定某天的 UV，例如 /uv/2026-11-04
     */
    @GetMapping("/{date}")
    public Result byDate(@PathVariable("date") String date) {
        if (!isValidDate(date)) {
            return Result.fail("日期格式应为 yyyy-MM-dd");
        }
        return Result.ok(uvService.countByDate(date));
    }

    /**
     * 一段时间的 UV，跨天去重，例如 /uv/range?start=2026-11-01&end=2026-11-04
     */
    @GetMapping("/range")
    public Result range(@RequestParam("start") String start,
                        @RequestParam("end") String end) {
        if (!isValidDate(start) || !isValidDate(end)) {
            return Result.fail("日期格式应为 yyyy-MM-dd");
        }
        return Result.ok(uvService.countRange(start, end));
    }

    /**
     * 用 JDK 自带的 ISO 格式校验，正好就是 yyyy-MM-dd
     */
    private boolean isValidDate(String date) {
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
