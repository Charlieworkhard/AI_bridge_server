package org.example;

import org.example.config.AppProperties;
import org.example.common.WavUtils;
import org.example.service.stt.VoskSttService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VoskSttTest {

    @Test
    void transcribeChineseSpeech() throws Exception {
        String modelPath = "./models/vosk-model-small-cn-0.22";
        assumeTrue(Files.isDirectory(Paths.get(modelPath)), "Vosk 模型未下载");

        AppProperties props = new AppProperties();
        props.getStt().setModelPath(modelPath);
        VoskSttService stt = new VoskSttService(props);

        short[] pcm;
        try (InputStream in = new ClassPathResource("audio/test-user.wav").getInputStream()) {
            WavUtils.Wav wav = WavUtils.read(in.readAllBytes());
            pcm = WavUtils.toMono16k(wav.pcm(), wav.sampleRate(), wav.channels());
        }
        String text = stt.transcribe(pcm);
        System.out.println("VOSK_RESULT=" + text);
        assertThat(text).isNotBlank();
    }
}
