// Records one console scene as build/raw/<scene>.webm. Run once per scene.
import { chromium } from "playwright";
import fs from "fs";
import path from "path";

const CONSOLE_URL = "http://localhost:9090/";
const OUT = path.join(import.meta.dirname, "build", "raw");
fs.mkdirSync(OUT, { recursive: true });

const scene = process.argv[2];
if (!scene) { console.error("usage: node record.mjs <scene>"); process.exit(1); }

// A visible fake cursor, since headless recordings have none.
const cursorScript = () => {
  const mk = () => {
    if (document.getElementById("__cursor")) return;
    const d = document.createElement("div");
    d.id = "__cursor";
    Object.assign(d.style, {
      position: "fixed", width: "20px", height: "20px", borderRadius: "50%",
      background: "rgba(240,240,240,0.85)", border: "2px solid rgba(20,20,20,0.9)",
      boxShadow: "0 0 6px rgba(0,0,0,0.5)", zIndex: 999999, pointerEvents: "none",
      transform: "translate(-50%,-50%)", left: "-60px", top: "-60px",
      transition: "width .12s, height .12s",
    });
    document.body.appendChild(d);
    window.addEventListener("mousemove", e => { d.style.left = e.clientX + "px"; d.style.top = e.clientY + "px"; });
    window.addEventListener("mousedown", () => { d.style.width = "14px"; d.style.height = "14px"; });
    window.addEventListener("mouseup", () => { d.style.width = "20px"; d.style.height = "20px"; });
  };
  if (document.readyState !== "loading") mk();
  else document.addEventListener("DOMContentLoaded", mk);
};

async function newScene(name) {
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    recordVideo: { dir: OUT, size: { width: 1920, height: 1080 } },
    colorScheme: "dark",
  });
  const page = await context.newPage();
  await page.addInitScript(cursorScript);
  const done = async () => {
    const video = page.video();
    await context.close();
    const p = await video.path();
    fs.renameSync(p, `${OUT}/${name}.webm`);
    await browser.close();
    console.log(`saved ${OUT}/${name}.webm`);
  };
  return { page, done };
}

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function moveTo(page, selector) {
  const box = await page.locator(selector).first().boundingBox();
  if (!box) throw new Error("no box for " + selector);
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 30 });
  await sleep(250);
}

async function clickAt(page, selector) {
  await moveTo(page, selector);
  await page.locator(selector).first().click();
}

async function zoomTo(page, selector, scale = 1.5, holdMs = 2600) {
  await page.evaluate(([sel, s]) => {
    const el = document.querySelector(sel);
    const r = el.getBoundingClientRect();
    const cx = r.x + r.width / 2, cy = r.y + r.height / 2;
    document.body.style.transformOrigin = `${cx}px ${cy}px`;
    document.body.style.transition = "transform 1100ms cubic-bezier(.4,0,.2,1)";
    document.body.style.transform = `scale(${s})`;
  }, [selector, scale]);
  await sleep(1100 + holdMs);
  await page.evaluate(() => { document.body.style.transform = "scale(1)"; });
  await sleep(1200);
}

async function setSql(page, sql) {
  await page.evaluate(q => {
    document.querySelector(".CodeMirror").CodeMirror.setValue(q);
  }, sql);
}

async function typeSql(page, sql, delay = 26) {
  await clickAt(page, ".CodeMirror");
  await page.evaluate(() => {
    const cm = document.querySelector(".CodeMirror").CodeMirror;
    cm.setValue(""); cm.focus();
  });
  await page.keyboard.type(sql, { delay });
  await sleep(400);
}

async function runSql(page) {
  await clickAt(page, "#run-btn");
  await page.waitForFunction(() => {
    const s = document.getElementById("sql-status").textContent;
    return s && !s.includes("running");
  }, { timeout: 30000 });
  await sleep(600);
}

async function explainSql(page) {
  await clickAt(page, "#explain-btn");
  await page.waitForSelector("#sql-explain-panel:not([hidden])", { timeout: 30000 });
  await sleep(600);
}

async function gotoPlayground(page) {
  await page.goto(CONSOLE_URL + "#/sql");
  await page.waitForSelector(".CodeMirror", { timeout: 15000 });
  await sleep(1500);
}

const scenes = {
  // Live time-lapse: fresh table, worker tiers partitions on screen.
  async tiering() {
    const { page, done } = await newScene("tiering");
    await page.goto(CONSOLE_URL);
    await page.waitForSelector("tbody#fleet tr", { timeout: 30000 });
    await sleep(1500);
    await clickAt(page, 'tbody#fleet tr:has-text("events")');
    await page.waitForSelector("#detail-partitions tr", { timeout: 15000 });
    await sleep(2000);
    await page.waitForFunction(() => {
      const t = document.getElementById("detail-partitions").textContent;
      return t.includes("tiered") || t.includes("dropped");
    }, { timeout: 120000 });
    await sleep(4000);
    await zoomTo(page, "#detail-partitions", 1.5, 3000);
    await sleep(1500);
    await done();
  },

  async overview() {
    const { page, done } = await newScene("overview");
    await page.goto(CONSOLE_URL);
    await page.waitForSelector("tbody#fleet tr", { timeout: 30000 });
    await sleep(3500);
    await moveTo(page, "tbody#fleet tr");
    await zoomTo(page, "tbody#fleet tr", 1.45, 3200);
    await sleep(1200);
    await done();
  },

  async select() {
    const { page, done } = await newScene("select");
    await gotoPlayground(page);
    await typeSql(page, "SELECT * FROM public.events ORDER BY id;");
    await runSql(page);
    await zoomTo(page, ".sql-results-panel", 1.45, 3200);
    await sleep(800);
    await done();
  },

  async explain() {
    const { page, done } = await newScene("explain");
    await gotoPlayground(page);
    await setSql(page, "SELECT * FROM public.events ORDER BY id;");
    await sleep(800);
    await explainSql(page);
    await zoomTo(page, "#sql-explain-panel", 1.5, 3800);
    await sleep(800);
    await done();
  },

  async update() {
    const { page, done } = await newScene("update");
    await gotoPlayground(page);
    await typeSql(page, "UPDATE public.events SET val = 'c (corrected)' WHERE id = 3;");
    await explainSql(page);
    await zoomTo(page, "#sql-explain-panel", 1.5, 3600);
    await runSql(page);
    // Catch the delta row before the next worker sweep folds it.
    await setSql(page, "SELECT pk, op, tier_key, payload FROM modak.delta;");
    await runSql(page);
    await zoomTo(page, ".sql-results-panel", 1.45, 3000);
    await sleep(600);
    await done();
  },

  async folded() {
    const { page, done } = await newScene("folded");
    await gotoPlayground(page);
    await typeSql(page, "SELECT count(*) AS delta_rows FROM modak.delta;", 20);
    await runSql(page);
    await sleep(1200);
    await typeSql(page, "SELECT * FROM public.events WHERE id = 3;", 20);
    await runSql(page);
    await zoomTo(page, ".sql-results-panel", 1.45, 3000);
    await sleep(600);
    await done();
  },

  async insert() {
    const { page, done } = await newScene("insert");
    await gotoPlayground(page);
    await typeSql(page, "INSERT INTO public.events VALUES\n  (7, 60,  'g'),   -- behind the cut-line: cold\n  (8, 260, 'h');   -- ahead of it: hot", 18);
    await explainSql(page);
    await zoomTo(page, "#sql-explain-panel", 1.5, 3600);
    await runSql(page);
    await sleep(600);
    await setSql(page, "SELECT * FROM public.events ORDER BY id;");
    await runSql(page);
    await zoomTo(page, ".sql-results-panel", 1.4, 3200);
    await sleep(600);
    await done();
  },

  async maintenance() {
    const { page, done } = await newScene("maintenance");
    await page.goto(CONSOLE_URL);
    await page.waitForSelector("tbody#fleet tr", { timeout: 30000 });
    await sleep(1200);
    await clickAt(page, 'tbody#fleet tr:has-text("events")');
    await page.waitForSelector("#lake-panel:not([hidden])", { timeout: 30000 });
    await page.locator("#lake-panel").scrollIntoViewIfNeeded();
    await sleep(1500);
    await zoomTo(page, "#lake-stats", 1.4, 2600);
    await clickAt(page, "#maintain-btn");
    await page.waitForSelector("#maintain-status:not([hidden])", { timeout: 15000 });
    await sleep(1200);
    await page.waitForFunction(() => {
      const t = document.getElementById("lake-maintenance").textContent;
      return t && t.trim().length > 0;
    }, { timeout: 120000 });
    await sleep(1500);
    await zoomTo(page, "#lake-maintenance", 1.4, 3000);
    await sleep(800);
    await done();
  },

  async outro() {
    const { page, done } = await newScene("outro");
    await page.goto(CONSOLE_URL);
    await page.waitForSelector("tbody#fleet tr", { timeout: 30000 });
    await sleep(2500);
    await zoomTo(page, "#chart-commits", 1.4, 2800);
    await sleep(1500);
    await done();
  },
};

if (!scenes[scene]) { console.error("unknown scene " + scene); process.exit(1); }
await scenes[scene]();
