package org.example.service.stt;

public interface SttService {

    /** 16k 单声道 PCM 转文本。 */
    String transcribe(short[] pcm16k);
}
