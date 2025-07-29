package com.yupi.springbootinit.mqLearn;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Scanner;

public class DirectProducer {

  private static final String EXCHANGE_NAME = "direct-exchange";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    try (Connection connection = factory.newConnection();
         Channel channel = connection.createChannel()) {
        //创建交换机
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");

        Scanner scanner = new Scanner(System.in);
        int i = 0;
        while (scanner.hasNext()){
            String message = scanner.next();
            if (i++%2==0){
                channel.basicPublish(EXCHANGE_NAME, "white", null, message.getBytes("UTF-8"));
            }else {
                channel.basicPublish(EXCHANGE_NAME, "black", null, message.getBytes("UTF-8"));
            }
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
  }
}
