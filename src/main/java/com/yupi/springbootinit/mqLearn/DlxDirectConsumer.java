package com.yupi.springbootinit.mqLearn;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.util.HashMap;
import java.util.Map;

public class DlxDirectConsumer {
  private static final String EXCHANGE_NAME = "direct-exchange";
  private static final String DEAD_EXCHANGE_NAME = "dlx-direct-exchange";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    // 指定死信队列参数
    Map<String, Object> args = new HashMap<>();
    // 要绑定到哪个交换机
    args.put("x-dead-letter-exchange", DEAD_EXCHANGE_NAME);
    // 指定死信要转发到哪个死信队列
    args.put("x-dead-letter-routing-key", "waibao");


    channel.exchangeDeclare(EXCHANGE_NAME, "direct");
    String queueName1 = "王";
    channel.queueDeclare(queueName1,false,false,false,args);
    channel.queueBind(queueName1, EXCHANGE_NAME, "white");


    args.put("x-dead-letter-routing-key", "laoban");
    String queueName2 = "李";
    channel.queueDeclare(queueName2,false,false,false,args);
    channel.queueBind(queueName2, EXCHANGE_NAME, "black");

    System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

    DeliverCallback deliverCallback1 = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), "UTF-8");
        System.out.println(" [王] Received '" + message + "'");
        channel.basicNack(delivery.getEnvelope().getDeliveryTag(),false,false);
    };

    DeliverCallback deliverCallback2 = (consumerTag, delivery) -> {
      String message = new String(delivery.getBody(), "UTF-8");
      System.out.println(" [李] Received '" + message + "'");
      channel.basicNack(delivery.getEnvelope().getDeliveryTag(),false,false);
    };

    channel.basicConsume(queueName1, false, deliverCallback1, consumerTag -> { });
    channel.basicConsume(queueName2, false, deliverCallback2, consumerTag -> { });
  }
}
