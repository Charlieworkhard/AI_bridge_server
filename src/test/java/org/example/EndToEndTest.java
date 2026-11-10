package org.example;

import org.example.common.WavUtils;
import org.example.service.AudioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.mock=true"})
class EndToEndTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void otaThenVoiceLoop() throws Exception {
        String ota = restTemplate.postForObject(
                "http://localhost:" + port + "/esp32Agent/local/", "{}", String.class);
        assertThat(ota).contains("websocket").contains("/esp32Agent/ws/");

        CountDownLatch hello = new CountDownLatch(1);
        CountDownLatch ttsStop = new CountDownLatch(1);
        AtomicInteger binaryFrames = new AtomicInteger();
        List<String> messages = Collections.synchronizedList(new ArrayList<>());

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                String text = data.toString();
                messages.add(text);
                if (text.contains("\"type\":\"hello\"")) {
                    hello.countDown();
                }
                if (text.contains("\"type\":\"tts\"") && text.contains("\"state\":\"stop\"")) {
                    ttsStop.countDown();
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                binaryFrames.incrementAndGet();
                webSocket.request(1);
                return null;
            }
        };

        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Device-Id", "test-device")
                .header("Client-Id", "test-client")
                .header("Protocol-Version", "1")
                .buildAsync(URI.create("ws://localhost:" + port + "/esp32Agent/ws/"), listener)
                .get(10, TimeUnit.SECONDS);

        ws.sendText("{\"type\":\"hello\",\"version\":1,\"transport\":\"websocket\",\"audio_params\":"
                + "{\"format\":\"opus\",\"sample_rate\":16000,\"channels\":1,\"frame_duration\":60}}", true)
                .get(5, TimeUnit.SECONDS);
        assertThat(hello.await(10, TimeUnit.SECONDS)).isTrue();

        ws.sendText("{\"type\":\"listen\",\"state\":\"start\",\"mode\":\"auto\"}", true)
                .get(5, TimeUnit.SECONDS);

        AudioService audio = new AudioService();
        short[] pcm;
        try (InputStream in = new ClassPathResource("audio/test-user.wav").getInputStream()) {
            WavUtils.Wav wav = WavUtils.read(in.readAllBytes());
            pcm = WavUtils.toMono16k(wav.pcm(), wav.sampleRate(), wav.channels());
        }
        for (byte[] frame : audio.encode(audio.newEncoder(), pcm)) {
            ws.sendBinary(ByteBuffer.wrap(frame), true).get(5, TimeUnit.SECONDS);
        }
        short[] silence = new short[960];
        for (byte[] frame : audio.encode(audio.newEncoder(), silence)) {
            ws.sendBinary(ByteBuffer.wrap(frame), true).get(5, TimeUnit.SECONDS);
        }
        for (int i = 0; i < 15; i++) {
            ws.sendBinary(ByteBuffer.wrap(audio.encode(audio.newEncoder(), silence).get(0)), true)
                    .get(5, TimeUnit.SECONDS);
        }

        assertThat(ttsStop.await(40, TimeUnit.SECONDS)).isTrue();
        assertThat(binaryFrames.get()).isGreaterThan(50);
        assertThat(messages).anyMatch(m -> m.contains("\"state\":\"start\""));
        ws.abort();
    }
}
