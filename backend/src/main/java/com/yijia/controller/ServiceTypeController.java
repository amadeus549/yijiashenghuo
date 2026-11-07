package com.yijia.controller;


import com.yijia.dto.Result;
import com.yijia.service.IServiceTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/service-type")
public class ServiceTypeController {

    @Resource
    private IServiceTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        // 只做参数接收与结果返回，业务逻辑全部下沉到 Service
        return typeService.queryTypeList();
    }
}
