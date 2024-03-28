package com.ck.mapper;

import com.ck.model.entity.Chart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;
import java.util.Map;

/**
* @author 15925
* @description 针对表【chart(图表信息表)】的数据库操作Mapper
* @createDate 2024-03-11 09:46:41
* @Entity generator.domain.Chart
*/
public interface ChartMapper extends BaseMapper<Chart> {

    List<Map<String, Object>> queryChartData(String querySql);
}




