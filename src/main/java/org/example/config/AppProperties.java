package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** true：不调用 STT/LLM/TTS，直接回一段预置语音，用于先验证设备链路。 */
    private boolean mock = true;

    /** 可选：OTA 返回给设备的服务地址，例如 192.168.1.100:8003；留空则用请求的 Host。 */
    private String publicHost = "";

    /** 人设。 */
    private String persona = "你叫小智，是用户熟悉的朋友。说话温柔细腻、口语化、中文，每次回答不超过两句话。";

    private final Vad vad = new Vad();
    private final Stt stt = new Stt();
    private final Llm llm = new Llm();
    private final Tts tts = new Tts();

    public boolean isMock() { return mock; }
    public void setMock(boolean mock) { this.mock = mock; }

    public String getPublicHost() { return publicHost; }
    public void setPublicHost(String publicHost) { this.publicHost = publicHost; }

    public String getPersona() { return persona; }
    public void setPersona(String persona) { this.persona = persona; }

    public Vad getVad() { return vad; }
    public Stt getStt() { return stt; }
    public Llm getLlm() { return llm; }
    public Tts getTts() { return tts; }

    public static class Vad {
        private int silenceMs = 700;
        private int minSpeechMs = 300;
        private int maxUtteranceMs = 15000;
        private int energyThreshold = 500;

        public int getSilenceMs() { return silenceMs; }
        public void setSilenceMs(int silenceMs) { this.silenceMs = silenceMs; }
        public int getMinSpeechMs() { return minSpeechMs; }
        public void setMinSpeechMs(int minSpeechMs) { this.minSpeechMs = minSpeechMs; }
        public int getMaxUtteranceMs() { return maxUtteranceMs; }
        public void setMaxUtteranceMs(int maxUtteranceMs) { this.maxUtteranceMs = maxUtteranceMs; }
        public int getEnergyThreshold() { return energyThreshold; }
        public void setEnergyThreshold(int energyThreshold) { this.energyThreshold = energyThreshold; }
    }

    public static class Stt {
        /** vosk / mock */
        private String provider = "vosk";
        private String modelPath = "./models/vosk-model-small-cn-0.22";
        private String mockText = "你好呀，今天过得怎么样？";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModelPath() { return modelPath; }
        public void setModelPath(String modelPath) { this.modelPath = modelPath; }
        public String getMockText() { return mockText; }
        public void setMockText(String mockText) { this.mockText = mockText; }
    }

    public static class Llm {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-chat";
        private int timeoutSeconds = 30;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Tts {
        /** sapi（系统离线语音，机械） / edge（微软在线 neural，自然） */
        private String provider = "edge";
        private String voice = "Microsoft Huihui Desktop";
        private int rate = 0;
        private String presetAudio = "classpath:audio/reply.wav";

        /** edge 语音名，见 tools/tts-venv 里 `python -m edge_tts --list-voices`。 */
        private String edgeVoice = "zh-CN-XiaoxiaoNeural";
        /** 语速，+0% 为原速；想更活泼可调 +6%。 */
        private String edgeRate = "+0%";
        private String edgeVolume = "+0%";
        /** 音调，+10Hz 左右会更年轻一点。 */
        private String edgePitch = "+8Hz";
        /** 合成脚本解释器，相对路径基于后端启动目录。 */
        private String edgePython = "tools/tts-venv/Scripts/python.exe";
        private String edgeScript = "tools/tts_helper.py";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getVoice() { return voice; }
        public void setVoice(String voice) { this.voice = voice; }
        public int getRate() { return rate; }
        public void setRate(int rate) { this.rate = rate; }
        public String getPresetAudio() { return presetAudio; }
        public void setPresetAudio(String presetAudio) { this.presetAudio = presetAudio; }

        public String getEdgeVoice() { return edgeVoice; }
        public void setEdgeVoice(String edgeVoice) { this.edgeVoice = edgeVoice; }
        public String getEdgeRate() { return edgeRate; }
        public void setEdgeRate(String edgeRate) { this.edgeRate = edgeRate; }
        public String getEdgeVolume() { return edgeVolume; }
        public void setEdgeVolume(String edgeVolume) { this.edgeVolume = edgeVolume; }
        public String getEdgePitch() { return edgePitch; }
        public void setEdgePitch(String edgePitch) { this.edgePitch = edgePitch; }
        public String getEdgePython() { return edgePython; }
        public void setEdgePython(String edgePython) { this.edgePython = edgePython; }
        public String getEdgeScript() { return edgeScript; }
        public void setEdgeScript(String edgeScript) { this.edgeScript = edgeScript; }
    }
}
