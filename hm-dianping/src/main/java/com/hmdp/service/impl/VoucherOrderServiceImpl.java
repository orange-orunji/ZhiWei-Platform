package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.AMQImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

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
    private RabbitTemplate rabbitTemplate;
    @Resource
    private RedissonClient redissonClient;
    private static final String STREAM_KEY = "stream.orders";
    private static final String GROUP_NAME = "g1";
    private static final String CONSUMER_NAME = "c1";    //创建lua脚ben
    private static final DefaultRedisScript<Long> SECKILL;
    private final boolean running = true;
    // 消费者组是否已经初始化的标记
    private volatile boolean groupInitialized = false;
    static {
        SECKILL = new DefaultRedisScript<>();
        SECKILL.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL.setResultType(Long.class);
    }
/**
 * 线程池相关做法
 */
    //新创阻塞队列(JVM虚拟机实现)
//    BlockingQueue<VoucherOrder> queue = new ArrayBlockingQueue<>(1024 * 1024);
    //创建线程池
//    private final ExecutorService executor = Executors.newSingleThreadExecutor();

//    创建销毁方法，用于销毁线程池
//    @PreDestroy
//    public void destroy() {
//        running = false;
//        executor.shutdown();
//        try {
//            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
//                executor.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            executor.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//    }
    //初始化代理对象和提交线程任务
//    @PostConstruct
//    public void init(){
//        if (!groupInitialized) extracted();
//        executor.submit(runnable);
//    }
    /**
     * 创建消费者组
     * @return
     */
//    private void extracted() {
//        try {
//            // 创建消费者组，从队列开头(0)开始消费
//            stringRedisTemplate.opsForStream()
//                    .createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
//            log.info("消费者组 {} 创建成功", GROUP_NAME);
//        } catch (Exception e) {
//            // 组已经存在，忽略异常
//            log.info("消费者组 {} 已存在，无需重复创建", GROUP_NAME);
//        }
//    }

/**
    线程池任务
 @return
 **/
//    Runnable runnable = new Runnable() {
//        @Override
//        public void run() {
//            groupInitialized = true;
//
//            while (running){
//                try {
////                    VoucherOrder voucherOrder = queue.take();
////                    proxy.getVoucherOrder(voucherOrder);
//                    //1.创建消费者监听对象
//                    List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
//                            Consumer.from(GROUP_NAME,CONSUMER_NAME),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create(STREAM_KEY, ReadOffset.from(">"))
//                    );
//                    //2.获取阻塞队列中的订单消息
//                    if(recordList == null || recordList.isEmpty()) continue;
//                    MapRecord<String, Object, Object> record = recordList.get(0);
//                    //3.获取订单信息
//                    VoucherOrder voucherOrder = BeanUtil.mapToBean(record.getValue(), VoucherOrder.class,true);
//                    proxy.getVoucherOrder(voucherOrder);
//                    //4.XCAK 处理处理队列中的订单信息
//                    stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY,GROUP_NAME,record.getId());
//                } catch (Exception e) {
//                    handlerPlanting();
//                }
//            }
//        }
//
//
//
//        /**
//         * 处理异常带出来订单队列数据
//         * @return
//         */
//
//        private void handlerPlanting() {
//            while (running){
//                try {
//                    List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create(STREAM_KEY, ReadOffset.from("0"))
//                    );
//                    //2.获取阻塞队列中的订单消息
//                    if(recordList == null || recordList.isEmpty()) continue;
//                    MapRecord<String, Object, Object> record = recordList.get(0);
//                    //3.获取订单信息
//                    VoucherOrder voucherOrder = BeanUtil.mapToBean(record.getValue(), VoucherOrder.class,true);
//                    //4.XCAK 处理处理队列中的订单信息
//                    stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY,GROUP_NAME,record.getId());
//                } catch (Exception e) {
//                    log.error("处理pending消息失败！", e);
//                    try {
//                        Thread.sleep(2000L);
//                    } catch (InterruptedException ex) {
//                        Thread.currentThread().interrupt();
//                        break;
//                    }
//                }
//            }
//        }
//    };

    //创建全局类的代理对象
    IVoucherOrderService proxy;

    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        // 登录校验
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        long orderId = redisIdWorker.nextId("order");
        //1.lua脚本实现秒杀库存,一人一单是否抢购成功
        Long l = stringRedisTemplate.execute(
                //lua脚本引用
                SECKILL,
                Collections.emptyList(),
                voucherId.toString(),
                user.getId().toString(),String.valueOf(orderId)
        );
        long r = l.intValue();
        //2.判断是否抢购成功
        //2.1 失败
        if(r!=0) return Result.fail(r==1?"库存不足！":"请勿重复下单！");
//        if (proxy==null)  proxy = (IVoucherOrderService) AopContext.currentProxy();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(user.getId());
        rabbitTemplate.convertAndSend("order.exchange","order.generate",voucherOrder);
        return Result.ok(orderId);
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) throws InterruptedException {
//        //1.lua脚本实现秒杀库存,一人一单是否抢购成功
//        Long l = stringRedisTemplate.execute(
//                SECKILL,
//                Collections.emptyList(),
//                voucherId.toString(),
//                UserHolder.getUser().getId().toString()
//        );
//        long r = l.intValue();
//        //2.判断是否抢购成功
//        //2.1 失败
//        if(r!=0){
//            return Result.fail(r==1?"库存不足！":"请勿重复下单！");
//        }
//        //3. 基于阻塞队列来实现存储
//        long orderId = redisIdWorker.nextId("order");
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        voucherOrder.setVoucherId(voucherId);
//        if (proxy==null)  proxy = (IVoucherOrderService) AopContext.currentProxy();
//        queue.put(voucherOrder);
//        return Result.ok(orderId);
//    }
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
//    @Transactional
//    public void getVoucherOrder(VoucherOrder voucher, Message message, AMQImpl.Channel channel) {
//        //一人一单处理
////        Long userId = UserHolder.getUser().getId();
////        幂等检验,判断是否重复下单
//        Integer count = query().eq("user_id", voucher.getUserId()).eq("voucher_id", voucher.getVoucherId()).count();
//        if(count>0){
//            log.error("您已购买过该优惠券！");
//            throw new RuntimeException();
//        }
//        //4.更新库存
//        seckill.update().setSql("stock = stock - 1")
//                .eq("voucher_id", voucher.getVoucherId())
//                .gt("stock",0)
//                .update();
////        //5.创建订单
////        VoucherOrder order = new VoucherOrder();
////        //6.设置订单属性
////        order.setVoucherId(voucherId);
////        order.setUserId(userId);
////        order.setId(redisIdWorker.nextId("order"));
////        //8.保存订单
//        save(voucher);
////        //9.返回订单结果
////        Result.ok(voucher.getId());
//    }

    /**
     * 基于rabbitMq来实现秒杀活动
     * @param voucher
     */
    @Transactional
    public void getVoucherOrder(VoucherOrder voucher) {
        // 幂等校验,判断是否重复下单
        Integer count = query().eq("user_id", voucher.getUserId()).eq("voucher_id", voucher.getVoucherId()).count();
//        重复购买
        if(count > 0){
            log.error("您已购买过该优惠券");
            return;
        }
        // 更新秒杀券库存
        boolean success = seckill.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucher.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            throw new RuntimeException("库存不足");
        }
        // 保存订单
        save(voucher);
    }
}
