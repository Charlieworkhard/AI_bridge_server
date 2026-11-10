package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.example.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备开机时请求的配置接口。返回 WebSocket 地址，设备会存到 NVS 并连接。
 */
@RestController
public class OtaController {

    private static final Logger log = LoggerFactory.getLogger(OtaController.class);

    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public OtaController(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/esp32Agent/local/")
    public Map<String, Object> config(@RequestBody(required = false) String body,
                                      HttpServletRequest request) {
        String deviceId = request.getHeader("Device-Id");
        log.info("设备请求配置: device={}, body={}", deviceId, body);

        String host = props.getPublicHost();
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName() + ":" + request.getServerPort();
        }
        String wsUrl = "ws://" + host + "/esp32Agent/ws/";

        Map<String, Object> websocket = new LinkedHashMap<>();
        websocket.put("url", wsUrl);
        websocket.put("token", "");
        websocket.put("version", 1);

        // 回显设备当前版本，避免被判定为有新固件
        String version = "2.5.2";
        try {
            if (body != null && !body.isBlank()) {
                JsonNode node = objectMapper.readTree(body);
                version = node.path("application").path("version").asText(version);
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> firmware = new LinkedHashMap<>();
        firmware.put("version", version);
        firmware.put("url", "");

        Map<String, Object> serverTime = new LinkedHashMap<>();
        serverTime.put("timestamp", System.currentTimeMillis());
        serverTime.put("timezone_offset", 480);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("firmware", firmware);
        result.put("websocket", websocket);
        result.put("server_time", serverTime);
        log.info("下发 WebSocket 地址: {}", wsUrl);
        return result;
    }

    @GetMapping("/esp32Agent/local/")
    public String info() {
        return "esp32 agent config endpoint is running";
    }
}
