package com.ck.model.dto.chart;

import lombok.Data;

import java.io.Serializable;

/**
 * 删除请求，删除用户或者图表
 */
@Data
public class ChartDeleteRequest implements Serializable {

    /**
     * id
     */
    private long id;

    private static final long serialVersionUID = 1L;
}
