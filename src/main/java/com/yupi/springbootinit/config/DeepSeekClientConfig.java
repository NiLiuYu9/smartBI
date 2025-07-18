package com.yupi.springbootinit.config;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;

import com.tencentcloudapi.lkeap.v20240522.LkeapClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "deepseek.client")
@Data
public class DeepSeekClientConfig {

    private String secretId;

    private String secretKey;


    @Bean
    public LkeapClient deepSeekClient(){

        Credential cred = new Credential(secretId, secretKey);

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("lkeap.tencentcloudapi.com");

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
//        clientProfile.setSignMethod("TC3-HMAC-SHA256");

        LkeapClient client = new LkeapClient(cred, "ap-shanghai", clientProfile);
        return client;

    }


}
