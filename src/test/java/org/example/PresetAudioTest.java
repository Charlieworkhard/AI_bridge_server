package org.example;

import org.example.common.WavUtils;
import org.example.dao.model.DeviceSession;
import org.example.service.AudioService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PresetAudioTest {

    @Test
    void presetAudioIsAudibleAfterOpusRoundTrip() throws Exception {
        short[] pcm;
        try (InputStream in = new ClassPathResource("audio/reply.wav").getInputStream()) {
            WavUtils.Wav wav = WavUtils.read(in.readAllBytes());
            pcm = WavUtils.toMono16k(wav.pcm(), wav.sampleRate(), wav.channels());
        }
        AudioService audio = new AudioService();
        DeviceSession session = new DeviceSession("t", "d", "c", null);
        session.setDecoder(audio.newDecoder());

        double sum = 0;
        int count = 0;
        for (byte[] frame : audio.encode(audio.newEncoder(), pcm)) {
            short[] out = audio.decode(session, frame);
            for (short s : out) {
                sum += (double) s * s;
                count++;
            }
        }
        double rms = Math.sqrt(sum / Math.max(1, count));
        System.out.println("PRESET_RMS=" + rms + " samples=" + count);
        assertThat(rms).isGreaterThan(300);
    }
}
