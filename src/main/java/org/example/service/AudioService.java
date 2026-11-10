package org.example.service;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import org.example.dao.model.DeviceSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME_SAMPLES = 960;
    public static final int FRAME_MS = 60;

    public OpusDecoder newDecoder() {
        try {
            return new OpusDecoder(SAMPLE_RATE, 1);
        } catch (OpusException e) {
            throw new IllegalStateException("创建 Opus 解码器失败", e);
        }
    }

    public OpusEncoder newEncoder() {
        try {
            OpusEncoder encoder = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
            encoder.setBitrate(24000);
            encoder.setComplexity(5);
            return encoder;
        } catch (OpusException e) {
            throw new IllegalStateException("创建 Opus 编码器失败", e);
        }
    }

    public short[] decode(DeviceSession session, byte[] opus) {
        if (opus == null || opus.length == 0 || session.getDecoder() == null) {
            return new short[0];
        }
        short[] out = new short[FRAME_SAMPLES];
        try {
            int n = session.getDecoder().decode(opus, 0, opus.length, out, 0, FRAME_SAMPLES, false);
            return n == FRAME_SAMPLES ? out : Arrays.copyOf(out, Math.max(n, 0));
        } catch (OpusException e) {
            log.warn("Opus 解码失败, 长度={}: {}", opus.length, e.getMessage());
            return new short[0];
        }
    }

    public List<byte[]> encode(OpusEncoder encoder, short[] pcm) {
        List<byte[]> frames = new ArrayList<>();
        if (encoder == null || pcm == null || pcm.length == 0) {
            return frames;
        }
        byte[] out = new byte[1500];
        for (int offset = 0; offset < pcm.length; offset += FRAME_SAMPLES) {
            short[] chunk = new short[FRAME_SAMPLES];
            int len = Math.min(FRAME_SAMPLES, pcm.length - offset);
            System.arraycopy(pcm, offset, chunk, 0, len);
            try {
                int n = encoder.encode(chunk, 0, FRAME_SAMPLES, out, 0, out.length);
                frames.add(Arrays.copyOf(out, n));
            } catch (OpusException e) {
                log.warn("Opus 编码失败: {}", e.getMessage());
            }
        }
        return frames;
    }
}
