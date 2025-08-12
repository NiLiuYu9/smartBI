package com.yupi.springbootinit.model.vo;

import lombok.Data;

import java.util.Date;
@Data

public class ChartMetaVO{
    private long chartId;

    private String status;
    /**
     * 图表名称
     */
    private String name;

    /**
     * 分析目标
     */
    private String goal;
}
