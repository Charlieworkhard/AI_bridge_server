package org.example;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import org.example.dao.model.DeviceSession;
import org.example.service.AudioService;
import org.example.service.VadSegmenter;
import org.example.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioPipelineTest {

    private final AudioService audio = new AudioService();

    @Test
    void opusEncodeDecodeRoundTrip() {
        OpusEncoder encoder = audio.newEncoder();
        short[] pcm = tone(1000, 8000, 16000);
        List<byte[]> frames = audio.encode(encoder, pcm);
        assertThat(frames).hasSize(17);

        OpusDecoder decoder = audio.newDecoder();
        DeviceSession session = new DeviceSession("t", "d", "c", null);
        session.setDecoder(decoder);

        int decoded = 0;
        for (byte[] frame : frames) {
            short[] out = audio.decode(session, frame);
            assertThat(out).hasSize(AudioService.FRAME_SAMPLES);
            decoded += out.length;
        }
        assertThat(decoded).isEqualTo(17 * AudioService.FRAME_SAMPLES);
    }

    @Test
    void vadSegmentsSpeechThenSilence() {
        AppProperties props = new AppProperties();
        VadSegmenter vad = new VadSegmenter(props.getVad());
        short[] speech = tone(440, 6000, 960);
        short[] silence = new short[960];

        short[] result = null;
        for (int i = 0; i < 5; i++) {
            result = vad.accept(speech);
            assertThat(result).isNull();
        }
        for (int i = 0; i < 15 && result == null; i++) {
            result = vad.accept(silence);
        }
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThanOrEqualTo(5 * 960);
    }

    @Test
    void protocolWrapUnwrapForAllVersions() {
        byte[] opus = new byte[]{1, 2, 3, 4, 5};
        for (int version : new int[]{1, 2, 3}) {
            byte[] wrapped = org.example.common.ProtocolCodec.wrap(opus, version, 120);
            byte[] unwrapped = org.example.common.ProtocolCodec.unwrap(wrapped, version);
            assertThat(unwrapped).containsExactly(opus);
        }
    }

    private short[] tone(int hz, int amplitude, int samples) {
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = (short) (Math.sin(2 * Math.PI * hz * i / 16000.0) * amplitude);
        }
        return out;
    }
}
