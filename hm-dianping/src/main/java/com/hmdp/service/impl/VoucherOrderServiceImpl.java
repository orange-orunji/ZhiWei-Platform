package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
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
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
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
    private RedissonClient redissonClient;
    //创建lua脚ben
    private static final DefaultRedisScript<Long> SECKILL;
    static {
        SECKILL = new DefaultRedisScript<>();
        SECKILL.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL.setResultType(Long.class);
    }

    //新创阻塞队列
    BlockingQueue<VoucherOrder> queue = new ArrayBlockingQueue<>(1024 * 1024);
    //创建线程池
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    //初始化代理对象和提交线程任务
    @PostConstruct
    public void init(){
       proxy = (IVoucherOrderService) AopContext.currentProxy();
       executor.submit(runnable);
    }

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder voucherOrder = queue.take();
                    proxy.getVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.info("获取订单失败！");
                }
            }
        }
    };

    //创建全局类的代理对象
    IVoucherOrderService proxy;

    private static final String USER_ID = "lock:shop:";
    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    @Override
    public Object seckillVoucher(Long voucherId) throws InterruptedException {
        //1.lua脚本实现秒杀库存,一人一单是否抢购成功
        Long l = stringRedisTemplate.execute(
                SECKILL,
                Collections.emptyList(),
                voucherId,
                UserHolder.getUser().getId()
        );
        long r = l.intValue();
        //2.判断是否抢购成功
        //2.1 失败
        if(r!=0){
            return Result.fail(r==1?"库存不足！":"请勿重复下单！");
        }
        //3 基于阻塞队列来实现存储
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);

        queue.put(voucherOrder);
        return Result.ok(orderId);
    }
    //分布式+lua脚本来实现原子性的秒杀
//    @Override
//    public Object seckillVoucher(Long voucherId) throws InterruptedException {
//        //1.根据id查询优惠券信息
//        SeckillVoucher voucher = seckill.getById(voucherId);
//        //2.判断秒杀是否开始
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("当前秒杀尚未开始！");
//        }
//        //3.判断库存是否充足
//        if(voucher.getStock()<1){
//            return Result.fail("库存不足！");
//        }
//        Long userId = UserHolder.getUser().getId();
//        String name = USER_ID + userId;
//        //获取锁对象
////        IRedisLock lock = new IRedisLock(USER_ID+userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock(name);
//        if (!lock.tryLock(1, 10, TimeUnit.SECONDS)){
//            return Result.fail("请勿重复下单！");
//        }
//        try {
//            //获取事务代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.getVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            return Result.fail("请勿重复下单！");
//        } finally {
//            lock.unlock();
//        }
//    }

    /**
     * 创建订单
     * @param voucher
     * @return
     */
    @Transactional
    public void getVoucherOrder(VoucherOrder voucher) {
        //一人一单处理
//        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", voucher.getUserId()).eq("voucher_id", voucher.getVoucherId()).count();
        if(count>0){
            log.error("您已购买过该优惠券！");
            throw new RuntimeException();
        }
        //4.更新库存
        seckill.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucher.getVoucherId())
                .gt("stock",0)
                .update();
//        //5.创建订单
//        VoucherOrder order = new VoucherOrder();
//        //6.设置订单属性
//        order.setVoucherId(voucherId);
//        order.setUserId(userId);
//        order.setId(redisIdWorker.nextId("order"));
//        //8.保存订单
        save(voucher);
//        //9.返回订单结果
//        Result.ok(voucher.getId());
    }
}
