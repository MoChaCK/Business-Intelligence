package com.ck.bimq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.context.annotation.Configuration;

import static com.ck.constant.BiMqConstant.*;

/**
 * 用于创建用到的交换机和队列（只用在程序启动前执行一次）
 */
@Configuration
public class BiInitMain {

    public static void main(String[] args) {

        try {
            // 创建连接工厂
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost("192.168.200.130");
            connectionFactory.setUsername("itcast");
            connectionFactory.setPassword("123321");
            Connection connection = connectionFactory.newConnection();
            // 创建通道
            Channel channel = connection.createChannel();
            // 创建交换机
            channel.exchangeDeclare(BI_EXCHANGE_NAME, "direct", true);
            // 创建队列
            channel.queueDeclare(BI_QUEUE_NAME, true, false, false, null);
            // 绑定队列和交换机
            channel.queueBind(BI_QUEUE_NAME, BI_EXCHANGE_NAME, BI_ROUTING_KEY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
