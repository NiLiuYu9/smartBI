package com.yupi.springbootinit.manager;

import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.SSEResponseModel;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.lkeap.v20240522.LkeapClient;
import com.tencentcloudapi.lkeap.v20240522.models.ChatCompletionsRequest;
import com.tencentcloudapi.lkeap.v20240522.models.ChatCompletionsResponse;
import com.tencentcloudapi.lkeap.v20240522.models.Message;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiManager {
    @Autowired
    private LkeapClient lkeapClient;

    public String doChat(String prompt) {
        try {
            ChatCompletionsRequest req = new ChatCompletionsRequest();
            req.setModel("deepseek-v3");
            req.setStream(false);
            req.setTemperature(0.6f);
            Message message = new Message();
            message.setRole("user");
            message.setContent(prompt);
            Message[] messages = new Message[1];
            messages[0] = message;
            req.setMessages(messages);
            ChatCompletionsResponse resp = lkeapClient.ChatCompletions(req);
            String result = resp.getChoices()[0].getMessage().getContent();

            return result;

        } catch (
                TencentCloudSDKException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
