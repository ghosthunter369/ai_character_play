package com.character.config.ars;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.ars")
public class ARSConfig {
    private String accessKeyId;
    private String appId;
    private String accessKeySecret;
}