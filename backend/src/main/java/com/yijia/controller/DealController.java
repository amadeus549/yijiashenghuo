package com.yijia.controller;


import com.yijia.dto.Result;
import com.yijia.entity.Deal;
import com.yijia.service.IDealService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/deal")
public class DealController {

    @Resource
    private IDealService dealService;

    /**
     * 新增普通券
     * @param deal 特惠套餐信息
     * @return 特惠套餐id
     */
    @PostMapping
    public Result addDeal(@RequestBody Deal deal) {
        dealService.save(deal);
        return Result.ok(deal.getId());
    }

    /**
     * 新增抢购套餐
     * @param deal 特惠套餐信息，包含抢购信息
     * @return 特惠套餐id
     */
    @PostMapping("flash")
    public Result addFlashDeal(@RequestBody Deal deal) {
        dealService.addFlashDeal(deal);
        return Result.ok(deal.getId());
    }

    /**
     * 查询服务商的特惠套餐列表
     * @param providerId 服务商id
     * @return 特惠套餐列表
     */
    @GetMapping("/list/{providerId}")
    public Result queryDealOfProvider(@PathVariable("providerId") Long providerId) {
       return dealService.queryDealOfProvider(providerId);
    }
}
