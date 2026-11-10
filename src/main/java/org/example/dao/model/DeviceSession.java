package org.example.dao.model;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import org.example.service.VadSegmenter;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一台设备的会话状态。全部放内存，不依赖数据库。
 */
public class DeviceSession {

    private final String id;
    private final String deviceId;
    private final String clientId;
    private final WebSocketSession webSocketSession;
    private final String dialogId = UUID.randomUUID().toString();
    private final long createdAt = System.currentTimeMillis();

    private volatile int protocolVersion = 1;
    private volatile boolean listening;
    private volatile boolean busy;
    private volatile boolean aborted;
    private volatile long lastActiveAt = System.currentTimeMillis();

    private OpusDecoder decoder;
    private OpusEncoder encoder;
    private VadSegmenter vad;

    private final Object sendLock = new Object();
    private final AtomicInteger audioSequence = new AtomicInteger();
    private final ExecutorService executor;
    private final List<Map<String, String>> history = Collections.synchronizedList(new ArrayList<>());

    public DeviceSession(String id, String deviceId, String clientId, WebSocketSession webSocketSession) {
        this.id = id;
        this.deviceId = deviceId;
        this.clientId = clientId;
        this.webSocketSession = webSocketSession;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "dialog-" + id);
            t.setDaemon(true);
            return t;
        });
    }

    public void addHistory(String role, String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        history.add(msg);
        while (history.size() > 20) {
            history.remove(0);
        }
    }

    public List<Map<String, String>> snapshotHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void touch() { lastActiveAt = System.currentTimeMillis(); }

    public String getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getClientId() { return clientId; }
    public WebSocketSession getWebSocketSession() { return webSocketSession; }
    public String getDialogId() { return dialogId; }
    public long getCreatedAt() { return createdAt; }
    public long getLastActiveAt() { return lastActiveAt; }
    public int getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(int protocolVersion) { this.protocolVersion = protocolVersion; }
    public boolean isListening() { return listening; }
    public void setListening(boolean listening) { this.listening = listening; }
    public boolean isBusy() { return busy; }
    public void setBusy(boolean busy) { this.busy = busy; }
    public boolean isAborted() { return aborted; }
    public void setAborted(boolean aborted) { this.aborted = aborted; }
    public OpusDecoder getDecoder() { return decoder; }
    public void setDecoder(OpusDecoder decoder) { this.decoder = decoder; }
    public OpusEncoder getEncoder() { return encoder; }
    public void setEncoder(OpusEncoder encoder) { this.encoder = encoder; }
    public VadSegmenter getVad() { return vad; }
    public void setVad(VadSegmenter vad) { this.vad = vad; }
    public Object getSendLock() { return sendLock; }
    public int nextSequence() { return audioSequence.incrementAndGet(); }
    public ExecutorService getExecutor() { return executor; }

    public void close() {
        executor.shutdownNow();
    }
}
