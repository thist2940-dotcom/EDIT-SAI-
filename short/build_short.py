#!/usr/bin/env python3
"""
EDIT SAI — 40s vertical Shorts builder.

Drop real clips in short/videos/ and they are cut to the beat. Any segment with no
clip falls back to a still from short/assets/raw/ with Ken Burns motion.

Every segment gets:
  * top + bottom gradient scrims so text always reads
  * bold stroke-safe captions, title cards, accent colours
  * hard cuts on the narration beat (external voice-over drops straight in)
  * silent video out + SRT sidecar

Filenames are matched by their number prefix, e.g. videos/01_narwhal.mp4
matches segment 01_narwhal (any extension: mp4, mov, webm, mkv).

Usage: python3 build_short.py
"""

import json
import os
import re
import shutil
import subprocess

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, "assets", "raw")
VIDEOS = os.path.join(ROOT, "videos")
BUILD = os.path.join(ROOT, "build")
SEGS = os.path.join(BUILD, "segs")
TEXT = os.path.join(BUILD, "text")
OUT = os.path.join(ROOT, "out")

FONT_BOLD = "/home/user/.tools/matplotlib/mpl-data/fonts/ttf/DejaVuSans-Bold.ttf"
FFMPEG = "/home/user/.tools/imageio_ffmpeg/binaries/ffmpeg-linux-x86_64-v9.0.2"

W, H, FPS = 1080, 1920, 30
VIDEO_EXT = (".mp4", ".mov", ".webm", ".mkv", ".m4v")

YELLOW = "#FFD400"
WHITE = "#FFFFFF"

# ---------------------------------------------------------------------------
# TIMELINE — 40.0s. These numbers ARE the narration cues in SCRIPT.md.
# ---------------------------------------------------------------------------
SEGMENTS = [
    dict(key="00_hook", dur=3.0, zoom="in", pan="up"),

    dict(key="01_narwhal", dur=7.0, zoom="in", pan="center",
         kicker="NUMBER 5", title="NARWHAL",
         caps=[(0.90, 3.00, [("That's ", WHITE), ("NOT", YELLOW), (" a horn", WHITE)]),
               (3.00, 5.00, [("It's a 9-foot tooth", WHITE),
                             ("through its skull", WHITE)]),
               (5.00, 7.00, [("It can feel the water", WHITE),
                             ("change around it", YELLOW)])]),

    dict(key="02_glassfrog", dur=7.0, zoom="out", pan="center",
         kicker="NUMBER 4", title="GLASS FROG",
         caps=[(0.80, 2.80, [("Its skin is", WHITE),
                             ("SEE-THROUGH", YELLOW)]),
               (2.80, 4.90, [("You can watch its", WHITE),
                             ("heart beating", YELLOW)]),
               (4.90, 7.00, [("and dinner digesting", WHITE)])]),

    dict(key="03_fairyarmadillo", dur=7.0, zoom="in", pan="center",
         kicker="NUMBER 3", title="PINK FAIRY ARMADILLO",
         caps=[(0.80, 2.80, [("Smaller than your hand", WHITE)]),
               (2.80, 5.00, [("Its armour's so loose", WHITE),
                             ("it can't roll up", YELLOW)]),
               (5.00, 7.00, [("It lives buried in sand", WHITE)])]),

    dict(key="04_goblinshark", dur=7.0, zoom="in", pan="down",
         kicker="NUMBER 2", title="GOBLIN SHARK",
         caps=[(0.80, 2.80, [("Older than trees", WHITE)]),
               (2.80, 5.00, [("When it hunts, its jaw", WHITE)]),
               (5.00, 7.00, [("SHOOTS out of its face", YELLOW)])]),

    dict(key="05_bluedragon", dur=6.0, zoom="out", pan="center",
         kicker="NUMBER 1", title="BLUE DRAGON",
         caps=[(0.80, 2.60, [("A sea slug that floats", WHITE),
                             ("upside down", WHITE)]),
               (2.60, 4.30, [("Eats venomous jellyfish", WHITE)]),
               (4.30, 6.00, [("and steals the stingers", YELLOW)])]),

    dict(key="06_outro", dur=3.0, zoom="in", pan="center"),
]

HOOK_OVERLAYS = [
    (0.00, 1.40, [
        dict(tokens=[("THESE 5", WHITE)], y=225, size=124),
        dict(tokens=[("LOOK ", WHITE), ("FAKE", YELLOW)], y=380, size=124),
    ]),
    (1.40, 3.00, [
        dict(tokens=[("they're", WHITE)], y=1020, size=66),
        dict(tokens=[("100% REAL", YELLOW)], y=1105, size=150),
    ]),
]

OUTRO_OVERLAYS = [
    (0.00, 3.00, [
        dict(tokens=[("PART 2", YELLOW)], y=280, size=150, tracking=8),
        dict(tokens=[("follow so you don't", WHITE)], y=470, size=62),
        dict(tokens=[("miss it", WHITE)], y=550, size=62),
    ]),
]


# ---------------------------------------------------------------------------
# text layers
# ---------------------------------------------------------------------------
def _font(size):
    return ImageFont.truetype(FONT_BOLD, size)


def _draw_tokens(d, tokens, y, size, tracking=0, max_w=W - 150):
    f = _font(size)
    widths = [d.textlength(t, font=f) for t, _ in tokens]
    total = sum(widths) + tracking * max(0, sum(len(t) for t, _ in tokens) - 1)
    # auto-fit: shrink long titles/captions instead of letting them bleed off frame
    while total > max_w and size > 38:
        size -= 2
        f = _font(size)
        widths = [d.textlength(t, font=f) for t, _ in tokens]
        total = sum(widths) + tracking * max(0, sum(len(t) for t, _ in tokens) - 1)
    x = (W - total) / 2.0
    for (txt, colour), w in zip(tokens, widths):
        if tracking:
            cx = x
            for ch in txt:
                d.text((cx + 3, y + 5), ch, font=f, fill=(0, 0, 0, 190),
                       stroke_width=8, stroke_fill=(0, 0, 0, 190))
                d.text((cx, y), ch, font=f, fill=colour,
                       stroke_width=8, stroke_fill="#0A0A0A")
                cx += d.textlength(ch, font=f) + tracking
        else:
            d.text((x + 3, y + 5), txt, font=f, fill=(0, 0, 0, 190),
                   stroke_width=8, stroke_fill=(0, 0, 0, 190))
            d.text((x, y), txt, font=f, fill=colour,
                   stroke_width=8, stroke_fill="#0A0A0A")
        x += w + tracking * len(txt)


def text_png(name, blocks):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for b in blocks:
        _draw_tokens(d, b["tokens"], b.get("y", 200), b.get("size", 80),
                     b.get("tracking", 0))
    p = os.path.join(TEXT, name + ".png")
    im.save(p)
    return p


def caption_blocks(lines, size=64, y0=1400, lead=1.22):
    out, y = [], y0
    for ln in lines:
        if isinstance(ln, str):
            toks = [(ln, WHITE)]
        elif isinstance(ln, tuple):
            toks = [ln]
        else:
            toks = list(ln)
        out.append(dict(tokens=toks, y=int(y), size=size))
        y += size * lead
    return out


def scrims():
    top = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = top.load()
    for y in range(760):
        a = int(150 * (1 - y / 760.0) ** 1.5)
        for x in range(W):
            px[x, y] = (0, 0, 0, a)
    p1 = os.path.join(TEXT, "_scrim_top.png")
    top.save(p1)

    bot = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = bot.load()
    for y in range(1080, H):
        t = (y - 1080) / float(H - 1080)
        a = int(205 * (t ** 1.35))
        for x in range(W):
            px[x, y] = (0, 0, 0, a)
    p2 = os.path.join(TEXT, "_scrim_bot.png")
    bot.save(p2)
    return p1, p2


# ---------------------------------------------------------------------------
# media helpers
# ---------------------------------------------------------------------------
def find_clip(key):
    if not os.path.isdir(VIDEOS):
        return None
    for f in sorted(os.listdir(VIDEOS)):
        stem, ext = os.path.splitext(f)
        if ext.lower() in VIDEO_EXT and (stem == key or stem.startswith(key)):
            return os.path.join(VIDEOS, f)
    return None


def probe_duration(path):
    r = subprocess.run([FFMPEG, "-hide_banner", "-i", path],
                       capture_output=True, text=True)
    m = re.search(r"Duration: (\d+):(\d+):(\d+\.\d+)", r.stderr)
    if not m:
        return None
    h, mi, s = m.groups()
    return int(h) * 3600 + int(mi) * 60 + float(s)


def still_filter(dur, frames, zoom, pan):
    big = (2160, 3840)
    z = ("min(1.0+0.0013*on,1.18)" if zoom == "in"
         else "max(1.18-0.0013*on,1.0)")
    x = "iw/2-(iw/zoom/2)"
    if pan == "up":
        y = "(ih/2-(ih/zoom/2))-((ih*0.05)*on/%d)" % frames
    elif pan == "down":
        y = "(ih/2-(ih/zoom/2))+((ih*0.05)*on/%d)" % frames
    else:
        y = "ih/2-(ih/zoom/2)"
    return ("scale=%d:%d:force_original_aspect_ratio=increase,"
            "crop=%d:%d," % (big[0], big[1], big[0], big[1]) +
            "zoompan=z='%s':x='%s':y='%s':d=%d:s=%dx%d:fps=%d,"
            % (z, x, y, frames, W, H, FPS) +
            "eq=contrast=1.06:saturation=1.10,vignette=PI/5")


def video_filter(path, dur):
    """Scale/crop to 9:16, gently slow down a too-short clip, trim to the beat."""
    base = ("fps=%d,scale=%d:%d:force_original_aspect_ratio=increase,"
            "crop=%d:%d,setsar=1," % (FPS, W, H, W, H))
    cd = probe_duration(path)
    if cd and cd < dur - 0.05:
        stretch = dur / cd
        if stretch <= 1.6:                     # <=60% slower: use slow motion
            return base + "setpts=PTS*%.4f,trim=0:%.3f,setpts=PTS-STARTPTS," % (stretch, dur) + \
                   "eq=contrast=1.05:saturation=1.08"
    return base + "trim=0:%.3f,setpts=PTS-STARTPTS," % dur + \
           "eq=contrast=1.05:saturation=1.08"


# ---------------------------------------------------------------------------
# render
# ---------------------------------------------------------------------------
def render_segment(idx, seg, scrim_top, scrim_bot):
    dur, frames = seg["dur"], int(round(seg["dur"] * FPS))
    clip = find_clip(seg["key"])
    still = os.path.join(RAW, seg["key"] + ".png")
    src = clip or still
    mode = "VIDEO" if clip else "still"

    overlays = []
    if seg["key"] == "00_hook":
        for k, (t0, t1, blocks) in enumerate(HOOK_OVERLAYS):
            overlays.append((text_png("hook_%d" % k, blocks), t0, t1))
    elif seg["key"] == "06_outro":
        for k, (t0, t1, blocks) in enumerate(OUTRO_OVERLAYS):
            overlays.append((text_png("outro_%d" % k, blocks), t0, t1))
    else:
        overlays.append((text_png("title_%d" % idx, [
            dict(tokens=[(seg["kicker"], YELLOW)], y=150, size=56, tracking=8),
            dict(tokens=[(seg["title"], WHITE)], y=238, size=92, tracking=2),
        ]), 0.0, dur))
    for k, (t0, t1, lines) in enumerate(seg.get("caps", [])):
        overlays.append((text_png("cap_%d_%d" % (idx, k), caption_blocks(lines)),
                         t0, t1))

    if clip:
        inputs = ["-stream_loop", "-1", "-i", clip] if (probe_duration(clip) or 0) < dur - 0.05 \
            else ["-i", clip]
        first = "[0:v]%s[v0]" % video_filter(clip, dur)
    else:
        inputs = ["-i", still]
        first = "[0:v]%s[v0]" % still_filter(dur, frames, seg["zoom"], seg["pan"])

    inputs += ["-loop", "1", "-framerate", str(FPS), "-i", scrim_top,
               "-loop", "1", "-framerate", str(FPS), "-i", scrim_bot]
    for p, _, _ in overlays:
        inputs += ["-loop", "1", "-framerate", str(FPS), "-i", p]

    fc = [first,
          "[v0][1:v]overlay=0:0[v1]",
          "[v1][2:v]overlay=0:0[v2]"]
    last = "v2"
    for n, (_, t0, t1) in enumerate(overlays):
        nxt = "o%d" % n
        fc.append("[%s][%d:v]overlay=0:0:enable='between(t,%.3f,%.3f)'[%s]"
                  % (last, n + 3, t0, t1 - 0.001, nxt))
        last = nxt
    fc.append("[%s]format=yuv420p[vout]" % last)

    dst = os.path.join(SEGS, "seg_%02d.mp4" % idx)
    subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error"] + inputs + [
        "-filter_complex", ";".join(fc),
        "-map", "[vout]", "-an", "-frames:v", str(frames), "-r", str(FPS),
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
        "-pix_fmt", "yuv420p", "-g", str(FPS * 2), dst], check=True)
    print("  %02d  %-18s %-6s %5.1fs" % (idx, seg["key"], mode, dur))
    return dst, mode


def srt_time(t):
    ms = int(round(t * 1000))
    return "%02d:%02d:%02d,%03d" % (ms // 3600000, ms // 60000 % 60,
                                    ms // 1000 % 60, ms % 1000)


def main():
    for D in (SEGS, TEXT, OUT):
        shutil.rmtree(D, ignore_errors=True)
        os.makedirs(D, exist_ok=True)
    os.makedirs(VIDEOS, exist_ok=True)

    st, sb = scrims()
    print("\nrendering 40s short...")
    files, durs, modes = [], [], []
    for i, seg in enumerate(SEGMENTS):
        f, m = render_segment(i, seg, st, sb)
        files.append(f)
        durs.append(seg["dur"])
        modes.append(m)

    lst = os.path.join(BUILD, "concat.txt")
    with open(lst, "w") as fh:
        for f in files:
            fh.write("file '%s'\n" % f)
    final = os.path.join(OUT, "animals_that_look_fake_1080x1920.mp4")
    subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
                    "-f", "concat", "-safe", "0", "-i", lst,
                    "-c", "copy", "-movflags", "+faststart", final], check=True)

    # SRT
    def cap_text(line):
        if isinstance(line, str):
            return line
        if isinstance(line, tuple):
            return line[0]
        return "".join(t[0] for t in line)

    rows, n, t0 = [], 1, 0.0
    for seg, d in zip(SEGMENTS, durs):
        if seg["key"] == "00_hook":
            rows.append((0.0, 1.40, "These five look fake. All five are real."))
        elif seg["key"] == "06_outro":
            rows.append((t0 + 0.2, t0 + 3.0,
                         "Which one got you? Comment below. Part two's already coming."))
        else:
            for a, b, cap in seg.get("caps", []):
                rows.append((t0 + a, t0 + b,
                             " ".join(cap_text(c) for c in cap).strip()))
        t0 += d

    with open(os.path.join(OUT, "captions.srt"), "w") as fh:
        for a, b, txt in rows:
            fh.write("%d\n%s --> %s\n%s\n\n" % (n, srt_time(a), srt_time(b), txt))
            n += 1

    total = sum(durs)
    with open(os.path.join(OUT, "timeline.json"), "w") as fh:
        json.dump(dict(total_seconds=round(total, 2), fps=FPS, size=[W, H],
                       sources=[dict(key=s["key"], dur=d, mode=m)
                                for s, d, m in zip(SEGMENTS, durs, modes)]),
                  fh, indent=2)

    n_vid = modes.count("VIDEO")
    print("\nDONE  %s" % final)
    print("      %.2fs · %d frames · %d/%d segments from real video"
          % (total, int(total * FPS), n_vid, len(modes)))
    if n_vid < len(modes):
        print("      NOTE: %d segment(s) used stills. Drop clips into short/videos/ "
              "to replace them." % (len(modes) - n_vid))


if __name__ == "__main__":
    main()
