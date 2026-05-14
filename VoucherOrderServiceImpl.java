package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.IRedisLock;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import io.lettuce.core.RedisClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
    private ISeckillVoucherService seckill ;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisClient redisClient;


    private static final String USER_ID = "lock:shop:";
    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    @Override
    public Object seckillVoucher(Long voucherId) {
        //1.根据id查询优惠券信息
        SeckillVoucher voucher = seckill.getById(voucherId);
        //2.判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("当前秒杀尚未开始！");
        }
        //3.判断库存是否充足
        if(voucher.getStock()<1){
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        //获取锁对象
        IRedisLock lock = new IRedisLock(USER_ID+userId, stringRedisTemplate);

        if (!lock.tryLock(10)){
            return Result.fail("请勿重复下单！");
        }
        try {
            //获取事务代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.getVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            return Result.fail("请勿重复下单！");
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public Result getVoucherOrder(Long voucherId) {
        //一人一单处理
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count>0){
            return Result.fail("您已购买过该优惠券！");
        }
        //4.更新库存
        seckill.update().setSql("stock = stock - 1")
                .eq("id", voucherId)
                .gt("stock",0)
                .update();
        //5.创建订单
        VoucherOrder order = new VoucherOrder();
        //6.设置订单属性
        order.setVoucherId(voucherId);
        order.setUserId(userId);
        order.setId(redisIdWorker.nextId("order"));
        //8.保存订单
        save(order);
        //9.返回订单结果
        return Result.ok(order.getId());
    }
}
