package com.ck.bimq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.util.Scanner;

/**
 * 死信队列生产者
 */
public class DlxDirectProducer {

    // 定义死信交换机名称"dlx-direct-exchange"
    private static final String DEAD_EXCHANGE_NAME = "dlx-direct-exchange";
    // 定义原来的业务交换机名称"direct2-exchange"
    private static final String WORK_EXCHANGE_NAME = "direct2-exchange";

    public static void main(String[] args) throws Exception {

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("192.168.200.130");
        connectionFactory.setUsername("itcast");
        connectionFactory.setPassword("123321");

        Connection connection = connectionFactory.newConnection();
        // 创建通道客户端
        Channel channel = connection.createChannel();

        // 声明死信交换
        channel.exchangeDeclare(DEAD_EXCHANGE_NAME, "direct");

        // 创建老板的死信队列，随机分配一个队列名称
        String deadQueueName1 = "laoban_dlx_queue";
        channel.queueDeclare(deadQueueName1, true, false, false, null);
        channel.queueBind(deadQueueName1, DEAD_EXCHANGE_NAME, "laoban");
        // 创建外包的死信队列，随机分配一个队列名称
        String deadQueueName2 = "waibao_dlx_queue";
        channel.queueDeclare(deadQueueName2, true, false, false, null);
        channel.queueBind(deadQueueName2, DEAD_EXCHANGE_NAME, "waibao");

        // 创建用于处理老板死信队列消息的回调函数，当接收消息时，拒绝消息并打印消息内容
        DeliverCallback laobanDeliveryCallback = ((consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            // 拒绝消息，并且不需要重新将消息放回队列，只拒绝当前消息
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            System.out.println("[laoban] received" + delivery.getEnvelope().getRoutingKey() + ":" + message);
        });
        // 创建用于处理外包死信队列消息的回调函数，当接收消息时，拒绝消息并打印消息内容
        DeliverCallback waibaoDeliveryCallback = ((consumerTag, delivery) -> {
            String message = new String(delivery.getBody());
            // 拒绝消息，并且不需要重新将消息放回队列，只拒绝当前消息
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            System.out.println("[laoban] received" + delivery.getEnvelope().getRoutingKey() + ":" + message);
        });

        // 注册消费者，用于消费老板的死信队列，绑定回调函数
        channel.basicConsume(deadQueueName1, laobanDeliveryCallback, consumerTag -> {});
        // 注册消费者，用于消费外包的死信队列，绑定回调函数
        channel.basicConsume(deadQueueName2, waibaoDeliveryCallback, consumerTag -> {});

        // 创建一个Scanner对象用于从控制台读取用户输入
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()){
            String userInput = scanner.nextLine();
            String[] strings = userInput.split(" ");
            if(strings.length < 1){
                continue;
            }
            String message = strings[0];
            String routingKey = strings[1];
            // 发布消息到业务交换机，带上指定的路由键
            channel.basicPublish(WORK_EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
            // 打印发送的消息和路由键
            System.out.println("[x] sent" + message + " with routingKey: " + routingKey);
        }

    }
}
