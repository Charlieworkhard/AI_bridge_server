package org.example.service.tts;

import org.example.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 在线 TTS：调用微软 Edge 的 neural 语音（edge-tts），比系统自带 SAPI 自然得多。
 *
 * <p>合成与解码都放在 {@code tools/tts_helper.py} 里完成（edge-tts 产出 mp3，
 * miniaudio 直接解码并重采样成 16kHz 单声道 PCM16），Java 只负责起进程、读原始字节，
 * 因此不需要引入额外的音频解码库。
 */
@Service
@ConditionalOnProperty(prefix = "app.tts", name = "provider", havingValue = "edge")
public class EdgeTtsService implements TtsService {

    private static final Logger log = LoggerFactory.getLogger(EdgeTtsService.class);

    /** 尾部静音只留这么多点，避免每句之间停顿被拉长。 */
    private static final int TRAIL_MS = 80;
    /** 开头静音只留这么多点，听起来更跟手。 */
    private static final int LEAD_MS = 40;
    private static final int SILENCE_GATE = 220;

    private final AppProperties.Tts props;
    private final Path python;
    private final Path helper;

    public EdgeTtsService(AppProperties props) {
        this.props = props.getTts();
        this.python = resolve(this.props.getEdgePython());
        this.helper = resolve(this.props.getEdgeScript());
        if (!Files.isRegularFile(python)) {
            log.warn("TTS: 找不到 Python 解释器 {}，edge 合成会失败", python);
        }
        if (!Files.isRegularFile(helper)) {
            log.warn("TTS: 找不到合成脚本 {}，edge 合成会失败", helper);
        }
        log.info("TTS edge 已就绪：voice={} rate={} pitch={}", this.props.getEdgeVoice(),
                this.props.getEdgeRate(), this.props.getEdgePitch());
    }

    @Override
    public short[] synthesize(String text) {
        String clean = sanitize(text);
        if (clean.isEmpty()) {
            return new short[0];
        }
        Path txt = null;
        Path pcm = null;
        try {
            txt = Files.createTempFile("tts-", ".txt");
            pcm = Files.createTempFile("tts-", ".pcm");
            Files.writeString(txt, clean, StandardCharsets.UTF_8);
            Files.deleteIfExists(pcm);

            ProcessBuilder pb = new ProcessBuilder(
                    python.toString(), helper.toString(),
                    "--text-file", txt.toString(),
                    "--out", pcm.toString(),
                    "--voice", props.getEdgeVoice(),
                    "--rate", props.getEdgeRate(),
                    "--volume", props.getEdgeVolume(),
                    "--pitch", props.getEdgePitch());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("TTS edge 合成超时");
                return new short[0];
            }
            if (process.exitValue() != 0 || !Files.exists(pcm)) {
                log.warn("TTS edge 合成失败: {}", output.trim());
                return new short[0];
            }
            short[] audio = readPcm16(Files.readAllBytes(pcm));
            return trimSilence(audio);
        } catch (Exception e) {
            log.warn("TTS edge 合成异常: {}", e.getMessage());
            return new short[0];
        } finally {
            deleteQuietly(txt);
            deleteQuietly(pcm);
        }
    }

    private static short[] readPcm16(byte[] raw) {
        short[] out = new short[raw.length / 2];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < out.length; i++) {
            out[i] = buf.getShort();
        }
        return out;
    }

    /** 掐掉首尾多余的静音，让回复听起来更利落。 */
    private static short[] trimSilence(short[] in) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < in.length; i++) {
            if (Math.abs(in[i]) > SILENCE_GATE) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (first < 0) {
            return in;
        }
        int start = Math.max(0, first - 16 * LEAD_MS);
        int end = Math.min(in.length, last + 16 * TRAIL_MS);
        if (start == 0 && end == in.length) {
            return in;
        }
        short[] out = new short[end - start];
        System.arraycopy(in, start, out, 0, out.length);
        return out;
    }

    /** 去掉 markdown 与表情符号，避免被逐字念出来。 */
    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String s = text
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("[*#_~>|]", " ")
                .replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1")
                .replaceAll("https?://\\S+", " ")
                .replaceAll("[\\p{So}\\p{Sk}]", "")
                .replaceAll("\\s+", " ");
        return s.trim();
    }

    private static Path resolve(String value) {
        Path path = Paths.get(value);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get(System.getProperty("user.dir", ".")).resolve(path).normalize();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
