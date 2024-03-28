package com.ck.utils;

import cn.hutool.core.util.StrUtil;

/**
 * SQL 工具
 */
public class SqlUtils {

    public static boolean validSortField(String sortField){
        if(StrUtil.isBlank(sortField)){
            return false;
        }
        return !StrUtil.containsAny(sortField, "=", "(", ")", " ");
    }
}
