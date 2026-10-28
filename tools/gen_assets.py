"""Ilova uchun melodiyalar, ovozli e'lonlar va ikonkani yaratadi.

Ishlatish: python3 tools/gen_assets.py  (ffmpeg va espeak-ng kerak)
"""
import math, os, struct, subprocess, sys, tempfile, wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
RAW = os.path.join(RES, "raw")
SR = 22050


def note(freq, dur, vol=0.5, decay=3.0, harmonics=(1.0, 0.4, 0.2, 0.1)):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        env = math.exp(-decay * t) * min(1.0, t * 80)
        s = sum(a * math.sin(2 * math.pi * freq * (k + 1) * t) for k, a in enumerate(harmonics))
        out.append(vol * env * s / sum(harmonics))
    return out


def mix_into(buf, start, samples):
    need = start + len(samples)
    if len(buf) < need:
        buf.extend([0.0] * (need - len(buf)))
    for i, s in enumerate(samples):
        buf[start + i] += s


def melody(seq, step, **kw):
    """seq: (freq yoki 0, uzunlik qadamlarda) ro'yxati."""
    buf, pos = [], 0
    for f, beats in seq:
        if f:
            mix_into(buf, pos, note(f, beats * step + 1.2, **kw))
        pos += int(beats * step * SR)
    return buf


def write_ogg(name, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    data = b"".join(struct.pack("<h", int(s / peak * 0.85 * 32767)) for s in samples)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data)
    out = os.path.join(RAW, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "3", out], check=True)
    os.remove(path)


# Hijoz maqomi (D asosida): D Eb F# G A Bb C D
D4, Eb4, Fs4, G4, A4, Bb4, C5, D5 = 293.66, 311.13, 369.99, 392.0, 440.0, 466.16, 523.25, 587.33
G3, A3 = 196.0, 220.0


def gen_melodies():
    hijoz = [(D4, 2), (Eb4, 1), (Fs4, 2), (G4, 1), (A4, 3), (0, 1), (A4, 1), (Bb4, 1), (A4, 1), (G4, 1), (Fs4, 2), (Eb4, 1), (D4, 3), (0, 1)]
    write_ogg("melody_hijoz", melody(hijoz * 3, 0.45, decay=1.6))
    bell = [(D5, 2), (A4, 2), (Fs4, 2), (D4, 4)]
    write_ogg("melody_bell", melody(bell * 4, 0.55, decay=1.2, harmonics=(1.0, 0.6, 0.35, 0.25, 0.1)))
    tong = [(G3, 2), (A3, 2), (D4, 2), (G4, 4), (Fs4, 2), (D4, 4), (0, 2)]
    write_ogg("melody_tong", melody(tong * 3, 0.5, decay=1.0, harmonics=(1.0, 0.2, 0.05)))
    beep = [(880.0, 1), (0, 1)] * 3 + [(0, 2)]
    write_ogg("melody_signal", melody(beep * 3, 0.25, decay=6.0, harmonics=(1.0, 0.3)))


ANNOUNCE = {
    "say_bomdod": "Bomdod namozi vaqti kirdi.",
    "say_quyosh": "Quyosh chiqdi.",
    "say_peshin": "Peshin namozi vaqti kirdi.",
    "say_juma": "Juma namozi vaqti kirdi.",
    "say_asr": "Asr namozi vaqti kirdi.",
    "say_shom": "Shom namozi vaqti kirdi.",
    "say_xufton": "Xufton namozi vaqti kirdi.",
    "say_iftor": "Iftor vaqti bo'ldi.",
    "say_saharlik": "Saharlik vaqti tugashiga o'ttiz daqiqa qoldi.",
}


def gen_announcements():
    for name, text in ANNOUNCE.items():
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            path = tmp.name
        subprocess.run(["espeak-ng", "-v", "uz+m3", "-s", "135", "-p", "38", "-a", "180", "-w", path, text], check=True)
        out = os.path.join(RAW, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-af", "apad=pad_dur=0.6",
                        "-c:a", "libvorbis", "-q:a", "4", out], check=True)
        os.remove(path)


def gen_icon():
    from PIL import Image, ImageDraw
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    big = 768
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, big - 1, big - 1], radius=170, fill=(7, 94, 69, 255))
    # Yarim oy
    d.ellipse([170, 150, 600, 580], fill=(255, 214, 92, 255))
    d.ellipse([270, 110, 680, 520], fill=(7, 94, 69, 255))
    # Yulduz
    cx, cy, r1, r2 = 520, 470, 70, 28
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = r1 if i % 2 == 0 else r2
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, fill=(255, 214, 92, 255))
    for k, px in sizes.items():
        img.resize((px, px), Image.LANCZOS).save(os.path.join(RES, "mipmap-" + k, "ic_launcher.png"))


if __name__ == "__main__":
    os.makedirs(RAW, exist_ok=True)
    gen_melodies()
    gen_announcements()
    gen_icon()
    print("tayyor")
