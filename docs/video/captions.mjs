// Renders the per-scene caption pills as transparent build/caps/<name>.png.
import { chromium } from "playwright";
import fs from "fs";
import path from "path";

const OUT = path.join(import.meta.dirname, "build", "caps");
fs.mkdirSync(OUT, { recursive: true });

const captions = {
  overview: "The Modak console. Every registered table, its cut-line, its snapshot, and the worker, live.",
  tiering: "Time-lapse. The worker tiers p0 and p1 into Iceberg, then drops them from Postgres.",
  select: "One plain SELECT returns every row. Recent rows from the heap, history from Iceberg.",
  explain: "Explain shows the routing. The heap for recent rows, a pinned Iceberg snapshot for history.",
  update: "A plain UPDATE of a cold row. Rewritten into two halves, the correction lands in modak.delta.",
  folded: "The delta is empty. The corrected row is now served straight from Iceberg.",
  insert: "One INSERT, two destinations. The recent row hits the heap, the historical row the delta.",
  maintenance: "Lake health per table. One click files a maintenance request, the worker journals the pass.",
  outro: "Tiering, folding, maintenance, partition premake. All background, all visible.",
};

const html = text => `<!DOCTYPE html><html><head><style>
  * { margin: 0; padding: 0; }
  body { width: 1920px; height: 1080px; background: transparent;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
  .pill { position: absolute; left: 56px; bottom: 52px;
    background: rgba(20,20,20,0.92); border: 1px solid rgba(255,255,255,0.14);
    border-radius: 10px; padding: 16px 24px; color: #e6e6e6;
    font-size: 28px; font-weight: 400; max-width: 1400px; line-height: 1.4; }
</style></head><body><div class="pill">${text}</div></body></html>`;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
for (const [name, text] of Object.entries(captions)) {
  await page.setContent(html(text));
  await page.waitForTimeout(80);
  await page.screenshot({ path: `${OUT}/${name}.png`, omitBackground: true });
  console.log(name);
}
await browser.close();
