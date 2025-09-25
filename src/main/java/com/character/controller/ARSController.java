package com.character.controller;

import com.character.service.RealtimeASRService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * 实时语音识别控制器
 * 提供REST API接口用于语音识别服务
 */
@RestController
@RequestMapping("/api/asr")
@CrossOrigin(origins = "*")
public class ARSController {

    private static final Logger logger = LoggerFactory.getLogger(ARSController.class);

    @Autowired
    private RealtimeASRService realtimeASRService;

    /**
     * 启动实时语音识别会话
     * @return 流式识别结果 (Server-Sent Events)
     */
    @PostMapping(value = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> startRealtimeASR() {
        String sessionId = UUID.randomUUID().toString();
        logger.info("启动语音识别会话，sessionId: {}", sessionId);

        return realtimeASRService.startRealtimeASR(sessionId)
                .map(result -> "data: " + result + "\n\n")
                .doOnSubscribe(subscription -> {
                    logger.info("客户端订阅语音识别流，sessionId: {}", sessionId);
                })
                .doOnCancel(() -> {
                    logger.info("客户端取消语音识别流，sessionId: {}", sessionId);
                })
                .doOnComplete(() -> {
                    logger.info("语音识别流完成，sessionId: {}", sessionId);
                });
    }

    /**
     * 启动指定会话ID的实时语音识别
     * @param sessionId 会话ID
     * @return 流式识别结果 (Server-Sent Events)
     */
    @PostMapping(value = "/start/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> startRealtimeASRWithSessionId(@PathVariable String sessionId) {
        logger.info("启动指定会话语音识别，sessionId: {}", sessionId);

        return realtimeASRService.startRealtimeASR(sessionId)
                .map(result -> "data: " + result + "\n\n")
                .doOnSubscribe(subscription -> {
                    logger.info("客户端订阅语音识别流，sessionId: {}", sessionId);
                })
                .doOnCancel(() -> {
                    logger.info("客户端取消语音识别流，sessionId: {}", sessionId);
                })
                .doOnComplete(() -> {
                    logger.info("语音识别流完成，sessionId: {}", sessionId);
                });
    }

    /**
     * 发送音频数据
     * @param sessionId 会话ID
     * @param audioData 音频数据 (PCM格式)
     * @return 操作结果
     */
    @PostMapping("/send/{sessionId}")
    public String sendAudioData(@PathVariable String sessionId,
                                @RequestBody byte[] audioData) {
        try {
            logger.debug("接收音频数据，sessionId: {}, 数据长度: {} 字节", sessionId, audioData.length);

            ByteBuffer buffer = ByteBuffer.wrap(audioData);
            realtimeASRService.sendAudioData(sessionId, buffer);

            return "音频数据发送成功";
        } catch (Exception e) {
            logger.error("发送音频数据失败，sessionId: " + sessionId, e);
            return "音频数据发送失败: " + e.getMessage();
        }
    }

    /**
     * 结束语音识别会话
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @PostMapping("/end/{sessionId}")
    public String endASR(@PathVariable String sessionId) {
        try {
            logger.info("结束语音识别会话，sessionId: {}", sessionId);
            realtimeASRService.endASR(sessionId);
            return "语音识别会话已结束";
        } catch (Exception e) {
            logger.error("结束语音识别会话失败，sessionId: " + sessionId, e);
            return "结束会话失败: " + e.getMessage();
        }
    }

    /**
     * 获取会话状态（可选功能）
     * @param sessionId 会话ID
     * @return 会话状态信息
     */
    @GetMapping("/status/{sessionId}")
    public String getSessionStatus(@PathVariable String sessionId) {
        // TODO: 实现会话状态查询
        logger.info("查询会话状态，sessionId: {}", sessionId);
        return "会话状态查询功能待实现";
    }

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public String healthCheck() {
        return "语音识别服务运行正常";
    }

    /**
     * 获取服务信息
     * @return 服务信息
     */
    @GetMapping("/info")
    public String getServiceInfo() {
        return "实时语音识别服务 - 基于讯飞语音识别API";
    }
}