package com.hmdp.utils;

import com.hmdp.config.RabbitMqConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;

@Slf4j
@Component
public class OrderConsumer {

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    /**
     * 订单队列消费者
     * @param voucherOrder
     * @param message
     * @param channel
     * @throws IOException
     *  ┌─────────────────────────────────────────────────────────┐
        │                    消息处理流程                           │
        │                                                         │
        │  RabbitMQ ──消息──▶ order.queue ──▶ OrderConsumer        │
        │                                          │               │
        │                            ┌─────────────┴──────────┐    │
        │                            ▼                        ▼    │
        │                       成功 ✓                    失败 ✗   │
        │                            │                        │    │
        │                     basicAck()              basicNack()  │
        │                            │                        │    │
        │                      消息删除               requeue=?    │
        │                                        ┌───────┴──────┐ │
        │                                      true           false│
        │                                       │               │  │
        │                                  重回原队列      路由到 DLX │
        │                                  (可能死循环!)      │      │
        │                                                   ▼      │
        │                                           order.dlx.queue│
        │                                                   │      │
        │                                              dlxConsumer │
        │                                             记录日志/告警  │
        └─────────────────────────────────────────────────────────┘

     */
    @Transactional
    @RabbitListener(
            bindings = @QueueBinding(
                    key= "order.generate",                                  //绑定遗嘱给死信队列
                    value= @Queue(value = "order.queue",durable = "true",arguments = {
                            @Argument(name = "x-dead-letter-exchange", value = "order.dlx.exchange"),
                            @Argument(name = "x-dead-letter-routing-key", value = "order.dlx")
                    }),
                    exchange = @Exchange(value = "order.exchange",type = ExchangeTypes.DIRECT)
            )
    )
    public void orderConsumer(VoucherOrder voucherOrder,
                              Message message,
                              Channel channel) throws IOException {
        long consumerTag = message.getMessageProperties().getDeliveryTag();
        try{
            voucherOrderService.getVoucherOrder(voucherOrder);
        }
        catch (Exception e){
            log.error("订单处理失败，订单ID：{}", voucherOrder.getId(), e);
            channel.basicNack(consumerTag,false,false);
            throw e;
        }
        channel.basicAck(consumerTag,false);
    }
    /**
     * 死信队列
     */
    @RabbitListener(
            bindings = @QueueBinding(
                    key = "order.dlx",
                    value = @Queue(value = "order.dlx.queue",durable = "true"),
                    exchange = @Exchange(value = "order.dlx.exchange",type = ExchangeTypes.DIRECT)
            )
    )
    public void dlxConsumer(VoucherOrder voucherOrder){
         log.error("【死信】订单 {} 已进入死信队列，用户ID：{}，券ID：{}",
                voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());
      }

}
