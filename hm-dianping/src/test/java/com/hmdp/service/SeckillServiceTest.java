package com.hmdp.service;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;


@SpringBootTest
public class SeckillServiceTest {

    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void testSeckill() {
        System.out.println("测试执行...");
    }

    @Test
    public void rabbitMQQueueTest(){
        System.out.println("测试执行...");
//        String message = "hello";
        Map<String,String> map = new HashMap<>();
        map.put("name","wangwu");
        map.put("age","23");
        rabbitTemplate.convertAndSend("order.exchange","order.create", map);
        System.out.println("消息已发送，等待接收");
    }
}

//@Configuration
//class RabbitMqConfig {
//
//    private static RabbitTemplate rabbitTemplate;
//
////    自动装配给rabbit Template
//    @Bean
//    public MessageConverter messageConverter(){
//        return new Jackson2JsonMessageConverter();
//    }
//
//    @RabbitListener(bindings = @QueueBinding(
//            value = @Queue(value = "order.queue", durable = "true"),
//            exchange = @Exchange(value = "order.exchange", type = ExchangeTypes.DIRECT),
//            key = "order.create"
//    )
//    )
//    public void receive(Map message) {
//        System.out.println("接收到消息：" + message);
//    }

//    public void sent(String massage){
//        rabbitTemplate.convertAndSend("order.exchange","order.create", JSONUtil.toBean(massage,Map.class));
//    }
//}