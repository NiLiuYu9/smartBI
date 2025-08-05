package com.yupi.springbootinit.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class MyMessageProducer {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    /**
     * 发送消息的方法
     *
     * @param message    消息内容，要发送的具体消息
     */
    public void sendMessage(String message) {
        // 使用rabbitTemplate的convertAndSend方法将消息发送到指定的交换机和路由键
        rabbitTemplate.convertAndSend("code_exchange", "my_routingKey", message);
    }

}
