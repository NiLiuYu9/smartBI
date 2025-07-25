package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.entity.Chart;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 *
 */
public interface ChartService extends IService<Chart> {
    public boolean createChartDataTable(String data,long chartId);
    public boolean dropChartDataTable(long chartId);
    public List<Map<String,String>> getChartDataById(long chartId);
}
