#!/usr/bin/env python3
"""
SkyFrame notification audio generator.

Emits two .ogg files for the life_safety and severe_weather notification
channels. Both are 1050 Hz sine waves, inspired by NOAA Weather Radio's
Warning Alarm Tone (WAT) character.

LEGAL CONSTRAINT (47 CFR section 11.45):
The EAS Attention Signal (853 Hz + 960 Hz dual tones) and SAME header bursts
are reserved for actual Emergency Alert System broadcasts. Reproducing them
in non-EAS contexts violates federal law. This generator emits ONLY a single
1050 Hz tone and does NOT combine frequencies that would approximate either
EAS signal. Any future edits MUST preserve this constraint.

Usage:
    python3 tools/generate-notification-audio.py

Requires:
    - Python 3.8+
    - numpy
    - ffmpeg in PATH (for libvorbis .ogg encoding)

Outputs:
    app/src/main/res/raw/notification_life_safety.ogg   (looping channel sound)
    app/src/main/res/raw/notification_severe.ogg        (single-play channel sound)
"""

from __future__ import annotations

import math
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

SAMPLE_RATE = 44_100
FREQ_HZ = 1050.0  # NWR WAT character; explicitly NOT EAS Attention Signal


def gen_tone(duration_s: float, fade_ms: float = 8.0) -> np.ndarray:
    """Generate a mono 16-bit PCM 1050 Hz sine wave with short cosine fades
    on each end to avoid click artifacts."""
    n = int(SAMPLE_RATE * duration_s)
    t = np.arange(n) / SAMPLE_RATE
    sine = np.sin(2.0 * math.pi * FREQ_HZ * t)

    fade_n = int(SAMPLE_RATE * (fade_ms / 1000.0))
    if fade_n > 0 and n > 2 * fade_n:
        ramp = 0.5 - 0.5 * np.cos(np.linspace(0.0, math.pi, fade_n))
        sine[:fade_n] *= ramp
        sine[-fade_n:] *= ramp[::-1]

    # 80% peak amplitude to leave headroom; system mixes with other audio.
    return (sine * 0.8 * 32_767).astype(np.int16)


def gen_silence(duration_s: float) -> np.ndarray:
    return np.zeros(int(SAMPLE_RATE * duration_s), dtype=np.int16)


def write_wav(path: Path, samples: np.ndarray) -> None:
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(samples.tobytes())


def wav_to_ogg(wav_path: Path, ogg_path: Path) -> None:
    """Encode WAV -> Ogg Vorbis via ffmpeg. -q:a 5 is ~160 kbps quality;
    overkill for a sine wave but tiny on disk (<10 KB each)."""
    result = subprocess.run(
        ["ffmpeg", "-y", "-i", str(wav_path), "-c:a", "libvorbis", "-q:a", "5", str(ogg_path)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        sys.stderr.write(result.stderr)
        raise RuntimeError(f"ffmpeg failed for {ogg_path.name}")


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    out_dir = repo_root / "app" / "src" / "main" / "res" / "raw"
    out_dir.mkdir(parents=True, exist_ok=True)

    tmp_dir = repo_root / "build" / "audio-gen"
    tmp_dir.mkdir(parents=True, exist_ok=True)

    # life_safety: 3 cycles of (500ms tone + 1000ms silence). System channel
    # config sets the channel as looping, so a finite-length file with 3
    # cycles gives natural fade in case looping is preempted.
    cycle = np.concatenate([gen_tone(0.5), gen_silence(1.0)])
    life_safety = np.tile(cycle, 3)
    life_safety_wav = tmp_dir / "life_safety.wav"
    write_wav(life_safety_wav, life_safety)
    wav_to_ogg(life_safety_wav, out_dir / "notification_life_safety.ogg")

    # severe: single ~800ms tone with fades.
    severe = gen_tone(0.8, fade_ms=20.0)
    severe_wav = tmp_dir / "severe.wav"
    write_wav(severe_wav, severe)
    wav_to_ogg(severe_wav, out_dir / "notification_severe.ogg")

    print(f"Wrote {out_dir / 'notification_life_safety.ogg'}")
    print(f"Wrote {out_dir / 'notification_severe.ogg'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
