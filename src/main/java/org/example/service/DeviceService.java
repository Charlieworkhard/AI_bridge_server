package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.ProtocolCodec;
import org.example.dao.model.DeviceSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final ObjectMapper objectMapper;

    public DeviceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sendHello(DeviceSession session) {
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", "opus");
        audio.put("sample_rate", AudioService.SAMPLE_RATE);
        audio.put("channels", 1);
        audio.put("frame_duration", AudioService.FRAME_MS);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "hello");
        msg.put("transport", "websocket");
        msg.put("session_id", session.getDialogId());
        msg.put("audio_params", audio);
        sendJson(session, msg);
        log.info("已回复 hello, device={}", session.getDeviceId());
    }

    public void sendStt(DeviceSession session, String text) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "stt");
        msg.put("text", text);
        msg.put("session_id", session.getDialogId());
        sendJson(session, msg);
    }

    public void sendTtsStart(DeviceSession session) {
        sendTtsState(session, "start", null);
    }

    public void sendTtsStop(DeviceSession session) {
        sendTtsState(session, "stop", null);
    }

    public void sendSentenceStart(DeviceSession session, String text) {
        sendTtsState(session, "sentence_start", text);
    }

    private void sendTtsState(DeviceSession session, String state, String text) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "tts");
        msg.put("state", state);
        msg.put("session_id", session.getDialogId());
        if (text != null) {
            msg.put("text", text);
        }
        sendJson(session, msg);
    }

    public void sendJson(DeviceSession session, Map<String, Object> payload) {
        WebSocketSession ws = session.getWebSocketSession();
        if (ws == null || !ws.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session.getSendLock()) {
                ws.sendMessage(new TextMessage(json));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("发送 JSON 失败: {}", e.getMessage());
        }
    }

    public void sendAudio(DeviceSession session, byte[] opus) {
        WebSocketSession ws = session.getWebSocketSession();
        if (ws == null || !ws.isOpen() || opus == null || opus.length == 0) {
            return;
        }
        byte[] payload = ProtocolCodec.wrap(opus, session.getProtocolVersion(), session.nextSequence() * AudioService.FRAME_MS);
        try {
            synchronized (session.getSendLock()) {
                ws.sendMessage(new BinaryMessage(payload));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("发送音频失败: {}", e.getMessage());
        }
    }
}
