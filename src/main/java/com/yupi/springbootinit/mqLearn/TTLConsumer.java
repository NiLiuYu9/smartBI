package com.yupi.springbootinit.mqLearn;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TTLConsumer {
	// 定义我们正在监听的队列名称
    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
         // 创建连接,创建连接工厂
        ConnectionFactory factory = new ConnectionFactory();
        // 设置连接工厂的主机名，这里我们连接的是本地的RabbitMQ服务器
        factory.setHost("localhost");
    	// 从工厂获取一个新的连接
        Connection connection = factory.newConnection();
        // 从连接中创建一个新的频道
        Channel channel = connection.createChannel();
        //设置过期时间参数
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl",5000);

        channel.queueDeclare(QUEUE_NAME, false, false, false, args);
        // 在控制台打印等待接收消息的信息
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
    	// 定义了如何处理消息,创建一个新的DeliverCallback来处理接收到的消息
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 将消息体转换为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            // 在控制台打印已接收消息的信息
            System.out.println(" [x] Received '" + message + "'");
        };
        //                              不确认模拟消息超时
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });
    }
}
