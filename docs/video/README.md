# Console demo video

Records the console demo with Playwright and assembles `build/modak-console.mp4`
with ffmpeg and edge-tts narration.

Needs `ffmpeg` on PATH and the local stack running with the example data
(`docker compose up -d --build`, then `./example/run.sh`).

```bash
npm install
python3 -m venv tts-venv && tts-venv/bin/pip install edge-tts

node cards.mjs
node captions.mjs
node record.mjs <scene>   # once per scene, see the list in record.mjs
node finalize.mjs
```

`shots.mjs` refreshes the console screenshots in `docs/assets` instead.
