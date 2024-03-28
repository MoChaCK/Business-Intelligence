package com.ck.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ck.bimq.BiMessageProducer;
import com.ck.common.BaseResponse;
import com.ck.model.dto.chart.ChartDeleteRequest;
import com.ck.common.ErrorCode;
import com.ck.common.ResultUtils;
import com.ck.constant.ChartStatusConstant;
import com.ck.constant.CommonConstant;
import com.ck.exception.BusinessException;
import com.ck.exception.ThrowUtils;
import com.ck.manager.AiManager;
import com.ck.manager.RedisLimiterManager;
import com.ck.model.dto.chart.ChartAddRequest;
import com.ck.model.dto.chart.ChartQueryRequest;
import com.ck.model.dto.chart.GenChartByAiRequest;
import com.ck.model.entity.Chart;
import com.ck.model.entity.User;
import com.ck.model.vo.BiResponse;
import com.ck.service.ChartService;
import com.ck.service.UserService;
import com.ck.utils.ExcelUtils;
import com.ck.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static com.ck.constant.CommonConstant.BI_MODEL_ID;

@RestController
@RequestMapping("chart")
@Slf4j
public class ChartController {

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private ChartService chartService;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiMessageProducer biMessageProducer;


    /**
     * 创建图表
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request){
        if(chartAddRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
        Long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }


    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody ChartDeleteRequest chartDeleteRequest, HttpServletRequest request){
        if(chartDeleteRequest == null || chartDeleteRequest.getId() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = chartDeleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅限本人可以删除
        User loginUser = userService.getLoginUser(request);
        if(!Objects.equals(oldChart.getUserId(), loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean remove = chartService.removeById(id);
        ThrowUtils.throwIf(!remove, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(remove);

    }


    /**
     * 智能分析(同步)
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAI(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        //1. 校验判断
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //1.1 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StrUtil.isBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        //1.2 文件校验
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        //1.3 文件大小校验
        long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1M");
        //1.4 文件后缀名校验
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validSuffix = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validSuffix.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        //2. 给每个用户限流
        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";

        //3. 构造用户输入
        //3.1 模型id
        long biModelId = 1659171950288818178L; //AI模型的ID
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        //3.2 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        //3.3 拼接分析目标
        String userGoal = goal;
        if(StrUtil.isNotBlank(chartType)){
            userGoal = "，请使用" + goal;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        //3.4 压缩文件数据，并拼接到用户输入中
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        //4. 图表生成
        String result = aiManager.doChat(biModelId, userInput.toString());
        String[] strings = result.split("【【【【【");
        if(strings.length < 3){ // 数组应当有三个元素: 空、前端代码、分析结果
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }

        String genChart = strings[1].trim();
        String genResult = strings[2].trim();

        //5. 生成结果插入到数据库
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // TODO：新建数据表，将图表数据分表存储，提高查询灵活性

        //6. 返回结果
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

    }

    /**
     * 智能分析(异步)
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAIAsync(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        //1. 校验判断
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //1.1 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StrUtil.isBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        //1.2 文件校验
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        //1.3 文件大小校验
        long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1M");
        //1.4 文件后缀名校验
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validSuffix = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validSuffix.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        //2.给每个用户限流
        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        //3. 构造用户输入
        //3.1 模型id
        long biModelId = BI_MODEL_ID; //AI模型的ID
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        //3.2 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        //3.3 拼接分析目标
        String userGoal = goal;
        if(StrUtil.isNotBlank(chartType)){
            userGoal = "，请使用" + goal;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        //3.4 压缩文件数据，并将数据结果拼接到用户输入
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        //4. 先将图表信息插入到数据库
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus(ChartStatusConstant.CHART_STATE_WAIT);
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        //TODO 建议处理任务队列满了后，抛异常的情况
        //5. 异步处理生成图表任务
        CompletableFuture.runAsync(() -> {
            // 先修改图表任务状态为“执行中”；执行成功后，状态修改为“成功”；执行失败后，状态改为“失败”；记录任务失败信息
            //5.1 修改图表任务状态为“执行中”
            Chart updateRunningChart = new Chart();
            updateRunningChart.setId(chart.getId());
            updateRunningChart.setStatus(ChartStatusConstant.CHART_STATE_RUNNING);
            boolean success1 = chartService.updateById(updateRunningChart);
            if(!success1){
                // 操作数据库失败，处理异常
                handleChartUpdateError(chart.getId(), "更新图表{执行中}状态失败");
            }
            //5.2 处理生成图表任务
            String result = aiManager.doChat(biModelId, userInput.toString());
            String[] strings = result.split("【【【【【");
            if(strings.length < 3){ // 数组应当有三个元素: 空、前端代码、分析结果
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
            }
            String genChart = strings[1].trim();
            String genResult = strings[2].trim();

            //5.3 将生成的图表插入到数据库，并将图表状态修改为“成功”
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

        }, threadPoolExecutor);

        // TODO：新建数据表，将图表数据分表存储，提高查询灵活性

        //6. 返回结果
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

    }



    /**
     * 智能分析(异步消息队列)
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAIAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        //1. 校验判断
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //1.1 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StrUtil.isBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        //1.2 文件校验
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        //1.3 文件大小校验
        long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1M");
        //1.4 文件后缀名校验
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validSuffix = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validSuffix.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        //2.给每个用户限流
        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        //3. 构造用户输入
        //3.1 模型id
        long biModelId = BI_MODEL_ID; //AI模型的ID
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        //3.2 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        //3.3 拼接分析目标
        String userGoal = goal;
        if(StrUtil.isNotBlank(chartType)){
            userGoal = "，请使用" + goal;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        //3.4 压缩文件数据，并将数据结果拼接到用户输入
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        //4. 先将图表信息插入到数据库
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus(ChartStatusConstant.CHART_STATE_WAIT);
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        //5. 发送消息到异步消息队列
        biMessageProducer.sendMessage(String.valueOf(chart.getId()));

        //6. 返回结果
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

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
     * 分页获取列表（封装类）
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest, HttpServletRequest request){
        long current = chartQueryRequest.getCurrent();
        long pageSize = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, pageSize),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }


    /**
     * 分页获取当前用户创建的资源列表
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest, HttpServletRequest request){
        if(chartQueryRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long pageSize = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, pageSize),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }


    /**
     * 获取查询包装类
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest){
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if(chartQueryRequest == null){
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.eq(StrUtil.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StrUtil.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }
}
