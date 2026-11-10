package org.example;

import org.example.common.WavUtils;
import org.example.service.AudioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不开 mock 的整链路：Vosk 识别 -> LLM（无 Key 时走兜底文本）-> SAPI 合成 -> Opus 推回。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.mock=false"})
class RealPipelineTest {

    @LocalServerPort
    int port;

    @Test
    void fullPipelineWithoutMock() throws Exception {
        CountDownLatch hello = new CountDownLatch(1);
        CountDownLatch stt = new CountDownLatch(1);
        CountDownLatch ttsStop = new CountDownLatch(1);
        AtomicInteger binary = new AtomicInteger();
        List<String> texts = Collections.synchronizedList(new ArrayList<>());

        WebSocket.Listener listener = new WebSocket.Listener() {
            public void onOpen(WebSocket ws) { ws.request(1); }

            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                String s = data.toString();
                texts.add(s);
                if (s.contains("\"type\":\"hello\"")) hello.countDown();
                if (s.contains("\"type\":\"stt\"")) stt.countDown();
                if (s.contains("\"type\":\"tts\"") && s.contains("\"state\":\"stop\"")) ttsStop.countDown();
                ws.request(1);
                return null;
            }

            public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                binary.incrementAndGet();
                ws.request(1);
                return null;
            }
        };

        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Device-Id", "real-test")
                .header("Client-Id", "real-test")
                .header("Protocol-Version", "1")
                .buildAsync(URI.create("ws://localhost:" + port + "/esp32Agent/ws/"), listener)
                .get(10, TimeUnit.SECONDS);

        ws.sendText("{\"type\":\"hello\",\"version\":1,\"transport\":\"websocket\",\"audio_params\":"
                + "{\"format\":\"opus\",\"sample_rate\":16000,\"channels\":1,\"frame_duration\":60}}", true);
        ws.sendText("{\"type\":\"listen\",\"state\":\"start\",\"mode\":\"auto\"}", true);
        assertThat(hello.await(10, TimeUnit.SECONDS)).isTrue();

        AudioService audio = new AudioService();
        short[] pcm;
        try (InputStream in = new ClassPathResource("audio/test-user.wav").getInputStream()) {
            WavUtils.Wav wav = WavUtils.read(in.readAllBytes());
            pcm = WavUtils.toMono16k(wav.pcm(), wav.sampleRate(), wav.channels());
        }
        for (byte[] frame : audio.encode(audio.newEncoder(), pcm)) {
            ws.sendBinary(ByteBuffer.wrap(frame), true);
        }
        short[] silence = new short[960];
        List<byte[]> silentFrames = audio.encode(audio.newEncoder(), silence);
        for (int i = 0; i < 15; i++) {
            ws.sendBinary(ByteBuffer.wrap(silentFrames.get(0)), true);
        }

        assertThat(stt.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(ttsStop.await(60, TimeUnit.SECONDS)).isTrue();
        assertThat(binary.get()).isGreaterThan(20);
        ws.abort();
    }
}
