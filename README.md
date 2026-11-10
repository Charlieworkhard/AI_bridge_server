# AI Bridge Server

ESP32-S3 小智固件的自建后端：接收设备上传的 Opus 音频，VAD 切句，经 STT → LLM → TTS 后把语音推回设备播放。

## 1. 技术栈

- Spring Boot 3.5 / Java 21 / Gradle
- Opus 编解码：Concentus（纯 Java，无需本地库）
- VAD：短时能量（自适应噪声底）
- STT：Vosk 本地离线中文小模型
- LLM：DeepSeek（OpenAI 兼容接口）
- TTS：微软 Edge 在线 neural 语音（默认 `zh-CN-XiaoxiaoNeural`，自然）；

## 2. 启动

```powershell
cd D:\code\esp32_bridge_server\AI_bridge_server
.\gradlew.bat bootRun
```

服务监听 `0.0.0.0:8003`。

首次启动前确认：

1. STT 模型在 `models/vosk-model-cn-0.22`（大模型，约 1.3 GB 解压后）。
   下载地址：`https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip`
2. TTS 的 Python 环境（仅 `provider: edge` 时需要）：
   ```powershell
   python -m venv tools\tts-venv
   tools\tts-venv\Scripts\python.exe -m pip install -r tools\requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
   ```
   验证：`tools\tts-venv\Scripts\python.exe -m edge_tts --list-voices | findstr zh-CN`
3. 需要真实对话时，在 `src/main/resources/application.yml` 填 `app.llm.api-key`，
   或设置环境变量 `DEEPSEEK_API_KEY`。

## 3. 配置

全部在 `src/main/resources/application.yml`：

| 配置 | 说明 |
| --- | --- |
| `app.mock` | `true`：收到语音只回预置音频，用于先验证设备链路；`false`：走完整 STT/LLM/TTS |
| `app.public-host` | OTA 返回给设备的地址，如 `192.168.1.100:8003`；留空则用请求 Host |
| `app.persona` | 人设 |
| `app.vad.*` | 静音断句时长、最短语音、最长一句话、能量阈值 |
| `app.stt.provider` | `vosk` 或 `mock` |
| `app.llm.api-key` | DeepSeek Key |
| `app.tts.provider` | `edge`（在线 neural，默认）/ `sapi`（Windows 离线，机械，断网时回退用） |
| `app.tts.edge-voice` | edge 音色，默认 `zh-CN-XiaoxiaoNeural`；男声可换 `zh-CN-YunxiNeural` |
| `app.tts.edge-rate` / `edge-pitch` | 语速 / 音调，默认 `+0%` / `+8Hz` |
| `app.tts.edge-python` / `edge-script` | 合成用的解释器与脚本，相对启动目录 |
| `app.tts.voice` | 仅 SAPI 生效，默认 `Microsoft Huihui Desktop` |

## 4. 固件侧配置

把固件的 OTA 地址改成：

```
http://<电脑局域网IP>:8003/esp32Agent/local/
```

即修改 `sdkconfig`：

```ini
CONFIG_OTA_URL="http://192.168.1.100:8003/esp32Agent/local/"
```

重新编译烧录后，设备开机会请求该接口，后端返回：

```json
{
  "firmware": { "version": "2.5.2", "url": "" },
  "websocket": { "url": "ws://192.168.1.100:8003/esp32Agent/ws/", "token": "", "version": 1 },
  "server_time": { "timestamp": 1760000000000, "timezone_offset": 480 }
}
```

设备随后连接 `ws://<IP>:8003/esp32Agent/ws/`。

## 5. 接口

### 配置下发

`POST /esp32Agent/local/`

### WebSocket

`/esp32Agent/ws/`

支持协议版本 1/2/3 的二进制帧；`hello`、`listen`、`abort`、`tts`、`stt` 消息。

## 6. 自测

```powershell
# 配置接口
curl.exe -X POST http://127.0.0.1:8003/esp32Agent/local/ -H "Device-Id: test" -d "{}"

# 单元测试 + 模拟设备端到端测试
.\gradlew.bat test
```

`EndToEndTest` 会模拟一台设备：OTA → WebSocket → hello → 上传 `test-user.wav` 的 Opus 帧 → 等待 tts start/音频/stop。

## 7. 目录结构

```
src/main/java/org/example
├── controller/   OtaController、DeviceWebSocketHandler
├── service/      DeviceService、DialogueService、AudioService、VadSegmenter
│   ├── stt/      Vosk / Mock
│   ├── llm/      DeepSeek
│   └── tts/      Edge（在线 neural） / SAPI
├── dao/          DeviceSessionDao + DeviceSession
├── common/       ProtocolCodec、WavUtils
└── config/       AppProperties、WebSocketConfig

tools/
├── tts_helper.py     文本 → 16kHz PCM（edge-tts + miniaudio 解码重采样）
└── requirements.txt  该脚本的 Python 依赖
```

## 8. 已知限制

- 会话状态在内存，重启后丢失。
- 单设备同一时间只处理一轮对话，打断依赖设备发 `abort`。
- `provider: edge` 需要联网到 `speech.platform.bing.com`，每句起一个 Python 进程约 1.6s；断网时改成 `provider: sapi` 可离线。
- Vosk 大模型对口音和长句仍会有错字，后续可换 sherpa-onnx 或云端 STT。
