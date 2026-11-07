package com.yijia.service.impl;

import com.yijia.entity.FlashDeal;
import com.yijia.mapper.FlashDealMapper;
import com.yijia.service.IFlashDealService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 抢购特惠套餐表，与特惠套餐是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class FlashDealServiceImpl extends ServiceImpl<FlashDealMapper, FlashDeal> implements IFlashDealService {

}
