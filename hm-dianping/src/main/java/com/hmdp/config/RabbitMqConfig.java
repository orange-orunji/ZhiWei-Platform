package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

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
        return ExchangeBuilder.directExchange("order.dlx.exchang").build();
    }

    /**
     * 绑定死信
     */
    @Bean
    public Binding BindingsDlx(){
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with("order.dlx").noargs();
    }
}
