"""Writes the app's notification sounds (companion/android/app/src/main/res/raw/*.wav).

They are made here from plain sine waves, so they are ours to ship (no one else's recording). Run it again after changing
a note below. Each sound is a list of (frequency Hz, start s, length s, volume); every note rings out with a soft decay.
"""

import math
import pathlib
import struct
import wave

RATE = 22050
OUT = pathlib.Path(__file__).parent.parent / "companion" / "android" / "app" / "src" / "main" / "res" / "raw"

SOUNDS = {
    # Ding-dong: a high note, then a lower one that rings longer.
    "cd_doorbell": [(659.3, 0.0, 0.7, 0.8), (523.3, 0.45, 1.0, 0.8)],
    # A soft single ping.
    "cd_motion": [(880.0, 0.0, 0.5, 0.6)],
    # Two notes going up: something good or useful (a cheap rate, a car).
    "cd_energy": [(523.3, 0.0, 0.3, 0.6), (784.0, 0.18, 0.5, 0.6)],
    # A gentle blip for to-dos and reminders.
    "cd_todo": [(698.5, 0.0, 0.18, 0.55), (880.0, 0.14, 0.35, 0.55)],
    # Two low beats: something in the house's own workings needs a look.
    "cd_system": [(392.0, 0.0, 0.3, 0.7), (392.0, 0.35, 0.45, 0.7)],
}


def render(notes):
    length = max(start + dur for _, start, dur, _ in notes) + 0.05
    samples = [0.0] * int(length * RATE)
    for freq, start, dur, vol in notes:
        first = int(start * RATE)
        for i in range(int(dur * RATE)):
            t = i / RATE
            env = min(1.0, t / 0.008) * math.exp(-4.5 * t / dur)  # quick attack, soft decay
            tone = math.sin(2 * math.pi * freq * t) + 0.3 * math.sin(2 * math.pi * 2 * freq * t) + 0.1 * math.sin(2 * math.pi * 3 * freq * t)
            if first + i < len(samples):
                samples[first + i] += vol * env * tone / 1.4
    peak = max(1.0, max(abs(s) for s in samples))
    return [int(32767 * 0.9 * s / peak) for s in samples]


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, notes in SOUNDS.items():
        with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b"".join(struct.pack("<h", s) for s in render(notes)))
        print("wrote", name)
