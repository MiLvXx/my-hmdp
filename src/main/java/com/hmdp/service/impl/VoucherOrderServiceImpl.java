package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;


    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否在时间范围
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 3.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) { // 确保相同用户id对应同样的锁
            // 获取代理对象(事务)
            IVoucherOrderService proxy = ((IVoucherOrderService) AopContext.currentProxy());
            return proxy.createVoucherOrder(voucherId, userId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail("用户已经购买过了！");
        }

        // 4.扣减库存
        // CAS方案解决超卖问题(乐观锁)
        boolean b = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!b) return Result.fail("库存不足!");

        // 5.创建订单
        VoucherOrder order = new VoucherOrder();
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        // 用户id

        order.setUserId(userId);
        // 代金券id
        order.setVoucherId(voucherId);
        save(order);
        // 6.返回订单id
        return Result.ok(orderId);
    }
}
