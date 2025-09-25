# Requirements Document

## Introduction

本项目需要开发一个基于Vue的实时语音对话前端应用，与现有的Spring Boot语音识别后端服务集成。系统实现从用户语音输入到AI语音回复的完整闭环，包括实时语音识别、流式文本生成、语音合成和音频播放功能。前端通过WebSocket和SSE与后端通信，支持音频上行、文本下行和音频下行的多路数据流。

## Requirements

### Requirement 1

**User Story:** 作为用户，我希望能够通过点击麦克风按钮开始语音输入，系统能够实时识别我的语音并显示识别结果，以便我能够看到系统是否正确理解了我的话。

#### Acceptance Criteria

1. WHEN 用户点击麦克风按钮 THEN 系统 SHALL 请求麦克风权限并开始音频采集
2. WHEN 音频采集开始 THEN 系统 SHALL 建立WebSocket连接到后端音频端点
3. WHEN 麦克风采集到音频数据 THEN 系统 SHALL 将PCM音频数据以20-40ms分片通过WebSocket发送到后端
4. WHEN 后端返回ASR识别结果 THEN 系统 SHALL 区分partial和final结果并在UI中实时显示
5. WHEN 显示partial结果 THEN 系统 SHALL 使用灰色或下划线样式表示临时识别文本
6. WHEN 显示final结果 THEN 系统 SHALL 将临时文本转为正式文本并更新样式

### Requirement 2

**User Story:** 作为用户，我希望看到AI对我的语音输入进行实时回复，文字能够流式显示，以便我能够立即看到AI的思考过程和回复内容。

#### Acceptance Criteria

1. WHEN 语音识别完成final结果 THEN 系统 SHALL 通过SSE接收ChatModel的流式token响应
2. WHEN 接收到token事件 THEN 系统 SHALL 将token增量拼接到回复文本中并实时渲染
3. WHEN 接收到done事件 THEN 系统 SHALL 标记回复完成并固定文本样式
4. WHEN 显示流式文本 THEN 系统 SHALL 区分中间态（灰色）和最终态（黑色/加粗）
5. WHEN 文本生成过程中 THEN 系统 SHALL 显示"Thinking"状态指示器

### Requirement 3

**User Story:** 作为用户，我希望能够听到AI的语音回复，音频播放要流畅连续，并且能够看到播放进度和状态，以便获得完整的对话体验。

#### Acceptance Criteria

1. WHEN ChatModel生成文本token THEN 系统 SHALL 通过WebSocket接收对应的TTS音频分片
2. WHEN 接收到音频分片 THEN 系统 SHALL 将PCM数据转换为AudioBuffer并加入播放队列
3. WHEN 播放音频 THEN 系统 SHALL 按seq顺序播放音频分片确保连续性
4. WHEN 音频播放中 THEN 系统 SHALL 显示"Speaking"状态和播放进度条
5. WHEN 音频播放到特定token THEN 系统 SHALL 高亮对应的文本实现同步显示
6. WHEN 音频播放完成 THEN 系统 SHALL 更新状态为待机并清空播放队列

### Requirement 4

**User Story:** 作为用户，我希望能够随时打断AI的回复或重新开始对话，系统能够立即响应我的操作，以便控制对话流程。

#### Acceptance Criteria

1. WHEN 用户点击停止按钮 THEN 系统 SHALL 发送停止信号到后端中断ASR/Chat/TTS
2. WHEN 发送停止信号 THEN 系统 SHALL 立即停止当前音频播放并清空播放队列
3. WHEN 停止操作完成 THEN 系统 SHALL 关闭所有WebSocket连接并重置UI状态
4. WHEN 用户点击重新开始 THEN 系统 SHALL 清理当前会话并重新初始化连接
5. WHEN 网络连接中断 THEN 系统 SHALL 显示错误提示并提供重试按钮

### Requirement 5

**User Story:** 作为用户，我希望能够看到系统的连接状态和错误信息，以便了解系统运行情况并在出现问题时进行相应操作。

#### Acceptance Criteria

1. WHEN 系统运行时 THEN 系统 SHALL 显示WebSocket连接状态（连接/重连中/断开）
2. WHEN 后端服务状态变化 THEN 系统 SHALL 更新ASR/TTS服务状态指示器
3. WHEN 发生网络错误 THEN 系统 SHALL 显示具体错误信息和重试选项
4. WHEN TTS服务超时 THEN 系统 SHALL 回退到纯文本显示模式
5. WHEN 麦克风权限被拒绝 THEN 系统 SHALL 显示权限请求提示和设置指导

### Requirement 6

**User Story:** 作为用户，我希望系统能够处理高质量的音频数据传输，确保语音识别和合成的准确性，以便获得最佳的对话质量。

#### Acceptance Criteria

1. WHEN 采集音频 THEN 系统 SHALL 使用PCM 16-bit, 16kHz, mono格式
2. WHEN 发送音频数据 THEN 系统 SHALL 每帧包含320或640样本（640或1280字节）
3. WHEN 处理音频流 THEN 系统 SHALL 为每个音频分片添加序号和时间戳
4. WHEN 网络拥塞时 THEN 系统 SHALL 实现backpressure机制丢帧或降采样
5. WHEN 音频数据丢失 THEN 系统 SHALL 能够跳过丢失分片继续播放
6. WHEN 音频格式转换 THEN 系统 SHALL 正确处理Int16到Float32的转换

### Requirement 7

**User Story:** 作为开发者，我希望系统具有良好的会话管理和资源控制，确保系统稳定运行和用户体验，以便支持多用户并发使用。

#### Acceptance Criteria

1. WHEN 建立新会话 THEN 系统 SHALL 生成唯一sessionId并维护会话状态
2. WHEN 会话超时 THEN 系统 SHALL 自动清理资源并关闭相关连接
3. WHEN 用户离开页面 THEN 系统 SHALL 正确清理所有WebSocket连接和音频资源
4. WHEN 系统负载过高 THEN 系统 SHALL 实现限流机制保护后端服务
5. WHEN 监控系统性能 THEN 系统 SHALL 记录连接数、延迟和错误率指标