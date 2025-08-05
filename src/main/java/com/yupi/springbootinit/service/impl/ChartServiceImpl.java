package com.yupi.springbootinit.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.mapper.ChartMapper;
import com.yupi.springbootinit.utils.ExcelUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService {
    @Autowired
    private ChartMapper chartMapper;

    @Override
    public boolean createChartDataTable(String data, long chartId) {

        char lineSeparator = '\n';
        String fieldSeparator = ",";
        String headersCsv = StrUtil.subBefore(data, lineSeparator, false);
        String[] headerArray = headersCsv.split(fieldSeparator);
        String chartIdStr = String.valueOf(chartId);
        String tableName = "chart_" + chartIdStr;
        StringBuilder createSql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
        for (String header : headerArray) {
            createSql.append(header).append(" VARCHAR(80),");
        }
        createSql.setLength(createSql.length() - 1);
        createSql.append(")");
        int table = chartMapper.createTable(createSql.toString());

        List<String[]> rows = new ArrayList<>();
        String[] lines = data.split("\n");
        for (String line : lines) {
            rows.add(line.split(","));
        }
        int insertNum = chartMapper.batchInsert(chartId, rows.get(0), rows.subList(1, rows.size()));

        ThrowUtils.throwIf(table != 0 && insertNum == 0, ErrorCode.SYSTEM_ERROR, "图表数据保存失败");

        return true;
    }

    @Override
    public boolean dropChartDataTable(long chartId) {
        int i = chartMapper.dropTable(chartId);
        return i == 1;
    }

    @Override
    public List<Map<String, String>> getChartDataById(long chartId) {
        return chartMapper.getDataByChartId(chartId);
    }

    @Override
    public String convertToCSV(List<Map<String, String>> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 获取列名
        Set<String> keys = data.get(0).keySet();
        List<String> columns = new ArrayList<>(keys);

        // 写表头
        sb.append(String.join(",", columns)).append("\n");

        // 写每行数据
        for (Map<String, String> row : data) {
            List<String> values = new ArrayList<>();
            for (String col : columns) {
                String value = row.getOrDefault(col, "");
                values.add(value);
            }
            sb.append(String.join(",", values)).append("\n");
        }

        return sb.toString();
    }

    @Override
    public String assembleMessage2Ai(String goal, String csvData, String chartType) {
        final String prompt = "请严格按照下面的输出格式生成结果，且不得添加任何多余内容（例如无关文字、注释、代码块标记或反引号）：\n" +
                "\n" +
                "【【【【 {\n" +
                "生成 Echarts V5 的 option 配置对象 JSON 代码，要求为合法 JSON 格式,不要有转义符和{换行符}且不含任何额外内容(如注释或多余引号)} 【【【【 结论： {\n" +
                "提供对数据的详细分析结论，内容应尽可能准确、详细，不允许添加其他无关文字或注释 }\n" +
                "\n" +
                "示例： 输入： 分析需求:分析网站用户增长情况 图表类型:柱状图 数据:日期,用户数 1号,10 2号,20 3号,30\n" +
                "\n" +
                "期望输出： 【【【【 { \"title\": { \"text\": \"分析网站用户增长情况\" }, \"xAxis\": { \"type\": \"category\", \"data\": [\"1号\", \"2号\", \"3号\"] }, \"yAxis\": { \"type\": \"value\" }, \"series\": [ { \"name\": \"用户数\", \"type\": \"bar\", \"data\": [10, 20, 30] } ] } 【【【【 结论： 从数据看，网站用户数由1号的10人增长到2号的20人，再到3号的30人，呈现出明显的上升趋势。这表明在这段时间内网站用户吸引力增强，可能与推广活动、内容更新或其他外部因素有关。\n";

        return prompt + "\n" +
                "分析需求:" + goal + "\n" + "图表类型:" + chartType + "\n" + "数据:" + csvData + "\n";
    }
}




