package com.ck.api;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;
import com.ck.common.ErrorCode;
import com.ck.exception.BusinessException;

/**
 * AI对话，需要自己创建请求响应对象
 */
public class OpenAIAPI {

    public CreateChatCompletionResponse createChatCompletionResponse(CreateChatCompletionRequest request, String openAiApiKey){
        if(StrUtil.isBlank(openAiApiKey)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未传入 openAiApiKey");
        }
        String url = "openai的地址";
        String json = JSONUtil.toJsonStr(request);
        String result = HttpRequest.post(url)
                .header("Authorization", "")
                .body(json)
                .execute()
                .body();
        return JSONUtil.toBean(result, CreateChatCompletionResponse.class);
    }
}
