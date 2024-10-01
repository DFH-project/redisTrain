package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisIDWorker redisIDWorker;

    @Override
    public Result seckillVouncher(long id) throws InterruptedException {
        // 查询获取秒杀信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(id);

        // 判断时间是否已经生效
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();
        if ( now .isBefore(beginTime) || now.isAfter(endTime)) return Result.fail("不在活动时间范围内！");

        // 判断库存
        if (seckillVoucher.getStock() < 1 ) return Result.fail("库存不足！");

        //一人一单
        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
//         //   return createOrder(id);  约等于   return this.createOrder(id);  但是事务是使用代理的对象来调用的，使用this是没有事务功能的，相当于事务失效
//            //获取代理对象
//            IVoucherOrderService currentProxy = (IVoucherOrderService)AopContext.currentProxy();
//            return currentProxy.createOrder(id);
//        }
        //使用redis 分布式锁
       // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);  // 使用stringRedisTemplate
        //放弃使用stringRedisTemplate  使用redisson
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        //获取索
        //boolean tryLock = lock.tryLock(10);
        boolean tryLock = lock.tryLock(1L,TimeUnit.SECONDS);   // 10 表示 十秒内获取不到锁可重新获取
        if (!tryLock){
            // 获取锁不成功    已经有线程为这个用户下单，所以不需要重试
            return Result.fail("。。。。。。");
        }
        try {
            IVoucherOrderService currentProxy = (IVoucherOrderService)AopContext.currentProxy();
            return currentProxy.createOrder(id);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    @Override
    public Result createOrder(long id) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", id).count();
        if (count>0){
            return Result.fail(" 已经购买过了哦~");
        }

        // 扣减库存     乐观锁 实现：.eq("stock",seckillVoucher.getStock())
        seckillVoucherService.update().setSql("stock = stock -1 ").eq("voucher_id", id)
                .gt("stock" ,0 ).update();

        // 创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIDWorker.nextId("order");
        order.setId(orderId);
        order.setVoucherId(id);
        order.setUserId(userId);
        save(order);

        return Result.ok();

    }
}
