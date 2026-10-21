"""Synthesizes Gloss's 16s promo score (pad, bass, music-box arpeggio). Deterministic.
Run: python3 assets/audio/score.py  -> assets/audio/score.wav"""
import numpy as np, wave, os

SR = 44100
DUR = 16.0
N = int(SR * DUR)
t = np.arange(N) / SR

def hz(m): return 440.0 * 2 ** ((m - 69) / 12)

def lowpass(x, cut):
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.empty_like(x); acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y

def env(start, end, att=0.6, rel=1.2):
    e = np.zeros(N)
    s, en = int(start * SR), min(int((end + rel) * SR), N)
    seg = np.arange(en - s) / SR
    a = np.clip(seg / att, 0, 1)
    r = np.clip(1 - (seg - (end - start)) / rel, 0, 1)
    e[s:en] = a * np.where(seg > end - start, r, 1)
    return e

# Chords as MIDI notes, with times.
chords = [
    (0.0, 2.6, [53, 57, 60, 64]),          # Fmaj7
    (2.6, 5.3, [57, 60, 64, 67]),          # Am7
    (5.3, 8.0, [50, 57, 60, 64, 65]),      # Dm9-ish
    (8.0, 10.6, [46, 53, 57, 62]),         # Bbmaj7
    (10.6, 12.2, [48, 55, 60, 62, 65]),    # Csus
    (12.2, 16.0, [41, 53, 57, 60, 67]),    # Fadd9, resolve
]

pad = np.zeros(N)
for (a, b, notes) in chords:
    e = env(a, b, att=0.9 if a > 0 else 1.6, rel=1.4)
    for m in notes:
        f = hz(m)
        v = sum(2 * ((t * f * d) % 1) - 1 for d in (0.996, 1.0, 1.005)) / 3   # detuned saws
        v += 0.5 * np.sin(2 * np.pi * f * t)
        pad += e * v / len(notes)
pad = lowpass(lowpass(pad, 900), 1400) * 0.55
# Slow swell, darker at the start.
pad *= np.clip(0.35 + t / 6, 0, 1)

bass = np.zeros(N)
for (a, b, notes) in chords:
    f = hz(notes[0] - 12)
    bass += env(a, b, att=0.3, rel=0.8) * np.sin(2 * np.pi * f * t)
bass = lowpass(bass, 300) * 0.35

# Music-box: a bell tone per note.
def bell(at, m, vel=1.0):
    out = np.zeros(N); s = int(at * SR)
    if s >= N: return out
    L = min(int(2.2 * SR), N - s); x = np.arange(L) / SR; f = hz(m)
    tone = np.sin(2*np.pi*f*x) + 0.35*np.sin(2*np.pi*f*2.01*x)*np.exp(-x*6) + 0.15*np.sin(2*np.pi*f*3.98*x)*np.exp(-x*9)
    out[s:s+L] = vel * tone * np.exp(-x * 2.6) * np.clip(x / 0.004, 0, 1)
    return out

box = np.zeros(N)
beat = 60 / 92
arp_notes = {0: [72, 76, 79, 84], 1: [72, 76, 79, 81], 2: [74, 77, 81, 84], 3: [74, 77, 81, 82], 4: [72, 74, 77, 79], 5: [72, 77, 79, 84]}
def chord_at(x):
    for i, (a, b, _) in enumerate(chords):
        if a <= x < b: return i
    return 5
# Arp starts at the tap (2.2s), eighths; doubles density when the page fills (6.8s); rests for the lockup.
x = 2.2; k = 0
while x < 10.2:
    step = beat / 2 if x < 6.8 else beat / 4
    notes = arp_notes[chord_at(x)]
    box += bell(x, notes[k % len(notes)], 0.55 if x < 6.8 else 0.4)
    x += step; k += 1
# The lockup: a rising four-note figure as the gold bar draws.
for i, m in enumerate([72, 77, 79, 84]):
    box += bell(11.35 + i * 0.11, m, 0.7)
box = lowpass(box, 5200) * 0.22

mix = pad + bass + box
# Gentle fade out.
mix *= np.clip((DUR - t) / 2.2, 0, 1)
mix /= np.max(np.abs(mix)) + 1e-9
mix *= 0.7
stereo = np.stack([mix, np.roll(mix, int(0.012 * SR))], axis=1)  # a little width
data = (stereo * 32767).astype(np.int16)
out = os.path.join(os.path.dirname(__file__), "score.wav")
with wave.open(out, "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data.tobytes())
print("wrote", out)
