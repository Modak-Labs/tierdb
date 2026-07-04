// Renders the full-frame title cards as build/cards/<name>.png.
import { chromium } from "playwright";
import fs from "fs";
import path from "path";

const OUT = path.join(import.meta.dirname, "build", "cards");
fs.mkdirSync(OUT, { recursive: true });

const LOGO = `<svg width="72" height="72" viewBox="0 0 64 64" fill="none" stroke="#e6e6e6" stroke-linecap="round" stroke-linejoin="round">
  <path d="M32 7 C43 17 55 30 55 42 A23 13 0 0 1 9 42 C9 30 21 17 32 7 Z" stroke-width="3"/>
  <path d="M32 9 C30 22 26 36 18 50" stroke-width="2.2"/>
  <path d="M32 9 C31 22 29 37 25.5 51.5" stroke-width="2.2"/>
  <path d="M32 9 L32 52.5" stroke-width="2.2"/>
  <path d="M32 9 C33 22 35 37 38.5 51.5" stroke-width="2.2"/>
  <path d="M32 9 C34 22 38 36 46 50" stroke-width="2.2"/>
</svg>`;

function html({ title, sub, brand = false, small = "" }) {
  return `<!DOCTYPE html><html><head><style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      width: 1920px; height: 1080px; background: #141414;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", sans-serif;
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      color: #e6e6e6; gap: 28px;
    }
    .brandrow { display: flex; align-items: center; gap: 22px; }
    .word { font-size: 84px; font-weight: 600; letter-spacing: 0.01em; }
    h1 { font-size: ${brand ? 84 : 58}px; font-weight: 600; letter-spacing: 0.01em; text-align: center; max-width: 1500px; }
    p  { font-size: 30px; font-weight: 400; color: #999999; text-align: center; max-width: 1300px; line-height: 1.5; }
    .small { font-size: 22px; color: #666666; margin-top: 18px;
      font-family: "SF Mono", ui-monospace, Menlo, monospace; }
  </style></head><body>
    ${brand ? `<div class="brandrow">${LOGO}<span class="word">modak</span></div>` : `<h1>${title}</h1>`}
    ${brand && title ? `<h1 style="font-size:40px;font-weight:400;color:#e6e6e6">${title}</h1>` : ""}
    ${sub ? `<p>${sub}</p>` : ""}
    ${small ? `<div class="small">${small}</div>` : ""}
  </body></html>`;
}

const cards = {
  "card-x-intro": { brand: true, title: "", sub: "Tier-aware data federation between Postgres and Apache Iceberg." },
  "card-x-outro": { brand: true, title: "", sub: "Tier-aware data federation between Postgres and Apache Iceberg.", small: "watch the demo next &middot; github.com/addu390/modak" },
  "card-intro": { brand: true, title: "The demo", sub: "An ordinary Postgres table, a worker moving data between tiers, and plain SQL over all of it. Live, no cuts." },
  "card-table": { title: "Start with an ordinary Postgres table", sub: "public.events has 5 rows across 3 range partitions, freshly registered with Modak. The worker takes it from here." },
  "card-dml": { title: "Plain SQL. Any row. Either tier.", sub: "SELECT, INSERT, UPDATE, DELETE work across the whole timeline. Explain shows exactly where rows come from and go to." },
  "card-fold": { title: "Moments later", sub: "The worker folds the correction into Iceberg. The delta drains to zero, and the corrected row now lives in the lake." },
  "card-maint": { title: "Maintenance is optional", sub: "Compaction, snapshot expiry, and orphan cleanup run on a schedule with sane defaults. Or trigger a pass yourself: one click in the console, or one CLI command." },
  "card-outro": { brand: true, title: "", sub: "Tier-aware data federation between Postgres and Apache Iceberg.", small: "github.com/addu390/modak &middot; beta" },
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
for (const [name, spec] of Object.entries(cards)) {
  await page.setContent(html(spec));
  await page.waitForTimeout(150);
  await page.screenshot({ path: `${OUT}/${name}.png` });
  console.log(`${OUT}/${name}.png`);
}
await browser.close();
