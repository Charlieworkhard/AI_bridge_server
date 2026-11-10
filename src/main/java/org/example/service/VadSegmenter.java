package org.example.service;

import org.example.config.AppProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 基于短时能量的 VAD：连续有声音开始，静音超过阈值结束。
 * 设备按 60ms 一帧上传，这里按帧累积。
 */
public class VadSegmenter {

    private static final int FRAME_MS = 60;
    private static final int PRE_ROLL_FRAMES = 3;

    private final int frameSamples;
    private final int silenceFrames;
    private final int minFrames;
    private final int maxFrames;
    private final double energyThreshold;

    private final Deque<short[]> preRoll = new ArrayDeque<>();
    private final List<short[]> buffer = new ArrayList<>();

    private boolean active;
    private int speechRun;
    private int silenceRun;
    private double noiseFloor = 120;

    public VadSegmenter(AppProperties.Vad cfg) {
        this.frameSamples = 960;
        this.silenceFrames = Math.max(1, cfg.getSilenceMs() / FRAME_MS);
        this.minFrames = Math.max(1, cfg.getMinSpeechMs() / FRAME_MS);
        this.maxFrames = Math.max(minFrames, cfg.getMaxUtteranceMs() / FRAME_MS);
        this.energyThreshold = Math.max(1, cfg.getEnergyThreshold());
    }

    /** 返回切好的一句话；没有则返回 null。 */
    public short[] accept(short[] frame) {
        if (frame == null || frame.length == 0) {
            return null;
        }
        double rms = rms(frame);
        double threshold = Math.max(energyThreshold, noiseFloor * 3);

        if (!active) {
            if (rms > threshold) {
                speechRun++;
                if (speechRun >= 2) {
                    active = true;
                    for (short[] f : preRoll) {
                        buffer.add(f);
                    }
                    preRoll.clear();
                    buffer.add(frame);
                    silenceRun = 0;
                }
            } else {
                speechRun = 0;
                noiseFloor = noiseFloor * 0.95 + rms * 0.05;
                preRoll.addLast(frame);
                while (preRoll.size() > PRE_ROLL_FRAMES) {
                    preRoll.removeFirst();
                }
            }
            return null;
        }

        buffer.add(frame);
        if (rms > threshold) {
            silenceRun = 0;
        } else {
            silenceRun++;
        }
        if (silenceRun >= silenceFrames && buffer.size() >= minFrames) {
            return emit();
        }
        if (buffer.size() >= maxFrames) {
            return emit();
        }
        return null;
    }

    /** 手动停止录音时调用，把剩余缓冲吐出来。 */
    public short[] flush() {
        if (buffer.isEmpty() || (!active && speechRun == 0)) {
            reset();
            return null;
        }
        if (buffer.size() < minFrames) {
            reset();
            return null;
        }
        return emit();
    }

    public void reset() {
        preRoll.clear();
        buffer.clear();
        active = false;
        speechRun = 0;
        silenceRun = 0;
    }

    private short[] emit() {
        int total = 0;
        for (short[] f : buffer) {
            total += f.length;
        }
        short[] out = new short[total];
        int offset = 0;
        for (short[] f : buffer) {
            System.arraycopy(f, 0, out, offset, f.length);
            offset += f.length;
        }
        reset();
        return out;
    }

    public int frameSamples() {
        return frameSamples;
    }

    private static double rms(short[] frame) {
        double sum = 0;
        for (short s : frame) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / frame.length);
    }
}
