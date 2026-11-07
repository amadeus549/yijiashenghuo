package com.yijia.controller;

import com.yijia.dto.Result;
import com.yijia.service.IDealOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 服务订单：抢购下单、我的订单、支付、取消
 */
@RestController
@RequestMapping("/deal-order")
public class DealOrderController {

    @Resource
    private IDealOrderService dealOrderService;

    /** 抢购下单，返回订单号；写库由消费线程异步完成 */
    @PostMapping("flash/{id}")
    public Result flashDeal(@PathVariable("id") Long dealId) {
        return dealOrderService.flashDeal(dealId);
    }

    /** 我的订单，按时间倒序，每页 5 条 */
    @GetMapping("/list")
    public Result list(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return dealOrderService.queryMyOrders(current);
    }

    /** 支付（演示：直接置为已支付） */
    @PostMapping("/{id}/pay")
    public Result pay(@PathVariable("id") Long orderId) {
        return dealOrderService.pay(orderId);
    }

    /** 取消待支付订单，回滚库存 */
    @PostMapping("/{id}/cancel")
    public Result cancel(@PathVariable("id") Long orderId) {
        return dealOrderService.cancel(orderId);
    }
}
