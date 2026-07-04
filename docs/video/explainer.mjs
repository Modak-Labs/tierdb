// Records the animated explainer scenes as build/raw/x-<scene>.webm.
// Usage: node explainer.mjs <scene|all>, scenes are listed at the bottom.
import { chromium } from "playwright";
import fs from "fs";
import path from "path";

const OUT = path.join(import.meta.dirname, "build", "raw");
fs.mkdirSync(OUT, { recursive: true });

const BASE = `
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { width: 1920px; height: 1080px; background: #141414; overflow: hidden;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", sans-serif;
    color: #e6e6e6; position: relative; }
  .mono { font-family: "SF Mono", ui-monospace, Menlo, monospace; }
  .title { position: absolute; top: 84px; left: 0; width: 100%; text-align: center;
    font-size: 50px; font-weight: 600; letter-spacing: 0.01em; }
  .sub { position: absolute; top: 158px; left: 0; width: 100%; text-align: center;
    font-size: 27px; color: #999999; }
  .fade { opacity: 0; animation: fadein 0.7s ease forwards; }
  @keyframes fadein { to { opacity: 1; } }
  .tag { font-size: 22px; color: #999999; letter-spacing: 0.06em; text-transform: uppercase; }
  .part { width: 128px; height: 76px; border-radius: 10px; border: 1.5px solid #3a3a3a;
    background: #1d1d1d; color: #777777; display: flex; align-items: center; justify-content: center;
    font-family: "SF Mono", ui-monospace, Menlo, monospace; font-size: 22px;
    position: absolute; transition: all 0.8s ease; }
  .part.hot { background: #e6e6e6; border-color: #e6e6e6; color: #141414; font-weight: 600; }
  .zone { position: absolute; border: 1.5px solid #2c2c2c; border-radius: 16px; background: #181818; }
  .zonelabel { position: absolute; font-size: 24px; color: #999999; font-weight: 600; }
`;

// Each scene is HTML with animations driven by element-level keyframes.
// step(el, t, ...) helpers below generate per-element delays.
const css = (sel, t, frames) => `${sel} { animation: ${frames} forwards; animation-delay: ${t}s; }`;

const scenes = {};

/* ------------------------------------------------------------------ */
/* x-problem: partitions pile up, history drowns the working set       */
/* ------------------------------------------------------------------ */
scenes["x-problem"] = {
  dur: 14500,
  html() {
    const N = 10;
    const X0 = 240, GAP = 146, Y = 460;
    let parts = "", anims = "";
    for (let i = 0; i < N; i++) {
      const born = 0.8 + i * 1.05;
      const cool = i < N - 2 ? `, cool 1.2s ease ${(born + 2.4).toFixed(2)}s forwards` : "";
      parts += `<div class="part hot" id="p${i}" style="left:${X0 + i * GAP}px; top:${Y}px; opacity:0">p${i}</div>`;
      anims += `#p${i} { animation: appear 0.5s ease ${born.toFixed(2)}s forwards${cool}; }\n`;
    }
    return `<!DOCTYPE html><html><head><style>${BASE}
      @keyframes appear { from { opacity: 0; transform: translateY(18px); } to { opacity: 1; transform: none; } }
      @keyframes cool { from { opacity: 1; } to { opacity: 1; background: #1d1d1d; border-color: #3a3a3a; color: #777777; font-weight: 400; } }
      ${anims}
      .axis { position: absolute; left: ${X0 - 30}px; top: ${Y + 110}px; width: ${N * GAP + 40}px;
        border-top: 1.5px solid #2c2c2c; }
      .axislabel { position: absolute; top: ${Y + 124}px; font-size: 22px; color: #666666; }
      #growbar { position: absolute; left: ${X0}px; top: 760px; height: 14px; border-radius: 7px;
        background: #2a2a2a; width: ${N * GAP - 20}px; overflow: hidden; }
      #growfill { height: 100%; width: 0; background: #555555; border-radius: 7px;
        animation: grow 11s linear forwards; animation-delay: 1s; }
      @keyframes grow { to { width: 100%; } }
      #growlabel { position: absolute; left: ${X0}px; top: 800px; font-size: 24px; color: #999999; }
      #hotlabel { position: absolute; top: ${Y - 64}px; font-size: 23px; color: #e6e6e6;
        left: ${X0}px; animation: chase 10.5s linear forwards; animation-delay: 1.4s; }
      @keyframes chase { to { left: ${X0 + (N - 2) * GAP}px; } }
    </style></head><body>
      <div class="title fade">Most tables age</div>
      <div class="sub fade" style="animation-delay:.3s">Recent rows stay busy. History only piles up.</div>
      <div id="hotlabel">&darr; the working set</div>
      ${parts}
      <div class="axis"></div>
      <div class="axislabel" style="left:${X0 - 30}px">older</div>
      <div class="axislabel" style="left:${X0 + N * GAP - 50}px">newer</div>
      <div id="growbar"><div id="growfill"></div></div>
      <div id="growlabel">table size &middot; one big heap, mostly cold</div>
    </body></html>`;
  },
};

/* ------------------------------------------------------------------ */
/* x-cutline: the split, and one query over both tiers                 */
/* ------------------------------------------------------------------ */
scenes["x-cutline"] = {
  dur: 17000,
  html() {
    const N = 8, CUT = 6;
    const X0 = 300, GAP = 165, Y = 400, LAKEY = 700;
    let parts = "", anims = "";
    for (let i = 0; i < N; i++) {
      const hot = i >= CUT;
      parts += `<div class="part ${hot ? "hot" : ""}" id="p${i}" style="left:${X0 + i * GAP}px; top:${Y}px">p${i}</div>`;
      if (!hot) {
        anims += css(`#p${i}`, 3.2 + i * 0.35, `sink 1.1s cubic-bezier(.4,0,.2,1)`);
      }
    }
    return `<!DOCTYPE html><html><head><style>${BASE}
      @keyframes sink { to { top: ${LAKEY + 34}px; background: #16211f; border-color: #2e4a44; color: #7fae9d; } }
      ${anims}
      #cut { position: absolute; left: ${X0 + CUT * GAP - 22}px; top: ${Y - 90}px; height: 0;
        border-left: 2.5px dashed #e6e6e6; animation: drop 0.9s ease forwards; animation-delay: 1.6s; }
      @keyframes drop { to { height: 260px; } }
      #cutlabel { position: absolute; left: ${X0 + CUT * GAP - 2}px; top: ${Y - 86}px; font-size: 23px;
        color: #e6e6e6; opacity: 0; animation: fadein 0.6s ease forwards; animation-delay: 2.2s; }
      #pgzone { left: ${X0 - 60}px; top: ${Y - 40}px; width: ${N * GAP + 90}px; height: 160px; }
      #lakezone { left: ${X0 - 60}px; top: ${LAKEY}px; width: ${N * GAP + 90}px; height: 150px;
        opacity: 0; animation: fadein 0.8s ease forwards; animation-delay: 2.8s; }
      #q { position: absolute; left: 50%; transform: translateX(-50%); top: 232px;
        background: #1d1d1d; border: 1px solid #333333; border-radius: 10px; padding: 14px 26px;
        font-size: 26px; opacity: 0; animation: fadein 0.7s ease forwards; animation-delay: 8.5s; }
      svg.wires { position: absolute; left: 0; top: 0; width: 1920px; height: 1080px; pointer-events: none; }
      .wire { stroke: #888888; stroke-width: 2.5; fill: none; stroke-dasharray: 10 8;
        opacity: 0; animation: wire 0.8s ease forwards, flow 1.4s linear infinite; }
      @keyframes wire { to { opacity: 1; } }
      @keyframes flow { to { stroke-dashoffset: -36; } }
      #w1 { animation-delay: 9.5s, 9.5s; }
      #w2 { animation-delay: 9.9s, 9.9s; }
      #one { position: absolute; left: 50%; transform: translateX(-50%); top: 930px; font-size: 27px;
        color: #999999; opacity: 0; animation: fadein 0.8s ease forwards; animation-delay: 11.5s; }
    </style></head><body>
      <div class="title fade">Split at the cut-line</div>
      <div class="sub fade" style="animation-delay:.3s">Recent partitions stay in Postgres. History moves to Iceberg.</div>
      <div class="zone" id="pgzone"></div>
      <div class="zonelabel fade" style="left:${X0 - 40}px; top:${Y - 78}px">Postgres</div>
      <div class="zone" id="lakezone"></div>
      <div class="zonelabel" style="left:${X0 - 40}px; top:${LAKEY - 38}px; opacity:0; animation: fadein .8s ease forwards; animation-delay: 2.8s;">Apache Iceberg</div>
      ${parts}
      <div id="cut"></div>
      <div id="cutlabel">cut-line T</div>
      <div id="q" class="mono">SELECT &middot; INSERT &middot; UPDATE &middot; DELETE</div>
      <svg class="wires">
        <path id="w1" class="wire" d="M 1000 296 C 1100 330, 1250 350, 1350 398" />
        <path id="w2" class="wire" d="M 920 296 C 800 420, 720 560, 700 715" />
      </svg>
      <div id="one">One SQL surface. Every row reachable, wherever it lives.</div>
    </body></html>`;
  },
};

/* ------------------------------------------------------------------ */
/* x-modes: tiered vs mirrored, side by side                           */
/* ------------------------------------------------------------------ */
scenes["x-modes"] = {
  dur: 18000,
  html() {
    const mini = (px, id) => {
      let s = "";
      for (let i = 0; i < 5; i++) {
        s += `<div class="part hot" id="${id}${i}" style="left:${px + i * 150}px; top:380px; width:118px; height:66px; font-size:20px">p${i}</div>`;
      }
      return s;
    };
    let anims = "";
    // Tiered: p0 then p1 detach, sink to the lake, and vacate the heap.
    for (let i = 0; i < 2; i++) {
      anims += css(`#L${i}`, 3.0 + i * 3.2, "tsink 1.6s cubic-bezier(.4,0,.2,1)");
    }
    // Mirrored: a steady stream of row dots, heap untouched.
    for (let i = 0; i < 12; i++) {
      anims += css(`#dot${i}`, 3.0 + i * 0.85, "mflow 1.9s ease-in");
    }
    return `<!DOCTYPE html><html><head><style>${BASE}
      @keyframes tsink { 50% { transform: translateY(130px); opacity: 1; }
        100% { transform: translateY(260px); opacity: 1; background: #16211f; border-color: #2e4a44; color: #7fae9d; } }
      @keyframes mflow { from { transform: translateY(0); opacity: 0; }
        15% { opacity: 1; } 85% { opacity: 1; }
        to { transform: translateY(295px); opacity: 0; } }
      ${anims}
      .half { position: absolute; top: 250px; width: 830px; height: 640px; border-radius: 18px;
        border: 1.5px solid #2c2c2c; background: #181818; }
      .halftitle { position: absolute; top: 282px; width: 830px; text-align: center; font-size: 34px; font-weight: 600; }
      .halfsub { position: absolute; top: 332px; width: 830px; text-align: center; font-size: 23px; color: #999999; }
      .lakebar { position: absolute; top: 740px; width: 700px; height: 78px; border-radius: 12px;
        border: 1.5px solid #2e4a44; background: #16211f; color: #7fae9d;
        display: flex; align-items: center; justify-content: center; font-size: 22px; }
      .dot { position: absolute; width: 14px; height: 14px; border-radius: 50%; background: #bbbbbb;
        top: 452px; opacity: 0; }
      .note { position: absolute; top: 955px; width: 830px; text-align: center; font-size: 24px;
        color: #999999; opacity: 0; animation: fadein .8s ease forwards; }
    </style></head><body>
      <div class="title fade">Two ways to the lake</div>
      <div class="sub fade" style="animation-delay:.3s">Same seam, same reads. What differs is how rows travel.</div>

      <div class="half" style="left:110px"></div>
      <div class="halftitle" style="left:110px">Tiered</div>
      <div class="halfsub" style="left:110px">whole partitions move, the heap sheds</div>
      ${mini(160, "L")}
      <div class="lakebar" style="left:175px">Iceberg &middot; bulk commits, one snapshot per batch</div>
      <div class="note" style="left:110px; animation-delay: 10.5s">no slot, no per-row decode &middot; cheapest at volume</div>

      <div class="half" style="left:980px"></div>
      <div class="halftitle" style="left:980px">Mirrored</div>
      <div class="halfsub" style="left:980px">CDC trails every change, the heap keeps everything</div>
      ${mini(1030, "R")}
      ${Array.from({ length: 12 }, (_, i) =>
        `<div class="dot" id="dot${i}" style="left:${1090 + (i % 5) * 150}px"></div>`).join("")}
      <div class="lakebar" style="left:1045px">Iceberg &middot; fresh within seconds, every version kept</div>
      <div class="note" style="left:980px; animation-delay: 12.5s">replication slot + WAL decode &middot; freshest lake</div>
    </body></html>`;
  },
};

/* ------------------------------------------------------------------ */
/* x-choosing: the decision, question by question                      */
/* ------------------------------------------------------------------ */
scenes["x-choosing"] = {
  dur: 26000,
  html() {
    const node = (id, x, y, w, text, cls = "") =>
      `<div class="dnode ${cls}" id="${id}" style="left:${x}px; top:${y}px; width:${w}px">${text}</div>`;
    const edge = (id, x1, y1, x2, y2, label = "", lx = 0, ly = 0) =>
      `<path id="${id}" class="dedge" d="M ${x1} ${y1} C ${x1} ${(y1 + y2) / 2}, ${x2} ${(y1 + y2) / 2}, ${x2} ${y2}"/>` +
      (label ? `<text id="${id}t" class="dlabel" x="${lx}" y="${ly}">${label}</text>` : "");
    return `<!DOCTYPE html><html><head><style>${BASE}
      .dnode { position: absolute; border: 1.5px solid #3a3a3a; background: #1d1d1d; border-radius: 12px;
        padding: 18px 24px; font-size: 26px; text-align: center; opacity: 0; }
      .dnode.q { border-color: #555555; }
      .dnode.mode { background: #e6e6e6; color: #141414; font-weight: 600; border-color: #e6e6e6; }
      svg.tree { position: absolute; left: 0; top: 0; width: 1920px; height: 1080px; pointer-events: none; }
      .dedge { stroke: #444444; stroke-width: 2.5; fill: none; opacity: 0; }
      .dlabel { fill: #999999; font-size: 23px; opacity: 0; }
      .on { animation: fadein 0.7s ease forwards; }
      ${[
        ["#q1", 1.2], ["#e1", 2.6], ["#e1t", 2.6], ["#m1", 3.2],
        ["#e2", 5.2], ["#e2t", 5.2], ["#q2", 5.6],
        ["#e3", 8.2], ["#e3t", 8.2], ["#m2", 8.8],
        ["#e4", 12.4], ["#e4t", 12.4], ["#q3", 12.8],
        ["#e5", 16.2], ["#e5t", 16.2], ["#m3", 16.8],
        ["#e6", 19.4], ["#e6t", 19.4], ["#m4", 20.0],
      ].map(([sel, t]) => `${sel} { animation: fadein 0.7s ease forwards; animation-delay: ${t}s; }`).join("\n")}
    </style></head><body>
      <div class="title fade">The mode is a choice</div>
      <div class="sub fade" style="animation-delay:.3s">Time series does not automatically mean tiered. Three questions decide.</div>

      ${node("q1", 760, 270, 400, "Does the data age along a tier key?", "q")}
      ${node("m1", 330, 460, 300, "Fully mirrored", "mode")}
      ${node("q2", 810, 460, 300, "Must Postgres keep the whole copy?", "q")}
      ${node("m2", 400, 660, 340, "Tiered + keep-heap<br><span style='font-size:21px;font-weight:400'>batches out, nothing deleted</span>", "mode")}
      ${node("q3", 890, 660, 300, "Must the lake trail in seconds?", "q")}
      ${node("m3", 1300, 860, 330, "Mirrored + heap retention", "mode")}
      ${node("m4", 620, 860, 240, "Tiered", "mode")}

      <svg class="tree">
        ${edge("e1", 830, 330, 500, 460, "no, entity data", 560, 420)}
        ${edge("e2", 1030, 330, 990, 460, "yes, it ages", 1050, 420)}
        ${edge("e3", 880, 530, 590, 660, "yes, keep it all", 560, 625)}
        ${edge("e4", 1030, 530, 1050, 660, "no, shed old heap", 1090, 625)}
        ${edge("e5", 1110, 730, 1450, 860, "yes, CDC", 1330, 830)}
        ${edge("e6", 980, 730, 740, 860, "the tiering lag is fine", 690, 830)}
      </svg>
    </body></html>`;
  },
};

/* ------------------------------------------------------------------ */
/* x-corrections: plain SQL everywhere, the delta, the fold            */
/* ------------------------------------------------------------------ */
scenes["x-corrections"] = {
  dur: 16500,
  html() {
    return `<!DOCTYPE html><html><head><style>${BASE}
      #sql { position: absolute; left: 50%; transform: translateX(-50%); top: 280px;
        background: #1d1d1d; border: 1px solid #333333; border-radius: 10px; padding: 16px 28px;
        font-size: 27px; white-space: nowrap; overflow: hidden; width: 0;
        animation: type 2.2s steps(46) forwards; animation-delay: 1.0s; }
      @keyframes type { from { width: 0; padding-right: 0; } to { width: 840px; } }
      .box { position: absolute; border-radius: 16px; border: 1.5px solid #2c2c2c; background: #181818; }
      #delta { left: 510px; top: 470px; width: 900px; height: 130px; border-style: dashed; }
      #lake { left: 460px; top: 720px; width: 1000px; height: 150px;
        border-color: #2e4a44; background: #16211f; }
      #entry { position: absolute; left: 620px; top: 505px; z-index: 2; background: #1d1d1d;
        border: 1.5px solid #555555; border-radius: 10px; padding: 12px 22px; font-size: 24px;
        opacity: 0; animation: fadein .6s ease forwards, fold 1.4s cubic-bezier(.4,0,.2,1) forwards;
        animation-delay: 3.8s, 9.5s; }
      @keyframes fold { from { opacity: 1; } to { opacity: 1; transform: translateY(255px);
        border-color: #2e4a44; background: #16211f; color: #7fae9d; } }
      #imm { position: absolute; left: 1010px; top: 517px; font-size: 22px; color: #999999;
        opacity: 0; animation: fadein .6s ease forwards; animation-delay: 4.6s; }
      #foldnote { position: absolute; left: 50%; transform: translateX(-50%); top: 940px;
        font-size: 26px; color: #999999; opacity: 0; animation: fadein .8s ease forwards;
        animation-delay: 11.5s; }
      #lakeflash { position: absolute; left: 460px; top: 720px; width: 1000px; height: 150px;
        border-radius: 16px; border: 2px solid #7fae9d; opacity: 0;
        animation: flash 1.6s ease; animation-delay: 10.6s; }
      @keyframes flash { 30% { opacity: .9; } 100% { opacity: 0; } }
      .boxlabel { position: absolute; font-size: 23px; color: #999999; }
    </style></head><body>
      <div class="title fade">Corrections stay plain SQL</div>
      <div class="sub fade" style="animation-delay:.3s">Even when the row already lives in the lake.</div>
      <div id="sql" class="mono">UPDATE events SET amount = 42 WHERE id = 1187;</div>
      <div class="box" id="delta"></div>
      <div class="boxlabel fade" style="left:535px; top:440px; animation-delay:3.4s">modak.delta &middot; the correction buffer</div>
      <div id="entry" class="mono">id 1187 &middot; upsert &middot; v2</div>
      <div id="imm">visible to every query, immediately</div>
      <div class="box" id="lake"></div>
      <div id="lakeflash"></div>
      <div class="boxlabel" style="left:485px; top:690px; color:#7fae9d">Apache Iceberg &middot; the cold base</div>
      <div id="foldnote">The worker folds the delta into Iceberg as one atomic snapshot. Newest wins.</div>
    </body></html>`;
  },
};

/* ------------------------------------------------------------------ */
/* x-surfaces: the extension is optional, the seam contract is public  */
/* ------------------------------------------------------------------ */
scenes["x-surfaces"] = {
  dur: 25500,
  html() {
    const item = (id, text, dim = false) =>
      `<div class="item${dim ? " dim" : ""}" id="${id}">${text}</div>`;
    return `<!DOCTYPE html><html><head><style>${BASE}
      .card { position: absolute; top: 250px; width: 780px; height: 540px; border-radius: 18px;
        border: 1.5px solid #2c2c2c; background: #181818; opacity: 0; }
      .cardtitle { position: absolute; top: 282px; width: 780px; text-align: center;
        font-size: 32px; font-weight: 600; opacity: 0; }
      .cardsub { position: absolute; top: 330px; width: 780px; text-align: center;
        font-size: 22px; color: #999999; opacity: 0; }
      .item { position: absolute; width: 630px; border: 1px solid #3a3a3a; background: #1d1d1d;
        border-radius: 10px; padding: 12px 22px; font-size: 24px; opacity: 0; }
      .item b { font-weight: 600; }
      .item span { color: #999999; font-size: 21px; }
      .item.dim { border-style: dashed; background: transparent; }
      .item.dim b { color: #999999; }
      #seam { position: absolute; left: 460px; top: 830px; width: 1000px; height: 62px;
        border-radius: 12px; border: 1.5px solid #2e4a44; background: #16211f; color: #7fae9d;
        display: flex; align-items: center; justify-content: center; font-size: 23px; opacity: 0; }
      #mirrored { position: absolute; left: 0; top: 928px; width: 100%; text-align: center;
        font-size: 24px; color: #999999; opacity: 0; }
      ${[
        ["#left", 1.0], ["#lt", 1.2], ["#ls", 1.2], ["#l1", 1.8], ["#l2", 2.4], ["#l3", 3.0],
        ["#seam", 4.8],
        ["#right", 6.2], ["#rt", 6.4], ["#rs", 6.4],
        ["#r1", 7.0], ["#r2", 7.8], ["#r3", 8.6], ["#r4", 9.4], ["#r5", 12.5],
        ["#mirrored", 15.0],
      ].map(([sel, t]) => `${sel} { animation: fadein 0.7s ease forwards; animation-delay: ${t}s; }`).join("\n")}
    </style></head><body>
      <div class="title fade">The extension is optional</div>
      <div class="sub fade" style="animation-delay:.3s">Best with it, complete without it. Some workloads deserve a better tool than SQL anyway.</div>

      <div class="card" id="left" style="left:150px"></div>
      <div class="cardtitle" id="lt" style="left:150px">As a Postgres extension</div>
      <div class="cardsub" id="ls" style="left:150px">the best experience: everything is plain SQL</div>
      ${item("l1", "<b>Transparent reads</b> <span>&middot; one SELECT over both tiers</span>")}
      ${item("l2", "<b>Transparent DML</b> <span>&middot; UPDATE and DELETE reach cold rows</span>")}
      ${item("l3", "<b>modak_explain</b> <span>&middot; shows the routing for any statement</span>")}

      <div class="card" id="right" style="left:990px"></div>
      <div class="cardtitle" id="rt" style="left:990px">Purpose-built surfaces</div>
      <div class="cardsub" id="rs" style="left:990px">with or without the extension, per workload</div>
      ${item("r1", "<b>Seam client library</b> <span>&middot; JDBC building block for your tooling</span>")}
      ${item("r2", "<b>Spark connector</b> <span>&middot; SQL and DataFrames over both tiers</span>")}
      ${item("r3", "<b>Stream Load</b> <span>&middot; streaming ingest, exactly once</span>")}
      ${item("r4", "<b>Bulk ingest</b> <span>&middot; backfills, files straight into both tiers</span>")}
      ${item("r5", "<b>Flink, Trino</b> <span>&middot; and more coming</span>", true)}

      <div id="seam">the seam contract &middot; a public protocol, same tables, same guarantees</div>
      <div id="mirrored">A mirrored table's heap is complete: plain SQL works with no extension at all.</div>
      <style>
        #l1 { left: 230px; top: 430px; } #l2 { left: 230px; top: 545px; } #l3 { left: 230px; top: 660px; }
        #r1 { left: 1065px; top: 388px; } #r2 { left: 1065px; top: 468px; }
        #r3 { left: 1065px; top: 548px; } #r4 { left: 1065px; top: 628px; }
        #r5 { left: 1065px; top: 708px; }
      </style>
    </body></html>`;
  },
};

const pick = process.argv[2];
if (!pick || (pick !== "all" && !scenes[pick])) {
  console.error("usage: node explainer.mjs <all|" + Object.keys(scenes).join("|") + ">");
  process.exit(1);
}

for (const [name, spec] of Object.entries(scenes)) {
  if (pick !== "all" && pick !== name) continue;
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    recordVideo: { dir: OUT, size: { width: 1920, height: 1080 } },
  });
  const page = await context.newPage();
  await page.setContent(spec.html());
  await page.waitForTimeout(spec.dur);
  const video = page.video();
  await context.close();
  fs.renameSync(await video.path(), `${OUT}/${name}.webm`);
  await browser.close();
  console.log(`saved ${OUT}/${name}.webm (${(spec.dur / 1000).toFixed(1)}s)`);
}
