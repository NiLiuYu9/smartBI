package com.yupi.springbootinit.mq;

import cn.hutool.core.util.StrUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

// 使用@Component注解标记该类为一个组件，让Spring框架能够扫描并将其纳入管理
@Component
// 使用@Slf4j注解生成日志记录器
@Slf4j
public class MyMessageConsumer {

    @Autowired
    private ChartService chartService;

    @Autowired
    private AiManager aiManager;

    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 接收消息的方法
     *
     * @param message     接收到的消息内容，是一个字符串类型
     * @param channel  消息所在的通道，可以通过该通道与 RabbitMQ 进行交互，例如手动确认消息、拒绝消息等
     * @param deliveryTag 消息的投递标签，用于唯一标识一条消息
     */
    // 使用@RabbitListener注解指定要监听的队列名称为"code_queue"，并设置消息的确认机制为手动确认
    @RabbitListener(queues = {"code_queue"}, ackMode = "MANUAL")
    // @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag是一个方法参数注解,用于从消息头中获取投递标签(deliveryTag),
    // 在RabbitMQ中,每条消息都会被分配一个唯一的投递标签，用于标识该消息在通道中的投递状态和顺序。通过使用@Header(AmqpHeaders.DELIVERY_TAG)注解,可以从消息头中提取出该投递标签,并将其赋值给long deliveryTag参数。
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("receiveMessage message = {}", message);
        if (StrUtil.isEmpty(message)) {
            safeNack(channel, deliveryTag);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        String[] parameters = message.split("/\\|");
        long userId = Long.parseLong(parameters[0]);
        long chartId = Long.parseLong(parameters[1]);

        Chart chart = chartService.getById(chartId);
        String status = chart.getStatus();
        // 幂等检查：若已是最终状态则直接ACK
        if (status.equals("succeed")) {
            safeAck(channel, deliveryTag);
            return;
        }

        Chart updateChart = new Chart();
        boolean updateFlag;
        updateChart.setId(chartId);
        String askMessage = parameters[2];
        if (status.equals("wait")||status.equals("failed")) {
            updateChart.setStatus("running");
            updateFlag = chartService.updateById(updateChart);
            if (!updateFlag) {
                safeNack(channel, deliveryTag);
                handleChartUpdateError(chartId, "更新图表running状态失败");
                return;
            }
        }
        String result = aiManager.doChat(askMessage);
        String[] results = result.split("【【【【");
        if (results.length < 3) {
            safeNack(channel, deliveryTag);
            handleChartUpdateError(chartId, "AI生成错误");
            return;
        }
        String genChart = results[1].trim();
        String genResult = results[2];
        updateChart.setGenChart(genChart);
        updateChart.setGenResult(genResult);
        updateChart.setStatus("succeed");
        updateFlag = chartService.updateById(updateChart);


        if (!updateFlag) {
            safeNack(channel, deliveryTag);
            handleChartUpdateError(chartId, "更新图表succeed状态失败");
            return;
        }
        updateChart.setExecMessage(null);
        chartService.updateById(updateChart);
        safeAck(channel, deliveryTag);
        String userVersionKey = userId + ":chartCache:ver";
        if (redisTemplate.hasKey(userVersionKey)) {
            redisTemplate.opsForValue().increment(userVersionKey);
        }
        log.info("图表{}生成成功",chartId);



    }

    private void safeAck(Channel channel, long tag) {
        try {
            channel.basicAck(tag, false);
        } catch (IOException e) {
            log.error("消息确认失败: ack", e);
        }
    }

    private void safeNack(Channel channel, long tag) {
        try {
            channel.basicNack(tag, false, false);
        } catch (IOException e) {
            log.error("消息拒绝失败: nack", e);
        }
    }

    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("数据库更新status错误");
        }


    }

}
