package com.yupi.springbootinit.mapper;

import com.yupi.springbootinit.model.entity.Chart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @Entity com.yupi.springbootinit.model.entity.Chart
 */
public interface ChartMapper extends BaseMapper<Chart> {
    public int createTable(String createSql);
    public int dropTable(long chartId);
    public int batchInsert(long chartId,String[] headers,List<String[]> rows);
    public List<Map<String,String>> getDataByChartId(long chartId);
}




