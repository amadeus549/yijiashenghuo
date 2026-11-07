package com.yijia.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yijia.dto.Result;
import com.yijia.entity.Deal;
import com.yijia.mapper.DealMapper;
import com.yijia.entity.FlashDeal;
import com.yijia.service.IFlashDealService;
import com.yijia.service.IDealService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.yijia.utils.RedisConstants.FLASH_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class DealServiceImpl extends ServiceImpl<DealMapper, Deal> implements IDealService {

    @Resource
    private IFlashDealService flashDealService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryDealOfProvider(Long providerId) {
        // 查询特惠套餐信息
        List<Deal> deals = getBaseMapper().queryDealOfProvider(providerId);
        // 返回结果
        return Result.ok(deals);
    }

    @Override
    @Transactional
    public void addFlashDeal(Deal deal) {
        // 保存特惠套餐
        save(deal);
        // 保存抢购信息
        FlashDeal flashDeal = new FlashDeal();
        flashDeal.setDealId(deal.getId());
        flashDeal.setStock(deal.getStock());
        flashDeal.setBeginTime(deal.getBeginTime());
        flashDeal.setEndTime(deal.getEndTime());
        flashDealService.save(flashDeal);
        //保存抢购库存到redis当中
        stringRedisTemplate.opsForValue().set(FLASH_STOCK_KEY + deal.getId(), deal.getStock().toString());
    }
}
