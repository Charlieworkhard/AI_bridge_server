package org.example.common;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WavUtils {

    private WavUtils() {
    }

    public record Wav(short[] pcm, int sampleRate, int channels) {
    }

    /** 只支持 PCM16 的 wav。 */
    public static Wav read(byte[] bytes) {
        if (bytes == null || bytes.length < 44) {
            throw new IllegalArgumentException("wav 数据太短");
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.get(0) != 'R' || buf.get(1) != 'I' || buf.get(2) != 'F' || buf.get(3) != 'F') {
            throw new IllegalArgumentException("不是合法的 wav");
        }
        int sampleRate = 16000;
        int channels = 1;
        int bits = 16;
        int dataOffset = -1;
        int dataLength = 0;
        int pos = 12;
        while (pos + 8 <= bytes.length) {
            int id0 = bytes[pos] & 0xFF;
            int id1 = bytes[pos + 1] & 0xFF;
            int id2 = bytes[pos + 2] & 0xFF;
            int id3 = bytes[pos + 3] & 0xFF;
            int size = buf.getInt(pos + 4);
            if (size < 0 || pos + 8 + size > bytes.length) {
                break;
            }
            if (id0 == 'f' && id1 == 'm' && id2 == 't' && id3 == ' ') {
                channels = buf.getShort(pos + 10) & 0xFFFF;
                sampleRate = buf.getInt(pos + 12);
                bits = buf.getShort(pos + 22) & 0xFFFF;
            } else if (id0 == 'd' && id1 == 'a' && id2 == 't' && id3 == 'a') {
                dataOffset = pos + 8;
                dataLength = size;
                break;
            }
            pos += 8 + size + (size & 1);
        }
        if (dataOffset < 0 || dataLength <= 0) {
            throw new IllegalArgumentException("wav 中没有 data 段");
        }
        if (bits != 16) {
            throw new IllegalArgumentException("只支持 16bit wav，当前 " + bits);
        }
        int samples = dataLength / 2;
        short[] pcm = new short[samples];
        for (int i = 0; i < samples; i++) {
            pcm[i] = buf.getShort(dataOffset + i * 2);
        }
        return new Wav(pcm, sampleRate, channels);
    }

    public static short[] toMono16k(short[] pcm, int sampleRate, int channels) {
        short[] mono = pcm;
        if (channels > 1) {
            int frames = pcm.length / channels;
            mono = new short[frames];
            for (int i = 0; i < frames; i++) {
                int sum = 0;
                for (int c = 0; c < channels; c++) {
                    sum += pcm[i * channels + c];
                }
                mono[i] = (short) (sum / channels);
            }
        }
        return sampleRate == 16000 ? mono : resample(mono, sampleRate, 16000);
    }

    public static short[] resample(short[] in, int fromRate, int toRate) {
        if (fromRate == toRate || in.length == 0) {
            return in;
        }
        int outLength = (int) Math.round(in.length * (double) toRate / fromRate);
        short[] out = new short[outLength];
        double step = (double) fromRate / toRate;
        for (int i = 0; i < outLength; i++) {
            double pos = i * step;
            int i0 = (int) pos;
            int i1 = Math.min(i0 + 1, in.length - 1);
            double frac = pos - i0;
            out[i] = (short) Math.round(in[i0] * (1 - frac) + in[i1] * frac);
        }
        return out;
    }

    public static byte[] write(short[] pcm, int sampleRate) {
        int dataLength = pcm.length * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[]{'R', 'I', 'F', 'F'});
        buf.putInt(36 + dataLength);
        buf.put(new byte[]{'W', 'A', 'V', 'E'});
        buf.put(new byte[]{'f', 'm', 't', ' '});
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put(new byte[]{'d', 'a', 't', 'a'});
        buf.putInt(dataLength);
        for (short s : pcm) {
            buf.putShort(s);
        }
        return buf.array();
    }

    public static byte[] toLittleEndianBytes(short[] pcm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(pcm.length * 2);
        for (short s : pcm) {
            out.write(s & 0xFF);
            out.write((s >> 8) & 0xFF);
        }
        return out.toByteArray();
    }
}
