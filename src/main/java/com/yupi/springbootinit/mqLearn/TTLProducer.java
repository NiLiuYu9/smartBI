package com.yupi.springbootinit.mqLearn;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
/*
* 如果消息已经被消费者接收，消息超过过期时间也不会过期
* */
public class TTLProducer {
    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
        // 创建一个ConnectionFactory对象，这个对象可以用于创建到RabbitMQ服务器的连接
        ConnectionFactory factory = new ConnectionFactory();
        // 设置ConnectionFactory的主机名为"localhost"，这表示我们将连接到本地运行的RabbitMQ服务器
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection();
             // 通过已建立的连接创建一个新的频道
             Channel channel = connection.createChannel()) {
            Map<String, Object> args = new HashMap<>();
            //设置队列过期时间
            args.put("x-message-ttl",5000);

            channel.queueDeclare(QUEUE_NAME, false, false, false, args);
            // 创建要发送的消息，这里我们将要发送的消息内容设置为"Hello World!"
            String message = "Hello World!";
            //设置消息过期时间,如果都设置，二者取数小的
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().expiration("60000")
                    .build();for (int i=0;i<4;i++) {
                channel.basicPublish("", QUEUE_NAME, properties, message.getBytes(StandardCharsets.UTF_8));
            }
            // 使用channel.basicPublish方法将消息发布到指定的队列中。这里我们指定的队列名为"hello"
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}
