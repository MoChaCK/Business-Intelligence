package com.ck.bimq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.ck.constant.BiMqConstant.BI_EXCHANGE_NAME;
import static com.ck.constant.BiMqConstant.BI_ROUTING_KEY;

@Component
public class BiMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送消息
     */
    public void sendMessage(String message){
        rabbitTemplate.convertAndSend(BI_EXCHANGE_NAME, BI_ROUTING_KEY, message);
    }
}
