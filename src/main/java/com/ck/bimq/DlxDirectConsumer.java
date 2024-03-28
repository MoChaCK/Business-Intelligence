package com.ck.bimq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

public class DlxDirectConsumer {

    // 定义监听的私信交换机名称
    private static final String DEAD_EXCHANGE_NAME = "dlx-direct-exchange";
    //定义监听的业务交换机名称
    private static final String WORK_EXCHANGE_NAME = "direct2-exchange";

    public static void main(String[] args) throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("192.168.200.130");
        connectionFactory.setUsername("itcast");
        connectionFactory.setPassword("123321");

        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        // 创建用于指定死信队列的参数的Map对象
        HashMap<String, Object> map = new HashMap<>();
        // 将要创建的队列绑定到指定的交换机，并设置死信队列的参数
        map.put("x-dead-letter-exchange", DEAD_EXCHANGE_NAME);
        // 指定死信要转发到外包死信队列
        map.put("x-dead-letter-routing-key", "waibao");

        // 创建新的工作队列
        String workQueueName = "xiaodog_queue";
        channel.queueDeclare(workQueueName, true, false, false, map);
        channel.queueBind(workQueueName, WORK_EXCHANGE_NAME, "xiaodog");

        // 创建用于指定死信队列的参数的Map对象
        HashMap<String, Object> map1 = new HashMap<>();
        // 将要创建的队列绑定到指定的交换机，并设置死信队列的参数
        map.put("x-dead-letter-exchange", DEAD_EXCHANGE_NAME);
        // 指定死信要转发到老板的死信队列
        map.put("x-dead-letter-routing-key", "laoban");

        //创建新的工作队列
        String workQueueName1 = "xiaocat_queue";
        channel.queueDeclare(workQueueName1, true, false, false, map1);
        channel.queueBind(workQueueName1, WORK_EXCHANGE_NAME, "xiaocat");

        // 创建用于处理小狗工作队列消息的回调函数，当接收消息时，拒绝消息并打印消息内容
        DeliverCallback xiaodogDeliveryCallback = (((consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            // 拒绝消息，并且不要将消息冲重新放回队列，只拒绝当前消息
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            System.out.println("[xiaodog] received" + delivery.getEnvelope().getRoutingKey() + ":" + message);
        }));
        // 创建用于处理小猫工作队列消息的回调函数，当接收消息时，拒绝消息并打印消息内容
        DeliverCallback xiaocatDeliveryCallback = (((consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            // 拒绝消息，并且不要将消息冲重新放回队列，只拒绝当前消息
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            System.out.println("[xiaocat] received" + delivery.getEnvelope().getRoutingKey() + ":" + message);
        }));

        // 注册消费者，用于消费小狗工作队列，绑定回调函数，自动确认下消息改为false
        channel.basicConsume(workQueueName, false, xiaodogDeliveryCallback, consumerTag -> {});

        // 注册消费者，用于消费小猫工作队列，绑定回调函数，自动确认下消息改为false
        channel.basicConsume(workQueueName1, false, xiaocatDeliveryCallback, consumerTag -> {});






    }
}
