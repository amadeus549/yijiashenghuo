package com.yijia.service;

import com.yijia.dto.Result;
import com.yijia.entity.DealOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IDealOrderService extends IService<DealOrder> {

    Result flashDeal(Long dealId);

    /** 我的订单，按时间倒序分页 */
    Result queryMyOrders(Integer current);

    /** 支付（演示用，直接把状态从未支付改成已支付） */
    Result pay(Long orderId);

    /** 取消订单：回滚库存 + 释放 Redis 里的一人一单标记 */
    Result cancel(Long orderId);

    /**
     * 创建订单，由消费线程调用
     *
     * @return true 下单成功
     */
    boolean createDealOrder(DealOrder dealOrder);
}
