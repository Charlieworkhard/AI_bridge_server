package org.example;

import org.example.config.AppProperties;
import org.example.service.tts.SapiTtsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SapiTtsTest {

    @Test
    void synthesizeChineseSpeech() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        AppProperties props = new AppProperties();
        SapiTtsService tts = new SapiTtsService(props);
        short[] pcm = tts.synthesize("你好，我是小智。");
        assertThat(pcm.length).isGreaterThan(8000);
    }
}
