package com.ck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ck.service.ChartService;
import com.ck.model.entity.Chart;
import com.ck.mapper.ChartMapper;
import org.springframework.stereotype.Service;

/**
* @author 15925
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2024-03-11 09:46:41
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService {

}




