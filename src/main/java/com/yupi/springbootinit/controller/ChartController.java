package com.yupi.springbootinit.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.DeleteRequest;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.*;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.ChartResp;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.RedisPrefix;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.model.vo.ChartMetaVO;
import com.yupi.springbootinit.model.vo.GenResultVo;
import com.yupi.springbootinit.mq.MyMessageProducer;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.ExcelUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 帖子接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Autowired
    private AiManager aiManager;

    @Autowired
    private RedisLimiterManager redisLimiterManager;

    @Autowired
    private UserService userService;


    @Autowired
    private MyMessageProducer messageProducer;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;



    // region 增删改查
    /**
     * @RequestParam接收表单和键值对参数，本质是键值对
     * @RequestBody接收请求体中的参数，本质是字节流
     * 新增图表方法，不对外暴露
     *
     * @param chart
     * @return
     */
    @PostMapping("/add")
    public long addChart(@RequestBody Chart chart) {
        if (chart == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图表空数据");
        }
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图表保存失败");
        return chart.getId();
    }


    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 智能分析
     */
    @Transactional
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChatByAi(@RequestPart("file") MultipartFile multipartFile,
                                                GenChatByAiRequest genChatByAiRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (null == loginUser) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Long userId = loginUser.getId();
        redisLimiterManager.doRateLimit(userId);
        String name = genChatByAiRequest.getName();
        String goal = genChatByAiRequest.getGoal();
        String chartType = genChatByAiRequest.getChartType();
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        String csvData = ExcelUtils.excelToCSV(multipartFile);
        //组装prompt
        String askMessage = chartService.assembleMessage2Ai(goal,csvData,chartType);
        String result = aiManager.doChat(askMessage);
        System.out.println(result);
        String[] results = result.split("【【【【");
        if (results.length < 3) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI生成错误");
        }
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(results[1].trim());
        biResponse.setGenResult(results[2]);
        ChartResp chartResp = new ChartResp();
        chartResp.setName(name);
        chartResp.setGoal(goal);
        chartResp.setChartData(csvData);
        chartResp.setChartType(chartType);
        chartResp.setGenChart(biResponse.getGenChart());
        chartResp.setGenResult(biResponse.getGenResult());
        chartResp.setUserId(loginUser.getId());

        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartType(chartType);
        chart.setGenChart(biResponse.getGenChart());
        chart.setGenResult(biResponse.getGenResult());
        chart.setStatus("succeed");
        chart.setUserId(loginUser.getId());
        long chartId = this.addChart(chart);
        boolean saveDataFlag = chartService.createChartDataTable(csvData, chartId);
        ThrowUtils.throwIf(!saveDataFlag, ErrorCode.SYSTEM_ERROR, "保存图表数据失败");

        return ResultUtils.success(biResponse);

    }

    @Transactional
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChatByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                    GenChatByAiRequest genChatByAiRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (null == loginUser) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Long userId = loginUser.getId();
        //限流
        redisLimiterManager.doRateLimit(userId);
        String name = genChatByAiRequest.getName();
        String goal = genChatByAiRequest.getGoal();
        String chartType = genChatByAiRequest.getChartType();
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");


        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartType(chartType);
//        chart.setGenChart(biResponse.getGenChart());
//        chart.setGenResult(biResponse.getGenResult());
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        long chartId = this.addChart(chart);
        String csvData = ExcelUtils.excelToCSV(multipartFile);
        boolean saveDataFlag = chartService.createChartDataTable(csvData, chartId);
        ThrowUtils.throwIf(!saveDataFlag, ErrorCode.SYSTEM_ERROR, "保存图表数据失败");


        final String prompt = "请严格按照下面的输出格式生成结果，且不得添加任何多余内容（例如无关文字、注释、代码块标记或反引号）：\n" +
                "\n" +
                "【【【【 {\n" +
                "生成 Echarts V5 的 option 配置对象 JSON 代码，要求为合法 JSON 格式,不要有转义符和{换行符}且不含任何额外内容(如注释或多余引号)} 【【【【 结论： {\n" +
                "提供对数据的详细分析结论，内容应尽可能准确、详细，不允许添加其他无关文字或注释 }\n" +
                "\n" +
                "示例： 输入： 分析需求:分析网站用户增长情况 图表类型:柱状图 数据:日期,用户数 1号,10 2号,20 3号,30\n" +
                "\n" +
                "期望输出： 【【【【 { \"title\": { \"text\": \"分析网站用户增长情况\" }, \"xAxis\": { \"type\": \"category\", \"data\": [\"1号\", \"2号\", \"3号\"] }, \"yAxis\": { \"type\": \"value\" }, \"series\": [ { \"name\": \"用户数\", \"type\": \"bar\", \"data\": [10, 20, 30] } ] } 【【【【 结论： 从数据看，网站用户数由1号的10人增长到2号的20人，再到3号的30人，呈现出明显的上升趋势。这表明在这段时间内网站用户吸引力增强，可能与推广活动、内容更新或其他外部因素有关。\n";


        StringBuilder userInput = new StringBuilder();
        userInput.append(prompt).append("\n");

        userInput.append("分析需求:" + goal).append("\n").append("图表类型:" + chartType).append("\n").append("数据:" + csvData).append("\n");
        String askMessage = userInput.toString();

        String messageQueueParameter = userId+"/|"+chartId + "/|" + askMessage;
        try{
            messageProducer.sendMessage(messageQueueParameter);
        }catch (AmqpException e){
            log.error(e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息队列发送失败");
        }
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chartId);
//        biResponse.setGenChart(results[1].trim());
//        biResponse.setGenResult(results[2]);
        //更新版本号使用户感知图表更新
        String userVersionKey = userId + ":chartCache:ver";
        redisTemplate.opsForValue().increment(userVersionKey);

        return ResultUtils.success(biResponse);

    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long chartId = chartUpdateRequest.getId();
        String chartData = chartUpdateRequest.getChartData();
        // 判断是否存在
        Chart oldChart = chartService.getById(chartId);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        boolean b = chartService.dropChartDataTable(chartId);
        ThrowUtils.throwIf(b, ErrorCode.SYSTEM_ERROR, "删除原表数据失败");
        chartService.createChartDataTable(chartData, chartId);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        List<Map<String, String>> chartDataList = chartService.getChartDataById(id);
        String data = chartService.convertToCSV(chartDataList);
        ChartResp chartResp = new ChartResp();
        BeanUtils.copyProperties(chart, chartResp);

        chartResp.setChartData(data);

        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {


        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();

        //读缓存
        String userVersionKey = userId + ":chartCache:ver";
        //先读版本号
        Integer cacheVersion = (Integer) redisTemplate.opsForValue().get(userVersionKey);
        if (null == cacheVersion){
            cacheVersion = 1;
            //写入首次缓存的版本号
            //随机ttl防止雪崩
            long VersionTtl = 600 + ThreadLocalRandom.current().nextInt(100);
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(userVersionKey, cacheVersion,VersionTtl, TimeUnit.SECONDS);
            if (!ok){
                //读数据库
                cacheVersion=-1;
            }
        }
        String pageKey = userId + ":chartCache:page" + current + ":" + cacheVersion;
        //读当前版本号对应的分页
        ChartCachePageData cachePageData = (ChartCachePageData) redisTemplate.opsForValue().get(pageKey);

        Page<Chart> chartPage = new Page<>();

        //读分页为空说明缓存过期，重建
        if (null == cachePageData){

            chartQueryRequest.setUserId(userId);

            // 限制爬虫
            ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

            //防止缓存击穿
            String lockKey = "lock:"+pageKey;
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = false;
            try {
                acquired = lock.tryLock(100, TimeUnit.MILLISECONDS);
                //没拿到锁
                if (!acquired){
                    //短暂退避后再读一次缓存
                    Thread.sleep(60);
                    cachePageData = (ChartCachePageData) redisTemplate.opsForValue().get(pageKey);
                    //缓存仍然为null，读数据库
                    if (null == cachePageData){
                        chartPage = chartService.page(new Page<>(current, size),
                                getQueryWrapper(chartQueryRequest).orderByDesc(Chart::getCreateTime));
                        return ResultUtils.success(chartPage);
                    }
                }else {
                    //拿到锁，二次检验缓存防止重复重建
                    cacheVersion = (Integer) redisTemplate.opsForValue().get(userVersionKey);
                    pageKey = userId + ":chartCache:page" + current + ":" + cacheVersion;
                    cachePageData = (ChartCachePageData) redisTemplate.opsForValue().get(pageKey);
                    if (null == cachePageData) {
                        chartPage = chartService.page(new Page<>(current, size),
                                getQueryWrapper(chartQueryRequest).orderByDesc(Chart::getCreateTime));
                        cachePageData = new ChartCachePageData(chartPage.getRecords(), chartPage.getTotal());
                        cacheVersion = (Integer) redisTemplate.opsForValue().get(userVersionKey);
                        pageKey = userId + ":chartCache:page" + current + ":" + cacheVersion;
                        long PageTtl;
                        //防止缓存穿透
                        if (CollUtil.isEmpty(chartPage.getRecords())) {
                            PageTtl = 50 + ThreadLocalRandom.current().nextInt(60);
                        } else {
                            PageTtl = 600 + ThreadLocalRandom.current().nextInt(60);
                        }
                        redisTemplate.opsForValue().set(pageKey, cachePageData, PageTtl, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"查询我的图表异常");
            }finally {
                if (acquired && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }


        chartPage.setRecords(cachePageData.getData());
        chartPage.setCurrent(current);
        chartPage.setSize(size);
        chartPage.setTotal(cachePageData.getTotal());

        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private LambdaQueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        LambdaQueryWrapper<Chart> queryWrapper = new LambdaQueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();

        queryWrapper.eq(id != null && id > 0, Chart::getId, id);
        queryWrapper.eq(StringUtils.isNotBlank(goal), Chart::getGoal, goal);
        queryWrapper.like(StringUtils.isNotBlank(name), Chart::getName, name);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), Chart::getChartType, chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), Chart::getUserId, userId);
        queryWrapper.eq(Chart::getIsDelete, false);
        return queryWrapper;
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
