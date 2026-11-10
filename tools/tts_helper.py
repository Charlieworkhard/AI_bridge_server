"""在线 TTS 合成助手：文本 -> 16kHz 单声道 PCM16 原始字节。

被后端 EdgeTtsService 以子进程方式调用：
    python tts_helper.py --text-file in.txt --out out.pcm --voice zh-CN-XiaoxiaoNeural --rate +0%

依赖：edge-tts（微软 Edge 在线语音）、miniaudio（自带 mp3 解码 + 重采样）。
输出固定为小端 16bit、单声道、16kHz，Java 侧直接按 short[] 读即可。
"""

import argparse
import asyncio
import os
import sys
import tempfile

import edge_tts
import miniaudio


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--text-file", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--voice", default="zh-CN-XiaoxiaoNeural")
    parser.add_argument("--rate", default="+0%")
    parser.add_argument("--volume", default="+0%")
    parser.add_argument("--pitch", default="+0Hz")
    return parser.parse_args()


async def synthesize(text, args, mp3_path):
    communicate = edge_tts.Communicate(
        text, args.voice, rate=args.rate, volume=args.volume, pitch=args.pitch
    )
    await communicate.save(mp3_path)


def main():
    args = parse_args()
    with open(args.text_file, "r", encoding="utf-8") as f:
        text = f.read().strip()
    if not text:
        print("empty text", file=sys.stderr)
        return 2

    fd, mp3_path = tempfile.mkstemp(suffix=".mp3")
    os.close(fd)
    try:
        asyncio.run(synthesize(text, args, mp3_path))
        decoded = miniaudio.decode_file(
            mp3_path,
            output_format=miniaudio.SampleFormat.SIGNED16,
            nchannels=1,
            sample_rate=16000,
        )
        with open(args.out, "wb") as f:
            f.write(decoded.samples.tobytes())
    finally:
        try:
            os.remove(mp3_path)
        except OSError:
            pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
