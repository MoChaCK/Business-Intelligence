package com.ck.bimq;

import cn.hutool.core.util.StrUtil;
import com.ck.common.ErrorCode;
import com.ck.constant.ChartStatusConstant;
import com.ck.exception.BusinessException;
import com.ck.manager.AiManager;
import com.ck.model.entity.Chart;
import com.ck.service.ChartService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.ck.constant.BiMqConstant.BI_QUEUE_NAME;
import static com.ck.constant.CommonConstant.BI_MODEL_ID;

@Component
@Slf4j
public class BiMessageConsumer {

    @Resource
    private AiManager aiManager;

    @Resource
    private ChartService chartService;

    /**
     * 接收消息并处理
     */
    @SneakyThrows
    @RabbitListener(queues = {BI_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        log.info("receiveMessage message = {}", message);
        if(StrUtil.isBlank(message)){
            // 消息为空，拒绝消息
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        // 图表查询为空，拒绝消息
        if(chart == null){
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图表为空");
        }
        // 先修改图表任务状态为“执行中”；执行成功后，状态修改为“成功”；执行失败后，状态改为“失败”；记录任务失败信息
        // 修改图表任务状态为“执行中”
        Chart updateRunningChart = new Chart();
        updateRunningChart.setId(chart.getId());
        updateRunningChart.setStatus(ChartStatusConstant.CHART_STATE_RUNNING);
        boolean success1 = chartService.updateById(updateRunningChart);
        if(!success1){
            // 操作数据库失败，处理异常
            handleChartUpdateError(chartId, "更新图表{执行中}状态失败");
        }
        // 处理生成图表任务
        String result = aiManager.doChat(BI_MODEL_ID, buildUserInput(chart));
        String[] strings = result.split("【【【【【");
        if(strings.length < 3){ // 数组应当有三个元素: 空、前端代码、分析结果
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }
        String genChart = strings[1].trim();
        String genResult = strings[2].trim();

        // 将生成的图表插入到数据库，并将图表状态修改为“成功”
        Chart updateSucceedChart = new Chart();
        updateSucceedChart.setId(chart.getId());
        updateSucceedChart.setGenChart(genChart);
        updateSucceedChart.setGenResult(genResult);
        updateSucceedChart.setStatus(ChartStatusConstant.CHART_STATE_SUCCEED);
        boolean success2 = chartService.updateById(updateSucceedChart);
        if(!success2){
            // 操作数据库失败，处理异常
            handleChartUpdateError(chart.getId(), "更新图表{成功}状态失败");
        }
        // 消息确认
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 更改图表状态失败的处理
     * @param chartId
     * @param exeMessage
     */
    private void handleChartUpdateError(long chartId, String exeMessage) {
        Chart updateFailedChart = new Chart();
        updateFailedChart.setId(chartId);
        updateFailedChart.setExecMessage(exeMessage);
        updateFailedChart.setStatus(ChartStatusConstant.CHART_STATE_FAILED);
        boolean updateResult = chartService.updateById(updateFailedChart);
        if(!updateResult){
            log.error("更新图表{失败}状态失败" + chartId + "," + exeMessage);
        }
    }

    /**
     * 构建用户输入
     */
    private String buildUserInput(Chart chart){
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();

        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if(StrUtil.isNotBlank(chartType)){
            userGoal = "，请使用" + goal;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        // 压缩文件数据，并将数据结果拼接到用户输入
        userInput.append(csvData).append("\n");
        return userInput.toString();
    }
}
