# AI Bridge Server

ESP32-S3 语音固件的自建后端：设备上传 Opus 音频 → 能量 VAD 切句 → Vosk 本地识别 → DeepSeek 生成回复 → Edge neural TTS 合成 → Opus 回推设备播放。不依赖任何云厂商的语音服务（LLM 除外）。

## 1. 环境要求

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 21+ | 运行 Spring Boot |
| Python | 3.9+ | 仅 TTS 用（edge-tts 合成 + miniaudio 解码） |
| 网络 | 可访问 `api.deepseek.com`、`speech.platform.bing.com` | LLM 与在线 TTS |
| 磁盘 | 约 2.5 GB 空闲 | Vosk 大模型解压后约 2 GB |

## 2. 克隆与首次运行

仓库里**不含**模型、Python 虚拟环境、日志和编译产物（都在 `.gitignore` 里），克隆后按下面四步补齐。

### 2.1 克隆

```powershell
git clone https://github.com/Charlieworkhard/AI_bridge_server.git
cd AI_bridge_server
```

### 2.2 下载 STT 模型（约 2 GB）

```powershell
New-Item -ItemType Directory -Force models | Out-Null
curl.exe -L -o models\vosk-model-cn-0.22.zip https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip
Expand-Archive models\vosk-model-cn-0.22.zip -DestinationPath models
Remove-Item models\vosk-model-cn-0.22.zip
```

解压后目录必须正好是 `models\vosk-model-cn-0.22`（里面有 `am\final.mdl` 和 `graph\HCLG.fst`）。如果解出来叫 `model-cn`，改名即可。

> 官方源在国内很慢，可换镜像：
> `https://hf-mirror.com/LiangJingyi/vosk-model-cn-0.22/resolve/main/model-cn.zip`

### 2.3 建 TTS 的 Python 环境

```powershell
python -m venv tools\tts-venv
tools\tts-venv\Scripts\python.exe -m pip install -r tools\requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

验证（能列出中文音色就说明可用）：

```powershell
tools\tts-venv\Scripts\python.exe -m edge_tts --list-voices | findstr zh-CN
```

> 只想要系统离线语音的话可以跳过这步，把 `application.yml` 的 `app.tts.provider` 改成 `sapi`。

### 2.4 配置 DeepSeek Key

Key **不要**写进 `application.yml`（那是仓库文件）。两种方式任选：

```powershell
# 方式一：环境变量（当前窗口有效）
$env:DEEPSEEK_API_KEY = "sk-你的key"

# 永久生效
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-你的key", "User")
```

```yaml
# 方式二：新建 src/main/resources/application-local.yml（已在 .gitignore 里）
app:
  llm:
    api-key: sk-你的key
```

### 2.5 启动

```powershell
.\gradlew.bat bootRun
```

服务监听 `0.0.0.0:8003`。正常启动应看到这两行：

```
TTS edge 已就绪：voice=zh-CN-XiaoxiaoNeural rate=+0% pitch=+8Hz
Tomcat started on port 8003 (http)
```

### 2.6 30 秒验证链路

```powershell
# 配置下发接口
curl.exe -X POST http://127.0.0.1:8003/esp32Agent/local/ -H "Device-Id: test" -d "{}"
```

返回里应包含 `ws://<你的IP>:8003/esp32Agent/ws/`。把固件的 OTA 地址指向这个接口（见第 4 节），设备开机即自动连上。

## 3. 配置

全部在 `src/main/resources/application.yml`：

| 配置 | 说明 |
| --- | --- |
| `app.mock` | `true`：收到语音只回预置音频，用于先验证设备链路；`false`：走完整 STT/LLM/TTS |
| `app.public-host` | OTA 返回给设备的地址，如 `192.168.1.100:8003`；留空则用请求 Host |
| `app.persona` | 人设 |
| `app.vad.*` | 静音断句时长、最短语音、最长一句话、能量阈值 |
| `app.stt.provider` | `vosk` 或 `mock` |
| `app.stt.model-path` | Vosk 模型目录，默认 `./models/vosk-model-cn-0.22` |
| `app.llm.api-key` | DeepSeek Key，建议留空走环境变量 |
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

即修改固件的 `CONFIG_APP_CONFIG_URL`（`idf.py menuconfig` → Application Configuration，或直接写进 `sdkconfig.defaults`）：

```ini
CONFIG_APP_CONFIG_URL="http://192.168.1.100:8003/esp32Agent/local/"
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
- 仓库不含 `models/` 与 `tools/tts-venv/`，换机器需按第 2 节重新准备。

## 9. 常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| 启动报 `model.go ... folder does not exist` | 模型路径不对，确认 `models\vosk-model-cn-0.22\am\final.mdl` 存在 |
| 日志刷 `没有识别到文本，忽略` | 设备没断句一直上传（固件侧 VAD 问题），属于设备端；后端只是把每段都识别了一遍 |
| 回复没声音 | 看日志有没有 `TTS edge 已就绪`；没有则是 `app.tts.provider` 或 Python 路径不对 |
| `TTS edge 合成失败` | `tools\tts-venv` 没建好，或网络到 bing 不通；可临时改 `provider: sapi` |
| LLM 返回 401 | 没设 `DEEPSEEK_API_KEY` 环境变量 |
| `Port 8003 was already in use` | 上一个实例没退，`taskkill /F /IM java.exe` |
| 设备连不上 | Windows 防火墙放行 8003 入站，且 `app.public-host` 填电脑局域网 IP 而不是 127.0.0.1 |
