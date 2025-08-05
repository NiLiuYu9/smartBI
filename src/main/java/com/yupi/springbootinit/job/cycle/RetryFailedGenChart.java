package com.yupi.springbootinit.job.cycle;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.model.entity.Chart;

import java.util.List;
import java.util.Map;

import com.yupi.springbootinit.mq.MyMessageProducer;
import com.yupi.springbootinit.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟自动检测有没有状态不为succeed的图表，将其加入消息队列
 */
// todo 取消注释开启任务
@Component
@Slf4j
public class RetryFailedGenChart {

    @Autowired
    private ChartService chartService;

    @Autowired
    private MyMessageProducer messageProducer;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        log.info("定时处理失败任务执行");
        LambdaQueryWrapper<Chart> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        //ExecMessage非空说明已经执行过一次 且状态为非succeed说明执行失败
        lambdaQueryWrapper.ne(Chart::getStatus,"succeed");
        List<Chart> failedChartList = chartService.list(lambdaQueryWrapper);
        failedChartList.forEach(
                (faieldChart)-> {
                    if (StrUtil.isBlank(faieldChart.getGenChart())) {
                        Long chartId = faieldChart.getId();
                        List<Map<String, String>> chartListData = chartService.getChartDataById(chartId);
                        String csvData = chartService.convertToCSV(chartListData);
                        String askMessage = chartService.assembleMessage2Ai(faieldChart.getGoal(), csvData, faieldChart.getChartType());
                        String parameters = chartId + "/|" + askMessage;
                        messageProducer.sendMessage(parameters);
                    }else {
                        faieldChart.setStatus("succeed");
                        chartService.updateById(faieldChart);
                    }
                });
    }
}
