package com.character.util;

import com.character.config.ars.ARSConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.character.constant.ARSConstant.*;

/**
 * 语音转文字工具类
 */
@Component
public class ARSUtil {

    @Autowired
    private ARSConfig arsConfig;

    /**
     * 拿到完整ws路径
     */
    public String getWsUrl() {
        Map<String, String> params = new ARSUtil().generateAuthParams();
        String paramsStr = buildParamsString(params);
        return baseWsUrl + "?" + paramsStr;

    }

    /**
     * 构建参数字符串
     */
    public static String buildParamsString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            try {
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name())).append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException e) {
                // UTF-8编码总是支持的
                e.printStackTrace();
            }
            first = false;
        }
        return sb.toString();
    }

    /**
     * 生成鉴权参数
     */
    private Map<String, String> generateAuthParams() {
        Map<String, String> params = new TreeMap<>();  // TreeMap保证字典序排序

        // 固定参数
        params.put("audio_encode", AUDIO_ENCODE);
        params.put("lang", LANG);
        params.put("samplerate", SAMPLERATE);

        // 动态参数
        params.put("accessKeyId", arsConfig.getAccessKeyId());
        params.put("appId", arsConfig.getAppId());
        params.put("uuid", UUID.randomUUID().toString().replaceAll("-", ""));
        params.put("utc", getUtcTime());
        // 计算签名
        String signature = calculateSignature(params);
        params.put("signature", signature);
        return params;
    }

    /**
     * 生成UTC时间字符串（yyyy-MM-dd'T'HH:mm:ss+0800）
     */
    private String getUtcTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(new Date());
    }

    /**
     * 计算HMAC-SHA1签名
     */
    private String calculateSignature(Map<String, String> params) {
        try {
            // 构建基础字符串
            StringBuilder baseStr = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // 跳过signature参数
                if ("signature".equals(key)) continue;
                // 过滤空值
                if (value == null || value.trim().isEmpty()) continue;

                if (!first) {
                    baseStr.append("&");
                }
                baseStr.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name())).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
                first = false;
            }

            // HMAC-SHA1计算
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(arsConfig.getAccessKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] signBytes = mac.doFinal(baseStr.toString().getBytes(StandardCharsets.UTF_8));

            // Base64编码
            return Base64.getEncoder().encodeToString(signBytes);
        } catch (Exception e) {
            throw new RuntimeException("计算签名失败", e);
        }
    }
}