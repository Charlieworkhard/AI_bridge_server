package org.example.service.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地离线识别：Vosk 小模型。模型不存在时退回固定文本，保证服务能启动。
 */
@Service
@ConditionalOnProperty(prefix = "app.stt", name = "provider", havingValue = "vosk", matchIfMissing = true)
public class VoskSttService implements SttService {

    private static final Logger log = LoggerFactory.getLogger(VoskSttService.class);

    private final AppProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Model model;

    public VoskSttService(AppProperties props) {
        this.props = props;
        Path path = Paths.get(props.getStt().getModelPath()).toAbsolutePath();
        Model loaded = null;
        if (Files.isDirectory(path)) {
            try {
                loaded = new Model(path.toString());
                log.info("Vosk 模型加载成功: {}", path);
            } catch (IOException e) {
                log.error("Vosk 模型加载失败: {}", e.getMessage());
            }
        } else {
            log.warn("Vosk 模型目录不存在: {}，将返回固定文本，可在 application.yml 里改 app.stt.provider=mock", path);
        }
        this.model = loaded;
    }

    @Override
    public String transcribe(short[] pcm16k) {
        if (model == null || pcm16k == null || pcm16k.length == 0) {
            return props.getStt().getMockText();
        }
        try (Recognizer recognizer = new Recognizer(model, 16000f)) {
            recognizer.acceptWaveForm(pcm16k, pcm16k.length);
            String json = recognizer.getFinalResult();
            JsonNode node = objectMapper.readTree(json);
            return node.path("text").asText("").replace(" ", "").trim();
        } catch (Exception e) {
            log.warn("Vosk 识别失败: {}", e.getMessage());
            return "";
        }
    }
}
