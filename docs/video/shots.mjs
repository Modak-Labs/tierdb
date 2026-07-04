// Refreshes the console screenshots used by the docs (docs/assets).
import { chromium } from "playwright";
import path from "path";

const DOCS = path.join(import.meta.dirname, "..", "assets");
const browser = await chromium.launch();
const page = await browser.newPage({
  viewport: { width: 1600, height: 900 },
  deviceScaleFactor: 2,
  colorScheme: "dark",
});

await page.goto("http://localhost:9090/");
await page.waitForSelector("tbody#fleet tr", { timeout: 30000 });
await page.waitForTimeout(2500);
await page.screenshot({ path: `${DOCS}/console-overview.png` });

await page.goto("http://localhost:9090/#/sql");
await page.waitForSelector(".CodeMirror", { timeout: 15000 });
await page.evaluate(() => {
  document.querySelector(".CodeMirror").CodeMirror.setValue("SELECT * FROM public.events ORDER BY id;");
});
await page.click("#run-btn");
await page.waitForSelector("#sql-results table", { timeout: 30000 });
await page.click("#explain-btn");
await page.waitForSelector("#sql-explain-panel:not([hidden])", { timeout: 30000 });
await page.waitForTimeout(800);
await page.screenshot({ path: `${DOCS}/console-playground.png` });

await browser.close();
console.log("saved console-overview.png and console-playground.png");
