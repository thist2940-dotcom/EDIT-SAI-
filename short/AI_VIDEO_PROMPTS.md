# AI video prompts — shot pack

Generate **7 separate clips**, vertical **9:16**, and save them with these exact
filenames into `short/videos/` (then I cut them to the frame):

| File | Shot | Length to generate | Narration beat |
|---|---|---|---|
| `00_hook.mp4` | Tusk breaks through ice | 4s | 0:00–0:03 |
| `01_narwhal.mp4` | Narwhal swims at camera | 8s | 0:03–0:10 |
| `02_glassfrog.mp4` | Glass frog macro | 8s | 0:10–0:17 |
| `03_fairyarmadillo.mp4` | Fairy armadillo on sand | 8s | 0:17–0:24 |
| `04_goblinshark.mp4` | Goblin shark jaw launch | 8s | 0:24–0:31 |
| `05_bluedragon.mp4` | Blue dragon on surface | 7s | 0:31–0:37 |
| `06_outro.mp4` | Light rays in blue water | 4s | 0:37–0:40 |

Generate slightly **longer** than needed — I trim and re-time. Extra length is never wasted;
short length is.

---

## Two rules that decide quality

1. **Use image-to-video.** Upload the stills I already made in `short/assets/raw/` as the
   *first frame* (Veo 3 / Kling 2.x / Runway Gen-4 / Hailuo all support this). You get my exact
   composition and colour grade, and the model only has to animate it. Text-to-video alone will
   drift off-model and cost you retries.
2. **Never let the model draw text.** Every prompt below says "no text" — models still try.
   Check each clip at 1× before uploading.

---

## Prompts (copy-paste)

### 00_hook.mp4 — the tusk
> Vertical 9:16 cinematic. A narwhal's long spiralled tusk thrusts up through dark arctic sea
> ice at dusk, shattering the crust. Sea spray and mist fly toward the lens. Slow push-in,
> shallow depth of field, cold teal and slate colour grade, moody backlight, heavy sky in the
> top third. Photorealistic documentary footage, 35mm, fine grain. No text, no captions,
> no watermark, no logos.

### 01_narwhal.mp4 — the swim
> Vertical 9:16 underwater documentary. A single narwhal swims slowly straight toward camera
> through clear arctic water, its spiralled tusk pointed forward, mottled grey spotted skin.
> Camera holds steady and drifts back slightly as it approaches. Shafts of pale sunlight rake
> down from the surface, suspended particles drifting. Cold muted grade. Photorealistic,
> natural history film look, subtle handheld float. No text, no captions, no watermark.

### 02_glassfrog.mp4 — the see-through frog
> Vertical 9:16 macro nature footage. A tiny lime-green glass frog clings to a wet tropical
> leaf; its translucent belly is see-through, red heart visibly pulsing and coiled intestines
> shifting beneath the skin. Extremely shallow depth of field, water droplets beading on the
> leaf, dark green jungle bokeh behind. Camera creeps in slowly from a medium macro to a tight
> macro. Photorealistic, National Geographic macro style. No text, no captions, no watermark.

### 03_fairyarmadillo.mp4 — the tiny armadillo
> Vertical 9:16 natural history footage. A pink fairy armadillo — very small, salmon-pink
> armoured shell plate over its back, pale silky fur along its sides — trundles across pale
> desert sand, its tiny clawed front feet kicking up fine grains. Warm soft late-afternoon
> light, low camera angle at ground level, blurred sandy background, gentle slow-motion.
> Photorealistic, shallow depth of field. No text, no captions, no watermark.

### 04_goblinshark.mp4 — the jaw launch
> Vertical 9:16 deep-sea submersible footage. A goblin shark in pitch-black water: pale
> pink-grey flattened snout, small eyes. Mid-shot, its entire jaw thrusts violently forward out
> of its mouth, needle teeth extending, then retracts. Harsh single spotlight from camera
> left, floating marine snow lit up in the beam, pure black surroundings, slight camera shake
> on the strike. Photorealistic, unsettling, documentary. No text, no captions, no watermark.

### 05_bluedragon.mp4 — the blue dragon
> Vertical 9:16 macro ocean-surface footage. A blue dragon sea slug (Glaucus atlanticus) floats
> upside down on a calm ocean surface, brilliant electric cobalt-blue body with silver-white
> underside, finger-like cerata fanned out like tiny wings, iridescent shimmer. Gentle ripples
> and sun glints, camera drifts slowly over it. Photorealistic macro, crisp detail, sunlight
> refracting. No text, no captions, no watermark.

### 06_outro.mp4 — the empty water
> Vertical 9:16 underwater footage. Empty deep blue ocean with dramatic god rays streaming down
> from the surface, drifting marine snow, a distant school of small fish in silhouette. Camera
> tilts slowly upward toward the light, deep indigo darkness at the bottom, large clean open
> centre of frame. Cinematic, moody, photorealistic, fine grain. No text, no captions, no
> watermark.

---

## If you'd rather use real stock footage

Search these on Pexels / Pixabay from your phone, download the vertical or 1080p file, and
rename to the table above. Best terms:

- `narwhal underwater`, `narwhal tusk ice`, `arctic whale`
- `glass frog macro`, `transparent frog`, `tree frog macro`
- `armadillo sand close up`
- `goblin shark`, `deep sea shark`, `submersible deep ocean`
- `blue dragon sea slug`, `nudibranch blue`, `ocean surface macro water`
- `underwater god rays`, `sunlight underwater blue`

A real clip beats an AI clip for this genre — the "is this real?" tension is the entire hook.
Mix if you like: real stock for the animals, AI for the hook and outro.

---

## Once the clips are in

Drop them in `short/videos/` and tell me — I run the builder and deliver the final 40s MP4 with
the captions and title cards burned in, already trimmed to the beat.
