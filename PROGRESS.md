# 进度快照（暂停点）

## 2026-11-10 更新

- TTS 从 Windows SAPI（Huihui，机械）换成 **Edge 在线 neural 语音**，默认 `zh-CN-XiaoxiaoNeural`、`rate +6%`、`pitch +8Hz`；
  SAPI 实现保留，`app.tts.provider: sapi` 可离线回退。
- 新增 `tools/tts_helper.py`（edge-tts 合成 + miniaudio 解码重采样成 16k 单声道）与 `tools/requirements.txt`；
  运行环境 `tools/tts-venv` 已建好，已加入 `.gitignore`。
- STT 换成 Vosk 大模型 `models/vosk-model-cn-0.22`（原为 small）。
- 固件侧：去掉 esp-sr/AFE，开麦策略改为「按键 + 回复后 3 秒续聊窗口」（设计文档 §8.3）。

日期：2026-11-03
状态：**任务一已完成并通过自测，服务已停止，等待继续。**

## 已完成

- 工程从空 Gradle 项目改造为 Spring Boot 3.5.6 / Java 21。
- 配置下发：`POST /esp32Agent/local/`，返回 `firmware / websocket / server_time`，默认回显设备版本，不下发升级、不下发 activation、不下发 mqtt。
- WebSocket：`/esp32Agent/ws/`，支持 hello 握手、listen start/stop/detect、abort、v1/v2/v3 二进制帧。
- 音频：Concentus Opus 解码/编码（16k 单声道 60ms），能量 VAD 自动断句。
- 三种能力：
  - STT：Vosk 本地离线中文模型（模型已下载到 `models/vosk-model-small-cn-0.22`）。
  - LLM：DeepSeek，Key 走 `app.llm.api-key` 或环境变量 `DEEPSEEK_API_KEY`。
  - TTS：Windows SAPI（Microsoft Huihui Desktop），本地离线。
- `app.mock=true` 预置音频回环模式，便于先验证设备链路。
- 代码结构按 controller / service / dao 分层，代码中没有 xiaozhi 字样。

## 验证结果

| 项 | 结果 |
| --- | --- |
| `.\gradlew.bat test` | 6 个测试全部通过 |
| Opus 编解码往返 | 通过 |
| v1/v2/v3 帧封装/解包 | 通过 |
| VAD 断句 | 通过 |
| 模拟设备端到端（OTA→WS→hello→音频→tts start/stop） | 通过 |
| Vosk 中文识别（test-user.wav） | 通过（非空文本） |
| SAPI 中文合成 | 通过（16k 单声道） |
| `bootRun` + curl OTA | 通过，返回 `ws://<host>:8003/esp32Agent/ws/` |
| 真实链路（mock=false：Vosk→LLM 兜底→SAPI→Opus→WS） | 通过 |

设计说明文档：`C:\Users\Administrator\Desktop\ESP32语音后端-第一步设计说明.md`

## 当前配置

`src/main/resources/application.yml`

- `app.mock: true`（先验证设备，改为 false 走完整链路）
- `app.public-host: ""`（自动用请求 Host）
- `app.llm.api-key: ${DEEPSEEK_API_KEY:}`

## 下一步（继续时从这里开始）

1. 固件侧改 `sdkconfig`：
   `CONFIG_OTA_URL="http://<电脑局域网IP>:8003/esp32Agent/local/"`
   重新编译烧录。
2. Windows 防火墙放行 8003 入站。
3. 启动 `.\gradlew.bat bootRun`，确认设备开机请求 OTA、连上 WebSocket。
4. 保持 `app.mock=true` 先说一句话，验证设备能收到并播放预置音频。
5. 改 `app.mock=false`，填 DeepSeek Key，验证 STT→LLM→TTS 完整链路。
6. 按实际麦克风音量调 `app.vad.energy-threshold`（默认 500）与 `silence-ms`（默认 700）。

## 已知限制 / 待办

- 会话状态在内存，重启丢失。
- 同一设备同时只处理一轮对话；打断依赖设备发 abort。
- 未接真实硬件验证，仅模拟设备 + 单测。
- Vosk 小模型识别率一般，后续可换 sherpa-onnx 或云端 STT。
- 端口固定 8003，尚未做配置化。
