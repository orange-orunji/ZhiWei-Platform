package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@Slf4j
@EnableRabbit
@Configuration
public class RabbitMqConfig {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 创建死信队列
     */
    @Bean
    public Queue dlxQueue(){
        return QueueBuilder.durable("order.dlx.queue").build();
    }

    /**
     * 创建死信交换器
     */
    @Bean
    public Exchange dlxExchange(){
        return ExchangeBuilder.directExchange("order.dlx.exchange").build();
    }

    /**
     * 绑定死信
     */
    @Bean
    public Binding BindingsDlx(){
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with("order.dlx").noargs();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,MessageConverter messageConverter){
            // 强转为 CachingConnectionFactory 开启 publisher returns
        if (connectionFactory instanceof CachingConnectionFactory) {
          ((CachingConnectionFactory) connectionFactory).setPublisherReturns(true);
        }
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);//添加序列化器
//      1.确认返回:消息是否到达 Exchange
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
            log.error("消息发送到Exchange失败: {}, 原因: {}",
                correlationData != null ? correlationData.getId() : "null", cause);
            //记录到Redis，人工补偿
                if (correlationData != null && cause != null) {
                        stringRedisTemplate.opsForSet().add("order:fail","CorrelationData:"+correlationData.getId()+"消息发送到Exchange失败:"+cause);
                }
            }
        });

//      2.退回返回:消息路由不到队列
        template.setReturnsCallback(returned -> {
            log.error("消息路由失败: exchange={}, routingKey={}, replyCode={}, body={}",
                 returned.getExchange(), returned.getRoutingKey(),
            returned.getReplyCode(), returned.getMessage());
            String body = new String(returned.getMessage().getBody());
            stringRedisTemplate.opsForSet().add("order:fail",
        "exchange:" + returned.getExchange() + " routingKey:" + returned.getRoutingKey() + " body:" + body);

        });
//        将returnCallback 生效
        template.setMandatory(true);
        return template;
    }
}
