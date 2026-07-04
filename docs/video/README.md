# Modak videos

Two videos, each around two minutes, assembled with ffmpeg and edge-tts
narration: `build/modak-explainer.mp4` (what Modak is, the modes, how to
choose) and `build/modak-demo.mp4` (the console demo, recorded live with
Playwright).

The demo needs `ffmpeg` on PATH and the local stack running with the example
data (`docker compose up -d --build`, then `./example/run.sh`). The explainer
only needs `ffmpeg`.

```bash
npm install
python3 -m venv tts-venv && tts-venv/bin/pip install edge-tts

node cards.mjs
node captions.mjs

node explainer.mjs all      # animated explainer scenes
node finalize.mjs explainer

node record.mjs <scene>     # once per scene, see the list in record.mjs
node finalize.mjs demo
```

`shots.mjs` refreshes the console screenshots in `docs/assets` instead.
