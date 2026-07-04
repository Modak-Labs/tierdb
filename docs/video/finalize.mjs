// Assembles build/modak-console.mp4 from the recorded scenes, cards,
// captions, and edge-tts narration.
import { execSync } from "child_process";
import fs from "fs";
import path from "path";

const DIR = import.meta.dirname;
process.chdir(path.join(DIR, "build"));
for (const d of ["seg", "nar"]) fs.mkdirSync(d, { recursive: true });

const sh = cmd => execSync(cmd, { stdio: ["ignore", "pipe", "pipe"] }).toString().trim();
const dur = f => parseFloat(sh(`ffprobe -v error -show_entries format=duration -of csv=p=0 "${f}"`));

// TTS=say uses the local macOS voice, anything else uses edge-tts neural voices.
const TTS = process.env.TTS || "edge";
const VOICE = process.env.VOICE || (TTS === "say" ? "Samantha" : "en-US-AndrewMultilingualNeural");
const EDGE = path.join(DIR, "tts-venv", "bin", "edge-tts");

// Playlist order. Cards stretch to fit their narration, live segments do not.
const parts = [
  { name: "card-intro", card: true, minDur: 3.2,
    say: "Modak federates Postgres and Apache Iceberg behind one SQL surface. It knows which tier holds what, and every query stays real time and consistent." },
  { name: "overview", speed: 1.0,
    say: "The console shows every registered table, its cut line, its snapshot, and the worker's activity, live." },
  { name: "card-seed", card: true, minDur: 3.0,
    say: "We start with an ordinary Postgres table, freshly registered with Modak." },
  { name: "card-sweep", card: true, minDur: 3.0,
    say: "The worker takes it from here." },
  { name: "tiering", speed: 2.5,
    say: "Both cold partitions move to Iceberg and are dropped from Postgres, live." },
  { name: "card-dml", card: true, minDur: 3.0,
    say: "Now, plain SQL against both tiers." },
  { name: "select", speed: 1.0,
    say: "One plain SELECT returns every row. Recent rows come from the heap, history from Iceberg." },
  { name: "explain", speed: 1.0,
    say: "Explain shows the routing. A pinned Iceberg snapshot serves the history." },
  { name: "update", speed: 1.25,
    say: "A plain UPDATE of a cold row is rewritten into two halves. The correction lands in the delta, visible immediately." },
  { name: "card-fold", card: true, minDur: 2.8,
    say: "Moments later, the worker folds the delta into Iceberg." },
  { name: "folded", speed: 1.0,
    say: "The delta is empty, and the corrected row is now served straight from the lake." },
  { name: "insert", speed: 1.25,
    say: "One INSERT, two destinations. The recent row goes to the heap, the historical row to the delta." },
  { name: "outro", speed: 1.0,
    say: "Tiering, folding, and partition premake, all in the background, all visible." },
  { name: "card-outro", card: true, minDur: 3.6,
    say: "Modak. Postgres as the hot tier of an open lakehouse." },
];

// 1. Narration audio, then measure it.
// "Postgress" keeps the voice from reading Postgres as "post-grees".
for (const p of parts) {
  const txt = p.say.replace(/Postgres/g, "Postgress").replace(/"/g, '\\"');
  if (TTS === "say") {
    execSync(`say -v "${VOICE}" -o nar/${p.name}.aiff "${txt}"`);
    p.nar = `nar/${p.name}.aiff`;
  } else {
    execSync(`${EDGE} --voice "${VOICE}" --text "${txt}" --write-media nar/${p.name}.mp3`);
    p.nar = `nar/${p.name}.mp3`;
  }
  p.narDur = dur(p.nar);
}

// 2. Video segments.
for (const p of parts) {
  if (p.card) {
    p.outDur = Math.max(p.minDur, p.narDur + 1.0);
    const fadeOut = (p.outDur - 0.4).toFixed(2);
    execSync(
      `ffmpeg -y -v error -loop 1 -t ${p.outDur.toFixed(2)} -i cards/${p.name}.png ` +
      `-vf "fps=30,fade=t=in:st=0:d=0.4,fade=t=out:st=${fadeOut}:d=0.4,format=yuv420p" ` +
      `-c:v libx264 -crf 18 -preset medium seg/${p.name}.mp4`
    );
  } else {
    const inDur = dur(`raw/${p.name}.webm`);
    p.outDur = inDur / p.speed;
    const fadeOut = (p.outDur - 0.4).toFixed(2);
    execSync(
      `ffmpeg -y -v error -i raw/${p.name}.webm -i caps/${p.name}.png ` +
      `-filter_complex "[0:v]setpts=PTS/${p.speed},fps=30[v];[v][1:v]overlay=0:0,` +
      `fade=t=in:st=0:d=0.35,fade=t=out:st=${fadeOut}:d=0.35,format=yuv420p[out]" ` +
      `-map "[out]" -an -c:v libx264 -crf 18 -preset medium seg/${p.name}.mp4`
    );
    p.outDur = dur(`seg/${p.name}.mp4`);
  }
  console.log(`${p.name}: video ${p.outDur.toFixed(1)}s, narration ${p.narDur.toFixed(1)}s`);
}

// 3. Offsets, with a warning if narration would spill past its segment.
let t = 0;
for (const p of parts) {
  p.offset = t + 0.4;
  if (p.narDur + 0.4 > p.outDur + 1.5) {
    console.warn(`WARN ${p.name}: narration ${p.narDur.toFixed(1)}s vs segment ${p.outDur.toFixed(1)}s`);
  }
  t += p.outDur;
}
console.log(`total ${t.toFixed(1)}s`);

// 4. Concat video, then mux the delayed narration tracks.
fs.writeFileSync("list.txt", parts.map(p => `file 'seg/${p.name}.mp4'`).join("\n") + "\n");
execSync(`ffmpeg -y -v error -f concat -safe 0 -i list.txt -c copy video-only.mp4`);

const inputs = parts.map(p => `-i ${p.nar}`).join(" ");
const delays = parts
  .map((p, i) => {
    const ms = Math.round(p.offset * 1000);
    return `[${i + 1}:a]aformat=sample_rates=44100:channel_layouts=stereo,adelay=${ms}|${ms}[a${i}]`;
  })
  .join(";");
const mix = parts.map((_, i) => `[a${i}]`).join("") + `amix=inputs=${parts.length}:normalize=0[a]`;
execSync(
  `ffmpeg -y -v error -i video-only.mp4 ${inputs} ` +
  `-filter_complex "${delays};${mix}" ` +
  `-map 0:v -map "[a]" -c:v copy -c:a aac -b:a 128k -shortest modak-console.mp4`
);
console.log(sh(`ffprobe -v error -show_entries format=duration,size -of default=noprint_wrappers=1 modak-console.mp4`));
console.log("DONE");
