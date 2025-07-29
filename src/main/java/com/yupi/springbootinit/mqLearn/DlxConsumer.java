package com.yupi.springbootinit.mqLearn;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.util.concurrent.TimeoutException;


public class DlxConsumer {
    private static final String DEAD_EXCHANGE_NAME = "dlx-direct-exchange";

    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        //声明死信交换机
        channel.exchangeDeclare(DEAD_EXCHANGE_NAME,"direct");
        //把死信队列与死信交换机绑定
        String waibaoQueue = "waibao_queue";
        channel.queueDeclare(waibaoQueue,false,false,false,null);
        channel.queueBind(waibaoQueue,DEAD_EXCHANGE_NAME,"waibao");

        String laobanQueue = "laoban_queue";
        channel.queueDeclare(laobanQueue,false,false,false,null);
        channel.queueBind(laobanQueue,DEAD_EXCHANGE_NAME,"laoban");


        DeliverCallback deliverCallback1 = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [死信外包] Received '" + message + "'");
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(),false,false);
        };

        DeliverCallback deliverCallback2 = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [死信老板] Received '" + message + "'");
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(),false,false);
        };

        channel.basicConsume(waibaoQueue, false, deliverCallback1, consumerTag -> { });
        channel.basicConsume(laobanQueue, false, deliverCallback2, consumerTag -> { });
    }

}
