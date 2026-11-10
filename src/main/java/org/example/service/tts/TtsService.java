package org.example.service.tts;

public interface TtsService {

    /** 文本转 16k 单声道 PCM。 */
    short[] synthesize(String text);
}
