package com.yijia.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.util.StrUtil;
import com.yijia.dto.DealOrderVO;
import com.yijia.dto.Result;
import com.yijia.dto.UserDTO;
import com.yijia.entity.Deal;
import com.yijia.entity.FlashDeal;
import com.yijia.entity.DealOrder;
import com.yijia.entity.Provider;
import com.yijia.mapper.DealOrderMapper;
import com.yijia.service.IDealService;
import com.yijia.service.IFlashDealService;
import com.yijia.service.IDealOrderService;
import com.yijia.service.IProviderService;
import com.yijia.utils.RedisConstants;
import com.yijia.utils.RedisIdWorker;
import com.yijia.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class DealOrderServiceImpl extends ServiceImpl<DealOrderMapper, DealOrder> implements IDealOrderService {

    @Resource
    private IFlashDealService flashDealService;
    @Resource
    private IDealService dealService;
    @Resource
    private IProviderService providerService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    /** 抢购脚本：库存判断 + 一人一单，全部在 Redis 内原子完成 */
    private static final DefaultRedisScript<Long> FLASH_SCRIPT;

    static {
        FLASH_SCRIPT = new DefaultRedisScript<>();
        FLASH_SCRIPT.setLocation(new ClassPathResource("flash.lua"));
        FLASH_SCRIPT.setResultType(Long.class);
    }

    /** 下单任务队列：请求线程只负责投递，写库交给消费线程 */
    private final BlockingQueue<DealOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    /** 单线程消费，保证写库不会并发；线程名便于排查问题 */
    private static final ExecutorService FLASH_ORDER_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "flash-order-handler");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 事务代理对象。
     * 消费线程里 UserHolder 的 ThreadLocal 是空的、AopContext 也没有调用上下文，
     * 所以必须在请求线程里提前取到代理，并且要在投递队列之前赋值。
     */
    private IDealOrderService proxy;

    @PostConstruct
    private void init() {
        // 启动消费线程
        FLASH_ORDER_EXECUTOR.submit(new DealOrderHandler());
        // 预热库存到 Redis，脚本依赖它
        warmUpStock();
    }

    /**
     * 消费线程：不断从队列取订单，交给数据库处理
     */
    private class DealOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息（队列为空时阻塞等待）
                    DealOrder dealOrder = orderTasks.take();
                    // 2.创建订单
                    handleDealOrder(dealOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    /**
     * 真正的下单动作，运行在消费线程中
     */
    private void handleDealOrder(DealOrder dealOrder) {
        Long userId = dealOrder.getUserId();
        // 1.创建锁对象，使用Redisson分布式锁（多实例部署时防止同一用户并发下单）
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 2.尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败，说明该用户还有订单在处理
            log.error("不允许重复下单，userId={}", userId);
            return;
        }
        try {
            // 3.用代理对象调用，保证 @Transactional 生效
            proxy.createDealOrder(dealOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result flashDeal(Long dealId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();

        // 1.执行lua脚本：库存判断 + 一人一单，在 Redis 内一次性校验并扣减
        Long result = stringRedisTemplate.execute(
                FLASH_SCRIPT,
                Collections.emptyList(),
                dealId.toString(),
                userId.toString()
        );
        int r = result == null ? -1 : result.intValue();
        if (r == 1) {
            return Result.fail("库存不足");
        }
        if (r == 2) {
            return Result.fail("不能重复下单");
        }
        if (r != 0) {
            return Result.fail("抢购活动未开始或已结束");
        }

        // 2.校验通过，生成订单号并封装下单信息
        long orderId = redisIdWorker.nextId("order");
        DealOrder dealOrder = new DealOrder();
        dealOrder.setId(orderId);
        dealOrder.setUserId(userId);
        dealOrder.setDealId(dealId);

        // 3.先拿代理再入队，顺序反了消费线程可能取到 null
        proxy = (IDealOrderService) AopContext.currentProxy();
        // 4.订单入队，交给消费线程异步写库
        orderTasks.add(dealOrder);

        // 5.立即返回订单号，用户不用等数据库
        return Result.ok(orderId);
    }

    /**
     * 数据库侧真正落库，运行在消费线程里，所以用户信息只能从订单对象上取
     */
    @Override
    @Transactional
    public boolean createDealOrder(DealOrder dealOrder) {
        Long userId = dealOrder.getUserId();
        Long dealId = dealOrder.getDealId();

        // 1.一人一单：数据库再兜一次底，防止 Redis 数据丢失导致重复
        // MyBatis-Plus 3.5 起 count() 返回 Long
        long count = query().eq("user_id", userId).eq("deal_id", dealId).count();
        if (count > 0) {
            log.error("用户已经购买过，userId={}, dealId={}", userId, dealId);
            return false;
        }

        // 2.扣减库存，gt("stock", 0) 保证并发下不会扣成负数
        boolean success = flashDealService.update()
                .setSql("stock = stock - 1")
                .eq("deal_id", dealId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足，扣减失败，dealId={}", dealId);
            return false;
        }

        // 3.写入订单
        return save(dealOrder);
    }

    /**
     * 启动时把"当前处于活动时间内"的券库存预热到 Redis。
     * 用 setIfAbsent，已经存在的 key 不覆盖，避免重启把已扣的库存还原。
     */
    private void warmUpStock() {
        try {
            List<FlashDeal> deals = flashDealService.query()
                    .le("begin_time", LocalDateTime.now())
                    .ge("end_time", LocalDateTime.now())
                    .list();
            for (FlashDeal deal : deals) {
                String key = RedisConstants.FLASH_STOCK_KEY + deal.getDealId();
                stringRedisTemplate.opsForValue()
                        .setIfAbsent(key, String.valueOf(deal.getStock()));
            }
            log.info("抢购库存预热完成，活动中的券共 {} 张", deals.size());
        } catch (Exception e) {
            log.error("抢购库存预热失败，请检查 MySQL / Redis 是否可用", e);
        }
    }

    /*
    ========================================================================
    下面是改造前的同步版实现，留作对照：
    请求线程里查库、加锁、扣库存、写订单，全部同步完成，接口耗时长
    ========================================================================

    @Override
    public Result flashDeal(Long dealId) {
        //查询特惠套餐信息
        FlashDeal deal = flashDealService.getById(dealId);
        //特惠套餐不存在，直接返回失败信息
        if(deal == null){
            return Result.fail("特惠套餐不存在");
        }
        //判断抢购是否开始：比较开始时间
        if(deal.getBeginTime().isAfter(LocalDateTime.now())){
            //抢购活动尚未开始，返回失败信息
            return Result.fail("抢购活动尚未开始");
        }
        //判断抢购是否结束：比较的是失效时间 endTime
        if(deal.getEndTime().isBefore(LocalDateTime.now())){
            //抢购活动已经结束，返回失败信息
            return Result.fail("抢购活动已经结束");
        }

        //判断库存是否充足
        if(deal.getStock() < 1){
            //库存不足，返回失败信息
            return Result.fail("库存不足");
        }

        //获取当前用户ID
        Long userId = UserHolder.getUser().getId();

        //创建锁对象，使用Redisson分布式锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        //尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //获取锁失败，说明用户重复下单
            return Result.fail("不允许重复下单");
        }
        try {
            //使用代理对象创建订单，解决事务问题
            IDealOrderService proxy = (IDealOrderService) AopContext.currentProxy();
            return proxy.createDealOrder(dealId);
        } finally  {
            //释放锁
            lock.unlock();
        }
    }
    */

    @Override
    public Result queryMyOrders(Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        int pageNo = (current == null || current < 1) ? 1 : current;
        Page<DealOrder> page = lambdaQuery()
                .eq(DealOrder::getUserId, user.getId())
                .orderByDesc(DealOrder::getCreateTime)
                .page(new Page<>(pageNo, 5));
        List<DealOrder> orders = page.getRecords();
        if (orders.isEmpty()) {
            return Result.ok(Collections.emptyList(), page.getTotal());
        }

        // 批量取套餐和服务商，避免每条订单都查一次库
        Set<Long> dealIds = orders.stream().map(DealOrder::getDealId).collect(Collectors.toSet());
        Map<Long, Deal> dealMap = dealService.listByIds(dealIds).stream()
                .collect(Collectors.toMap(Deal::getId, d -> d, (a, b) -> a));
        Set<Long> providerIds = dealMap.values().stream().map(Deal::getProviderId).collect(Collectors.toSet());
        Map<Long, Provider> providerMap = providerIds.isEmpty() ? Collections.emptyMap()
                : providerService.listByIds(providerIds).stream()
                .collect(Collectors.toMap(Provider::getId, p -> p, (a, b) -> a));

        List<DealOrderVO> vos = orders.stream().map(order -> {
            DealOrderVO vo = new DealOrderVO();
            vo.setId(order.getId());
            vo.setDealId(order.getDealId());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());
            vo.setPayTime(order.getPayTime());
            vo.setCancelable(Integer.valueOf(1).equals(order.getStatus()));
            Deal deal = dealMap.get(order.getDealId());
            if (deal != null) {
                vo.setTitle(deal.getTitle());
                vo.setPayValue(deal.getPayValue());
                vo.setProviderId(deal.getProviderId());
                Provider provider = providerMap.get(deal.getProviderId());
                if (provider != null) {
                    vo.setProviderName(provider.getName());
                    if (StrUtil.isNotBlank(provider.getImages())) {
                        vo.setImage(provider.getImages().split(",")[0]);
                    }
                }
            }
            return vo;
        }).collect(Collectors.toList());

        return Result.ok(vos, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result pay(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        DealOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!order.getUserId().equals(user.getId())) {
            return Result.fail("无权操作该订单");
        }
        if (Integer.valueOf(2).equals(order.getStatus())) {
            return Result.fail("订单已支付，请勿重复支付");
        }
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            return Result.fail("订单已取消，无法支付");
        }
        // 带 status = 1 条件更新，防止并发重复支付
        boolean updated = lambdaUpdate()
                .eq(DealOrder::getId, orderId)
                .eq(DealOrder::getStatus, 1)
                .set(DealOrder::getStatus, 2)
                .set(DealOrder::getPayTime, LocalDateTime.now())
                .update();
        if (!updated) {
            return Result.fail("支付失败，请重试");
        }
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result cancel(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        DealOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!order.getUserId().equals(user.getId())) {
            return Result.fail("无权操作该订单");
        }
        if (Integer.valueOf(2).equals(order.getStatus())) {
            return Result.fail("订单已支付，不能直接取消");
        }
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            return Result.fail("订单已取消");
        }

        boolean updated = lambdaUpdate()
                .eq(DealOrder::getId, orderId)
                .eq(DealOrder::getStatus, 1)
                .set(DealOrder::getStatus, 4)
                .update();
        if (!updated) {
            return Result.fail("取消失败，请重试");
        }

        // 抢购单取消要把库存还回去，同时把"已下单"标记从 Redis 摘掉，
        // 否则用户会被一人一单规则永久挡住，再也买不了这个套餐
        FlashDeal flashDeal = flashDealService.getById(order.getDealId());
        if (flashDeal != null) {
            boolean stockBack = flashDealService.update(new UpdateWrapper<FlashDeal>()
                    .eq("deal_id", order.getDealId())
                    .setSql("stock = stock + 1"));
            if (!stockBack) {
                log.warn("订单 {} 取消时回滚库存失败，dealId={}", orderId, order.getDealId());
            }
            try {
                stringRedisTemplate.opsForValue()
                        .increment(RedisConstants.FLASH_STOCK_KEY + order.getDealId());
                stringRedisTemplate.opsForSet()
                        .remove(RedisConstants.FLASH_ORDER_KEY + order.getDealId(), user.getId().toString());
            } catch (Exception e) {
                // Redis 挂了不影响取消结果，库存以数据库为准，重启预热会对齐
                log.warn("订单 {} 取消后同步 Redis 失败：{}", orderId, e.toString());
            }
        }
        return Result.ok();
    }
}
