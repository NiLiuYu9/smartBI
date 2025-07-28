package com.yupi.springbootinit.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.mapper.ChartMapper;
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
}




