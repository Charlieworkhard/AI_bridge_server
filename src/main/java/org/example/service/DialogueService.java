package org.example.service;

import jakarta.annotation.PostConstruct;
import org.example.common.WavUtils;
import org.example.config.AppProperties;
import org.example.dao.model.DeviceSession;
import org.example.service.llm.LlmService;
import org.example.service.stt.SttService;
import org.example.service.tts.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class DialogueService {

    private static final Logger log = LoggerFactory.getLogger(DialogueService.class);

    private final AppProperties props;
    private final SttService sttService;
    private final LlmService llmService;
    private final TtsService ttsService;
    private final AudioService audioService;
    private final DeviceService deviceService;
    private final ResourceLoader resourceLoader;

    private short[] presetPcm = new short[0];

    public DialogueService(AppProperties props,
                           SttService sttService,
                           LlmService llmService,
                           TtsService ttsService,
                           AudioService audioService,
                           DeviceService deviceService,
                           ResourceLoader resourceLoader) {
        this.props = props;
        this.sttService = sttService;
        this.llmService = llmService;
        this.ttsService = ttsService;
        this.audioService = audioService;
        this.deviceService = deviceService;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadPreset() {
        String location = props.getTts().getPresetAudio();
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.warn("预置音频不存在: {}", location);
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                WavUtils.Wav wav = WavUtils.read(in.readAllBytes());
                presetPcm = WavUtils.toMono16k(wav.pcm(), wav.sampleRate(), wav.channels());
                log.info("预置音频已加载，时长 {}ms", presetPcm.length * 1000 / AudioService.SAMPLE_RATE);
            }
        } catch (Exception e) {
            log.warn("预置音频加载失败: {}", e.getMessage());
        }
    }

    /** VAD 切出一句话后调用，异步执行，避免阻塞 WebSocket 接收线程。 */
    public void onUtterance(DeviceSession session, short[] pcm) {
        if (session.isBusy()) {
            log.info("上一轮还没结束，忽略本次语音");
            return;
        }
        session.setBusy(true);
        session.setAborted(false);
        session.getExecutor().submit(() -> {
            try {
                process(session, pcm);
            } catch (Exception e) {
                log.error("对话处理失败", e);
            } finally {
                session.setBusy(false);
            }
        });
    }

    private void process(DeviceSession session, short[] pcm) {
        if (props.isMock()) {
            log.info("mock 模式：回复预置音频");
            deviceService.sendTtsStart(session);
            sendPcm(session, presetPcm);
            finishTts(session);
            return;
        }

        String text = sttService.transcribe(pcm);
        if (text == null || text.isBlank()) {
            log.info("没有识别到文本，忽略");
            return;
        }
        log.info("STT: {}", text);
        deviceService.sendStt(session, text);
        if (session.isAborted()) {
            return;
        }

        String reply = llmService.chat(session.snapshotHistory(), text);
        log.info("LLM: {}", reply);
        session.addHistory("user", text);
        session.addHistory("assistant", reply);
        if (session.isAborted()) {
            return;
        }

        List<String> sentences = splitSentences(reply);
        boolean started = false;
        for (String sentence : sentences) {
            if (session.isAborted()) {
                break;
            }
            short[] audio = ttsService.synthesize(sentence);
            if (audio.length == 0) {
                continue;
            }
            if (!started) {
                deviceService.sendTtsStart(session);
                started = true;
            }
            deviceService.sendSentenceStart(session, sentence);
            sendPcm(session, audio);
        }
        if (started) {
            finishTts(session);
        }
    }

    /** 设备端还有几帧缓冲，稍等一下再发 stop，避免尾音被截断。 */
    private void finishTts(DeviceSession session) {
        if (session.isAborted()) {
            return;
        }
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        deviceService.sendTtsStop(session);
    }

    private void sendPcm(DeviceSession session, short[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        List<byte[]> frames = audioService.encode(session.getEncoder(), pcm);
        int sent = 0;
        for (byte[] frame : frames) {
            if (session.isAborted()) {
                break;
            }
            deviceService.sendAudio(session, frame);
            sent++;
            // 前两帧先发，之后按 60ms 节奏，避免设备缓冲溢出
            if (sent > 2) {
                try {
                    Thread.sleep(AudioService.FRAME_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private List<String> splitSentences(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        String[] parts = text.split("(?<=[。！？!?；;\\n])");
        for (String part : parts) {
            String s = part.trim();
            if (!s.isEmpty()) {
                result.add(s);
            }
            if (result.size() >= 6) {
                break;
            }
        }
        if (result.isEmpty()) {
            result.add(text.trim());
        }
        return result;
    }
}
