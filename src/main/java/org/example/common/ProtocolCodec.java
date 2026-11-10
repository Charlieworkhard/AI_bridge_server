package org.example.common;

/**
 * 设备端二进制帧协议：
 * v1 直接是 Opus 数据；v2 前 16 字节头（大端）；v3 前 4 字节头（大端）。
 */
public final class ProtocolCodec {

    private ProtocolCodec() {
    }

    public static byte[] unwrap(byte[] frame, int version) {
        if (frame == null || frame.length == 0) {
            return new byte[0];
        }
        if (version == 2) {
            if (frame.length < 16) {
                return new byte[0];
            }
            int size = readIntBE(frame, 12);
            if (size <= 0 || 16 + size > frame.length) {
                return new byte[0];
            }
            return copy(frame, 16, size);
        }
        if (version == 3) {
            if (frame.length < 4) {
                return new byte[0];
            }
            int size = readShortBE(frame, 2);
            if (size <= 0 || 4 + size > frame.length) {
                return new byte[0];
            }
            return copy(frame, 4, size);
        }
        return frame;
    }

    public static byte[] wrap(byte[] payload, int version, int timestampMs) {
        if (version == 2) {
            byte[] out = new byte[16 + payload.length];
            writeShortBE(out, 0, 2);
            writeShortBE(out, 2, 0);
            writeIntBE(out, 4, 0);
            writeIntBE(out, 8, timestampMs);
            writeIntBE(out, 12, payload.length);
            System.arraycopy(payload, 0, out, 16, payload.length);
            return out;
        }
        if (version == 3) {
            byte[] out = new byte[4 + payload.length];
            out[0] = 0;
            out[1] = 0;
            writeShortBE(out, 2, payload.length);
            System.arraycopy(payload, 0, out, 4, payload.length);
            return out;
        }
        return payload;
    }

    private static byte[] copy(byte[] src, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(src, offset, out, 0, length);
        return out;
    }

    private static int readShortBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private static int readIntBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static void writeShortBE(byte[] b, int off, int v) {
        b[off] = (byte) (v >> 8);
        b[off + 1] = (byte) v;
    }

    private static void writeIntBE(byte[] b, int off, int v) {
        b[off] = (byte) (v >> 24);
        b[off + 1] = (byte) (v >> 16);
        b[off + 2] = (byte) (v >> 8);
        b[off + 3] = (byte) v;
    }
}
