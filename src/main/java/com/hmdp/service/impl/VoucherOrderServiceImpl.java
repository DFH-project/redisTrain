package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;

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
    private RedisIDWorker redisIDWorker;

    @Override
    @Transactional
    public Result seckillVouncher(long id) {
        // 查询获取秒杀信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(id);

        // 判断时间是否已经生效
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();
        if ( now .isBefore(beginTime) || now.isAfter(endTime)) return Result.fail("不在活动时间范围内！");

        //一人一单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", id).count();
        if (count>0){
            return Result.fail(" 已经购买过了哦~");
        }

        // 判断库存
        if (seckillVoucher.getStock() < 1 ) return Result.fail("库存不足！");

        // 扣减库存     乐观锁 实现：.eq("stock",seckillVoucher.getStock())
        seckillVoucherService.update().setSql("stock = stock -1 ").eq("voucher_id",id )
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
