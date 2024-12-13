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
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.*;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedissonClient redissonClient2;

    @Autowired
    private RedissonClient redissonClient3;

    @Autowired
    private RedisIDWorker redisIDWorker;

    @Override
    public Result seckillVouncher(Long id) throws InterruptedException {
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
        RLock lock2 = redissonClient2.getLock("lock:order:" + userId);
        RLock lock3 = redissonClient3.getLock("lock:order:" + userId);
        RLock lockaLL = redissonClient.getMultiLock(lock,lock2,lock3);  //  获取分布式锁

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

    @Transactional
    public void createOrder2(VoucherOrder order) {
        //一人一单
        Long userId = order.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", order.getVoucherId()).count();
        if (count>0){
            log.error(" 已经购买过了哦~");
        }
        // 扣减库存     乐观锁 实现：.eq("stock",seckillVoucher.getStock())
        seckillVoucherService.update().setSql("stock = stock -1 ").eq("voucher_id", order.getVoucherId())
                .gt("stock" ,0 ).update();

        // 创建订单
        save(order);
    }


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT ;
    private  IVoucherOrderService currentProxy;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024*1024);

    private static ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newFixedThreadPool(1);

    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandle());
    }

    private  class VoucherOrderHandle implements Runnable{
        @Override
        public void run(){
            while (true){
                try {
                    VoucherOrder order = orderQueue.take();
                    handleVouncherOrder(order);
                }catch (Exception e){
                    log.error("ERROR!!!!!!!!!!");
                }
            }
        }
    }

    public void handleVouncherOrder(VoucherOrder order) throws InterruptedException {
        Long userId =  order.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        RLock lock2 = redissonClient2.getLock("lock:order:" + userId);
        RLock lock3 = redissonClient3.getLock("lock:order:" + userId);
        RLock lockaLL = redissonClient.getMultiLock(lock,lock2,lock3);  //  获取分布式锁

        //获取索
        boolean tryLock = lockaLL.tryLock(1L,TimeUnit.SECONDS);   // 10 表示 十秒内获取不到锁可重新获取
        if (!tryLock){
            // 获取锁不成功    已经有线程为这个用户下单，所以不需要重试
            log.error(" 获取锁不成功 ");
        }
        try {
            currentProxy.createOrder2(order);
        } finally {
            lock.unlock();
        }
    }


    @Override
    public Result seckillVouncherByLua(Long voucherId)  {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1 执行LUA脚本
        Long execute = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        // 2 判断脚本是否为0
        int res = execute.intValue();
        // 3 不为0 ，不能购买
        if (res !=0){
            return Result.fail(res ==1 ?"库存不足":"不能重复下单");
        }
        // 4 为0  有购买资格，把下单信息保存至阻塞队列
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIDWorker.nextId("order");
        order.setId(orderId);
        order.setVoucherId(voucherId);
        order.setUserId(userId);
        // TODO 保存阻塞队列
        // 创建阻塞队列
        orderQueue.add(order);  // 使用的是JVM内存  如果订单量过高，可能会内存溢出 ，因此需要引入 消息队列 
        // 开启异步进行下单
        currentProxy =  (IVoucherOrderService)AopContext.currentProxy();
        // 返回订单id
        return Result.ok();
    }



}
