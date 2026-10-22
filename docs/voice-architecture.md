# 语音对话时序与反馈

本轮实现采用按轮发言：录音期间不播放角色声音，点击结束发言后提交，支持用户主动打断。不是自动VAD、全双工或原生实时语音模型。

```mermaid
sequenceDiagram
    participant U as 用户/浏览器
    participant W as 鉴权WebSocket
    participant A as ASR网关
    participant D as MySQL
    participant L as LLM SSE
    participant T as TTS网关
    U->>W: 登录Cookie + appId/conversationId/供应商
    W-->>U: session.ready
    U->>W: 多帧16kHz PCM16（最多30秒）
    U->>W: audio.commit(requestId)
    W-->>U: turn.started(requestId,turnId)
    W->>A: WAV识别（可取消HTTP）
    A-->>W: 最终文字
    Note over U,W: text.submit可直接进入以下流程
    W-->>U: asr.final(turnId,text)
    W->>D: 按user/app/conversation读取历史
    W->>D: 保存本轮用户文字
    W->>L: system + 预算内历史 + 当前输入一次
    loop 模型逐块生成
        L-->>W: delta
        W-->>U: reply.delta
        opt 句号/换行/80字符边界
            W->>T: 短句合成（串行独立队列）
            T-->>W: 完整短句WAV
            W-->>U: audio.chunk(turnId,seq,mimeType,data)
            U->>U: 排队解码、逐段播放
        end
    end
    L-->>W: [DONE]
    W->>D: 仅完整成功时保存AI全文
    W-->>U: reply.done
    Note over W,T: 剩余短句合成结束
    W-->>U: turn.done
    Note over U: 实际onended后才判定播放完成
    U->>W: playback.started(首音elapsedMs)
    U->>W: feedback(turnId,category)
    W->>D: 保存分类与耗时，不存反馈原文/录音
    W-->>U: feedback.saved
    opt 任意阶段打断/断线/新会话
        U->>W: turn.cancel或关闭连接
        W->>A: 取消当前HTTP
        W->>L: 取消实际OkHttp Call
        W->>T: 取消当前HTTP及待合成队列
        U->>U: 停止播放、清队列、使旧requestId失效
        Note over U,W: 迟到started须匹配当前requestId，其他事件须匹配turnId
    end
```

文字流和语音合成是两条订阅，慢TTS不会拖住文字显示。TTS错误保留文字，模型错误/截断不保存半截AI消息；完整模型文字即使随后被用户打断播放，仍可能已经保存。当前不按用户实际听到的字数截断历史，答辩时应说明这个取舍。

反馈不止“好/不好”：当前按延迟、识别、相关性、角色、声音五类绑定具体轮次；前端收到保存确认才提示成功。首音时间由浏览器实际开始播放回调采集，经登录WS发送服务器日志。时间起点是文字提交或识别完成，不包含录音与ASR耗时；服务端elapsedMs是编排阶段时钟，不能与客户端时间直接混用。反馈表只有分类、用户/角色/会话/轮次、可选耗时与创建时间；不额外保存录音。

后续评测应使用明确授权的真人中文、噪声和口音样本，分别测CER/WER、回答相关性、角色一致性、打断成功率、首音P50/P95与失败率。当前没有数据集、质量看板或线上灰度实验结果，不把一次581ms首音观测说成稳定性能。供应商选择为手动配置，不做隐式付费回退。
