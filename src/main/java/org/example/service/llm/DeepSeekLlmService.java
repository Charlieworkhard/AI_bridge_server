package org.example.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmService.class);
    private static final Logger llmLog = LoggerFactory.getLogger("LLM_LOG");

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekLlmService(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String chat(List<Map<String, String>> history, String userText) {
        AppProperties.Llm cfg = props.getLlm();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            log.warn("未配置 app.llm.api-key（或环境变量 DEEPSEEK_API_KEY）");
            return "我还没配置好大脑，请先在配置文件里填入 DeepSeek 的 API Key。";
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", cfg.getModel());
            body.put("stream", false);
            body.put("temperature", 0.8);
            body.put("max_tokens", 300);

            ArrayNode messages = body.putArray("messages");
            addMessage(messages, "system", props.getPersona());
            for (Map<String, String> item : history) {
                addMessage(messages, item.get("role"), item.get("content"));
            }
            addMessage(messages, "user", userText);

            String base = cfg.getBaseUrl().replaceAll("/+$", "");
            String requestJson = objectMapper.writeValueAsString(body);
            llmLog.info("REQUEST {}", requestJson);
            long start = System.currentTimeMillis();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/chat/completions"))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long cost = System.currentTimeMillis() - start;
            if (response.statusCode() != 200) {
                llmLog.warn("RESPONSE status={} cost={}ms body={}", response.statusCode(), cost, response.body());
                log.error("DeepSeek 返回 {}: {}", response.statusCode(), response.body());
                return "抱歉，我这边刚刚走神了，再说一次好吗？";
            }
            llmLog.info("RESPONSE status=200 cost={}ms body={}", cost, response.body());
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            return content.isEmpty() ? "嗯，我在听呢。" : content;
        } catch (Exception e) {
            llmLog.error("ERROR {}", e.getMessage());
            log.error("调用 DeepSeek 失败: {}", e.getMessage());
            return "抱歉，我这边网络有点问题，稍后再试一次吧。";
        }
    }

    private void addMessage(ArrayNode messages, String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        ObjectNode node = messages.addObject();
        node.put("role", role);
        node.put("content", content);
    }
}
