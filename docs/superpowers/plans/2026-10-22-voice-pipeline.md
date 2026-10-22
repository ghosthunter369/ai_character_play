# Voice Pipeline Implementation Plan

> **For agentic workers:** Use subagent-driven-development for independent modules and a fresh whole-branch review. Never merge master.

**Goal:** Docker可运行、多供应商可配置、可取消且会话隔离的语音对话。
**Architecture:** Spring Boot编排 + 可取消语音网关 + Vue播放状态机；独立MySQL/Redis/llama.cpp/Whisper/eSpeak。
**Tech Stack:** Java21, Spring Boot, Reactor, OkHttp, Vue3, TypeScript, Docker Compose.
**Spec:** docs/superpowers/specs/2026-10-22-voice-pipeline-design.md

## Global Constraints
- feature/voice-pipeline-docker基于develop，仅合并develop。
- 不导入历史用户/对话，不修改现有容器与卷。
- 真实方法/HTTP/WS验证；协议替身与真实云调用分别报告。
- 不展示或提交密钥，不保证未经验证的免费额度。

## Review Focus
- 跨用户/会话记忆隔离。
- 取消/断线/新轮次不播放迟到音频。
- 录音长度、未知供应商、无密钥与超时明确处理。
- 无定时器猜回复完成；实际播放状态独立。
- 打包资源、容器健康与已有数据库升级可重复。

## Tasks
- [x] 1. Docker基线：compose、构建、干净SQL、配置；真实HTTP验证。
- [x] 2. 供应商：官方试用调研；HTTP契约测试、可取消SpeechGateway与配置状态；真实本地Whisper/eSpeak。
- [x] 3. 对话隔离：SQL真实方法测试；conversationId隔离、输入不重复、取消不存半截AI回复、上下文预算。
- [x] 4. 编排：分句/取消/失败回归；/ws/voice、请求/轮次、时延及反馈。
- [x] 5. 前端：迟到事件/取消/完成测试；录音提交、实际播放回调、供应商控制。
- [x] 6. 集成：Docker、真实WS长会话与浏览器入口；限制见docs/validation.md。
- [ ] 7. 审查交付：独立审查、修复重要问题、提交推送、develop PR、验证头提交后合并；master不变。
