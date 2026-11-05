# "5 Animals That Look Fake" — 40s Short

**Format:** 9:16 vertical · 1080×1920 · 30fps · 40.0 seconds
**Voice:** keep it dry. Deadpan sells the reveal. Do NOT read it like an ad.
**Music:** dark pulsing bed, ~90 BPM, no vocals. Duck it ~-14dB under the voice.

---

## The narration (humanized)

> Numbers in brackets are beat timings — land each line on them.

**[0:00 – 0:03] HOOK**
"These five look fake. All five are real."

**[0:03 – 0:10] #5 NARWHAL**
"Five. The narwhal. That's not a horn — it's a nine-foot tooth shoved through its skull. And it can *feel* the water change."

**[0:10 – 0:17] #4 GLASS FROG**
"Four. The glass frog. Its skin is genuinely see-through. You can watch its heart beating, and yesterday's dinner digesting."

**[0:17 – 0:24] #3 PINK FAIRY ARMADILLO**
"Three. The pink fairy armadillo. Smaller than your hand. Its armour's so loose it can't even roll up properly."

**[0:24 – 0:31] #2 GOBLIN SHARK**
"Two. The goblin shark. Older than trees. And when it hunts — its whole jaw *shoots* out of its face."

**[0:31 – 0:37] #1 BLUE DRAGON**
"And one. The blue dragon. A sea slug that floats upside down, eats venomous jellyfish, and steals their stingers."

**[0:37 – 0:40] OUTRO**
"Which one got you? Comment below — part two's already coming."

**Total spoken:** 124 words ≈ 40s at natural short-form pace (3.1 words/sec).

---

## Delivery notes

1. **Hook is the whole video.** "These five look fake" must be flat and fast — no excitement. The contrast does the work.
2. **Pause 150ms** before each number word ("Five." / "Four.") — it makes the cut land.
3. **Stretch "shoots"** in the goblin shark line. That's the one moment to break the deadpan.
4. **Don't say "amazing", "crazy", "unbelievable".** The visuals are already loud.
5. Record one line per take, keep the best. Won't sound robotic that way.

## Audio spec

- Voice: mono/stereo, 48kHz, peak around **-3dB**, loudness around **-14 LUFS**.
- Music bed at **-26 to -22 LUFS**, sidechain-ducked under the voice.
- Add a soft sub-drop / riser on each number cut (0:03, 0:10, 0:17, 0:24, 0:31).
- Leave the last 0.4s mostly quiet so the outro reads.

## Adding your audio

```bash
ffmpeg -i out/animals_that_look_fake_1080x1920.mp4 -i your_voice.wav \
  -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest \
  out/FINAL_animals_that_look_fake.mp4
```
