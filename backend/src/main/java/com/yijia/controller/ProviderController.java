package com.yijia.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yijia.dto.Result;
import com.yijia.entity.Provider;
import com.yijia.service.IProviderService;
import com.yijia.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/provider")
public class ProviderController {

    @Resource
    public IProviderService providerService;

    /**
     * 根据id查询服务商信息
     * @param id 服务商id
     * @return 服务商详情数据
     */
    @GetMapping("/{id}")
    public Result queryProviderById(@PathVariable("id") Long id) {
        return providerService.queryById(id);
    }

    /**
     * 新增服务商信息
     * @param provider 服务商数据
     * @return 服务商id
     */
    @PostMapping
    public Result saveProvider(@RequestBody Provider provider) {
        // 写库 + 同步 Redis 坐标
        return providerService.saveProvider(provider);
    }

    /**
     * 更新服务商信息
     * @param provider 服务商数据
     * @return 无
     */
    @PutMapping
    public Result updateProvider(@RequestBody Provider provider) {
        // 写入数据库
        return providerService.update(provider);
    }

    /**
     * 根据服务商类型分页查询服务商信息
     * 传了 x、y 就按"离我多远"排序，否则按普通分页
     */
    @GetMapping("/of/type")
    public Result queryProviderByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return providerService.queryProviderByType(typeId, current, x, y);
    }

    /**
     * 根据服务商名称关键字分页查询服务商信息
     * @param name 服务商名称关键字
     * @param current 页码
     * @return 服务商列表
     */
    @GetMapping("/of/name")
    public Result queryProviderByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Provider> page = providerService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
