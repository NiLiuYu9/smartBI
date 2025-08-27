package com.yupi.springbootinit.model.dto.chart;

import com.yupi.springbootinit.model.entity.Chart;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartCachePageData implements Serializable {
    private List<Chart> data;
    private long total;
}
