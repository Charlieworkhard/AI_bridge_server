package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.ProtocolCodec;
import org.example.config.AppProperties;
import org.example.dao.DeviceSessionDao;
import org.example.dao.model.DeviceSession;
import org.example.service.AudioService;
import org.example.service.DeviceService;
import org.example.service.DialogueService;
import org.example.service.VadSegmenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Map;

@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final AppProperties props;
    private final DeviceSessionDao sessionDao;
    private final AudioService audioService;
    private final DialogueService dialogueService;
    private final DeviceService deviceService;

    public DeviceWebSocketHandler(ObjectMapper objectMapper,
                                  AppProperties props,
                                  DeviceSessionDao sessionDao,
                                  AudioService audioService,
                                  DialogueService dialogueService,
                                  DeviceService deviceService) {
        this.objectMapper = objectMapper;
        this.props = props;
        this.sessionDao = sessionDao;
        this.audioService = audioService;
        this.dialogueService = dialogueService;
        this.deviceService = deviceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) {
        String deviceId = header(ws, "Device-Id", "unknown");
        String clientId = header(ws, "Client-Id", "unknown");
        int version = parseInt(header(ws, "Protocol-Version", "1"), 1);

        DeviceSession session = new DeviceSession(ws.getId(), deviceId, clientId, ws);
        session.setProtocolVersion(version);
        session.setDecoder(audioService.newDecoder());
        session.setEncoder(audioService.newEncoder());
        session.setVad(new VadSegmenter(props.getVad()));
        sessionDao.save(session);
        log.info("设备已连接: device={}, client={}, protocol=v{}", deviceId, clientId, version);
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        DeviceSession session = sessionDao.get(ws.getId());
        if (session == null) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText("");
            switch (type) {
                case "hello" -> handleHello(session, node);
                case "listen" -> handleListen(session, node);
                case "abort" -> handleAbort(session);
                default -> log.debug("忽略消息类型: {}", type);
            }
        } catch (Exception e) {
            log.warn("处理文本消息失败: {}", e.getMessage());
        }
    }

    private void handleHello(DeviceSession session, JsonNode node) {
        int version = node.path("version").asInt(session.getProtocolVersion());
        session.setProtocolVersion(version);
        session.touch();
        deviceService.sendHello(session);
    }

    private void handleListen(DeviceSession session, JsonNode node) {
        String state = node.path("state").asText("");
        switch (state) {
            case "start" -> {
                session.setListening(true);
                session.setAborted(false);
                session.getVad().reset();
                log.info("开始聆听, mode={}", node.path("mode").asText(""));
            }
            case "stop" -> {
                session.setListening(false);
                short[] rest = session.getVad().flush();
                if (rest != null) {
                    dialogueService.onUtterance(session, rest);
                }
                log.info("停止聆听");
            }
            case "detect" -> log.info("唤醒词: {}", node.path("text").asText(""));
            default -> log.debug("未知 listen 状态: {}", state);
        }
    }

    private void handleAbort(DeviceSession session) {
        session.setAborted(true);
        session.setListening(false);
        session.getVad().reset();
        log.info("设备请求打断: {}", session.getDeviceId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession ws, BinaryMessage message) {
        DeviceSession session = sessionDao.get(ws.getId());
        if (session == null) {
            return;
        }
        try {
            ByteBuffer buffer = message.getPayload();
            byte[] frame = new byte[buffer.remaining()];
            buffer.get(frame);
            byte[] opus = ProtocolCodec.unwrap(frame, session.getProtocolVersion());
            short[] pcm = audioService.decode(session, opus);
            if (pcm.length == 0 || !session.isListening()) {
                return;
            }
            short[] utterance = session.getVad().accept(pcm);
            if (utterance != null) {
                session.touch();
                dialogueService.onUtterance(session, utterance);
            }
        } catch (Exception e) {
            log.warn("处理音频帧失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        DeviceSession session = sessionDao.get(ws.getId());
        if (session != null) {
            sessionDao.remove(ws.getId());
            session.close();
            log.info("设备已断开: device={}, status={}", session.getDeviceId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession ws, Throwable exception) {
        log.warn("WebSocket 传输异常: {}", exception.getMessage());
    }

    private String header(WebSocketSession ws, String name, String defaultValue) {
        String value = ws.getHandshakeHeaders().getFirst(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
