package com.yijia.service;

import com.yijia.dto.Result;
import com.yijia.entity.Deal;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IDealService extends IService<Deal> {

    Result queryDealOfProvider(Long providerId);

    void addFlashDeal(Deal deal);
}
