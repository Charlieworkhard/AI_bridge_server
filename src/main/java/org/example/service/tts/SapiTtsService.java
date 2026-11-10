package org.example.service.tts;

import org.example.common.WavUtils;
import org.example.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * 本地 TTS：调用 Windows 自带 SAPI（默认中文 Huihui），不联网、不花钱。
 */
@Service
@ConditionalOnProperty(prefix = "app.tts", name = "provider", havingValue = "sapi", matchIfMissing = true)
public class SapiTtsService implements TtsService {

    private static final Logger log = LoggerFactory.getLogger(SapiTtsService.class);

    private final AppProperties props;
    private final Path script;

    public SapiTtsService(AppProperties props) {
        this.props = props;
        this.script = extractScript();
    }

    @Override
    public short[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            return new short[0];
        }
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            log.warn("SAPI 仅支持 Windows");
            return new short[0];
        }
        Path txt = null;
        Path wav = null;
        try {
            txt = Files.createTempFile("tts-", ".txt");
            wav = Files.createTempFile("tts-", ".wav");
            Files.writeString(txt, text, StandardCharsets.UTF_8);
            Files.deleteIfExists(wav);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Sta",
                    "-ExecutionPolicy", "Bypass",
                    "-File", script.toString(),
                    txt.toString(), wav.toString(),
                    props.getTts().getVoice(), String.valueOf(props.getTts().getRate()));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean done = process.waitFor(30, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                log.warn("SAPI 合成超时");
                return new short[0];
            }
            if (!Files.exists(wav) || Files.size(wav) == 0) {
                log.warn("SAPI 没有生成音频: {}", output.trim());
                return new short[0];
            }
            WavUtils.Wav data = WavUtils.read(Files.readAllBytes(wav));
            return WavUtils.toMono16k(data.pcm(), data.sampleRate(), data.channels());
        } catch (Exception e) {
            log.warn("SAPI 合成失败: {}", e.getMessage());
            return new short[0];
        } finally {
            deleteQuietly(txt);
            deleteQuietly(wav);
        }
    }

    private Path extractScript() {
        try (InputStream in = new ClassPathResource("tts/sapi_tts.ps1").getInputStream()) {
            Path target = Files.createTempFile("sapi-tts-", ".ps1");
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("释放 SAPI 脚本失败", e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
