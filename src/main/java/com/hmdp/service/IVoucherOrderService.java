package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    public Result seckillVouncher(Long id) throws InterruptedException;

    public Result seckillVouncherByLua(Long id) throws InterruptedException;

    Result createOrder(long id);

    void createOrder2(VoucherOrder order);


}
