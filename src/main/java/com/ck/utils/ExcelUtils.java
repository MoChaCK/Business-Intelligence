package com.ck.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel相关工具类
 */
@Slf4j
public class ExcelUtils {

    public static String excelToCsv(MultipartFile multipartFile){

        List<Map<Integer, String>> list = null;
        try {
            File file = ResourceUtils.getFile("classpath:网站数据测试.xlsx");
            list = EasyExcel.read(file)
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
            System.out.println(list);
        } catch (IOException e) {
            log.error("表格处理错误", e);
        }
        if(CollUtil.isEmpty(list)){
            return "";
        }
        // 转换为csv
        StringBuilder stringBuilder = new StringBuilder();
        // 读取表头
        LinkedHashMap<Integer, String> headerMap = (LinkedHashMap) list.get(0);
        List<String> headerList = headerMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
        stringBuilder.append(StrUtil.join(",", headerList)).append("\n");
        // 读取数据
        for (int i = 1; i < list.size(); i++) {
            LinkedHashMap<Integer, String> dataMap = (LinkedHashMap) list.get(i);
            List<String> dataList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
            stringBuilder.append(StrUtil.join(",", dataList)).append("\n");
        }
        System.out.println(stringBuilder);
        return stringBuilder.toString();
    }

    /**
     * 测试excel转换成csv
     */
    public static void main(String[] args) {
        excelToCsv(null);
    }

}
