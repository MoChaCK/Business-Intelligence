package com.ck.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;


@SpringBootTest
class ChartMapperTest {

    @Resource
    private ChartMapper chartMapper;

    @Test
    void testQueryData(){
        String charId = "1767740774939705346";
        String sql = String.format("select * from chart_%s", charId);
        List<Map<String, Object>> maps = chartMapper.queryChartData(sql);
        System.out.println(maps);
    }
}