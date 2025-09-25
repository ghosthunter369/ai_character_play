# AI Character Play - 实时语音识别服务

基于Spring Boot和讯飞语音识别API的实时语音识别服务，支持WebSocket和REST API两种接入方式。

## 🚀 功能特性

- **实时语音识别**: 支持实时音频流识别，返回流式文本结果
- **WebSocket支持**: 提供WebSocket端点接收前端音频数据
- **连接池管理**: 讯飞WebSocket连接池，支持连接复用
- **REST API**: 提供HTTP接口用于语音识别控制
- **响应式编程**: 使用Reactor实现流式数据处理

## 📋 技术栈

- Spring Boot 3.5.6
- Spring WebSocket
- Spring WebFlux (Reactor)
- Java-WebSocket客户端
- 讯飞语音识别API
- JSON处理

## 📁 项目结构

```
src/main/java/com/character/
├── controller/
│   └── ARSController.java             # REST API控制器
├── service/
│   ├── XunfeiConnectionPool.java      # 讯飞连接池服务
│   └── RealtimeASRService.java        # 实时语音识别服务
├── websocket/
│   └── AudioWebSocketHandler.java     # WebSocket处理器
└── config/
    └── AudioWebSocketConfig.java      # WebSocket配置
```

## ⚙️ 配置说明

在 `application.yml` 中配置讯飞API参数：

```yaml
xunfei:
  app-id: your_app_id
  access-key-id: your_access_key_id
  access-key-secret: your_access_key_secret
```

## 🔌 API接口

### REST API

| 方法 | 路径 | 描述 | 返回类型 |
|------|------|------|----------|
| POST | `/api/asr/start` | 启动实时语音识别会话 | SSE流 |
| POST | `/api/asr/send/{sessionId}` | 发送音频数据 | void |
| POST | `/api/asr/end/{sessionId}` | 结束语音识别会话 | void |
| GET | `/api/asr/health` | 健康检查 | String |

### WebSocket

- **端点**: `ws://localhost:8080/ws/audio`
- **支持消息类型**: 
  - 二进制消息（音频数据）
  - 文本消息（控制指令，如"END"）

## 🎯 使用方法

### 1. 启动应用

```bash
mvn spring-boot:run
```

### 2. 测试页面

访问 `http://localhost:8080/asr-test.html` 进行功能测试。

### 3. WebSocket客户端示例

```javascript
// 连接WebSocket
const websocket = new WebSocket('ws://localhost:8080/ws/audio');

// 发送音频数据（二进制）
websocket.send(audioData);

// 接收识别结果
websocket.onmessage = function(event) {
    console.log('识别结果:', event.data);
};

// 发送结束信号
websocket.send('END');
```

### 4. REST API示例

```javascript
// 启动识别会话（SSE）
const eventSource = new EventSource('/api/asr/start');
eventSource.onmessage = function(event) {
    console.log('识别结果:', event.data);
};

// 发送音频数据
fetch('/api/asr/send/sessionId', {
    method: 'POST',
    body: audioData,
    headers: {
        'Content-Type': 'application/octet-stream'
    }
});
```

## 🎵 音频格式要求

- **采样率**: 16000 Hz
- **编码格式**: PCM S16LE
- **声道数**: 单声道
- **帧大小**: 1280 字节
- **帧间隔**: 40ms

## 🔧 核心组件说明

### XunfeiConnectionPool
- 管理与讯飞API的WebSocket连接
- 支持连接池复用
- 自动处理鉴权和签名

### RealtimeASRService
- 提供实时语音识别服务
- 管理会话和连接的映射关系
- 处理音频数据的分帧和发送

### AudioWebSocketHandler
- 处理前端WebSocket连接
- 接收音频数据并转发给识别服务
- 返回识别结果给前端

### ARSController
- 提供REST API接口
- 支持SSE流式返回识别结果
- 健康检查和会话管理

## 📝 列表

以下功能需要进一步完善：

1. **音频帧处理优化**: 
   - ✅ 基础分帧逻辑已实现
   - 🔄 需要优化节奏控制算法
   - 🔄 支持音频格式转换

2. **连接管理增强**:
   - ✅ 基础连接池已实现
   - 🔄 需要添加连接健康检查
   - 🔄 实现自动重连机制

3. **错误处理**:
   - ✅ 基础异常处理已实现
   - 🔄 需要增强错误恢复机制
   - 🔄 添加详细的错误码定义

4. **性能优化**:
   - 🔄 连接池大小动态调整
   - 🔄 内存使用优化
   - 🔄 音频缓冲区管理

5. **监控和日志**:
   - ✅ 基础日志记录已实现
   - 🔄 添加性能指标监控
   - 🔄 集成APM工具

## 🐛 故障排除

### 常见问题

1. **WebSocket连接失败**
   - 检查防火墙设置
   - 确认端口8080未被占用
   - 验证讯飞API密钥配置

2. **音频识别无结果**
   - 检查麦克风权限
   - 确认音频格式符合要求
   - 查看控制台日志错误信息

3. **Maven依赖问题**
   - 清理本地仓库：`mvn clean`
   - 重新下载依赖：`mvn dependency:resolve`
   - 检查网络连接

### 日志级别调整

```yaml
logging:
  level:
    com.character: DEBUG  # 详细调试信息
    org.java_websocket: INFO  # WebSocket库日志
```

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交Issue和Pull Request来改进项目！

---

**注意**: 这是一个演示项目，生产环境使用时请注意：
- 更新讯飞API密钥
- 配置HTTPS和WSS
- 限制跨域访问
- 添加用户认证
- 实施速率限制