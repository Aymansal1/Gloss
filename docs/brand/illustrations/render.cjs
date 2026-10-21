// Renders each illustration page to docs/brand/img/<name>.png.
// Run from the repo root: NODE_PATH=$(npm root -g) node docs/brand/illustrations/render.cjs
const { chromium } = require("playwright");
const path = require("path");
const dir = __dirname;
const out = path.join(dir, "..", "img");
(async () => {
  require("fs").mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  for (const name of process.argv.slice(2).length ? process.argv.slice(2) : ["tour", "reading", "search"]) {
    const pg = await b.newPage({ viewport: { width: 1800, height: 1200 }, deviceScaleFactor: 1 });
    await pg.goto("file://" + path.join(dir, name + ".html"));
    await pg.evaluate(() => document.fonts.ready);
    await (await pg.$("#stage")).screenshot({ path: path.join(out, name + ".png") });
    console.log("wrote", name);
  }
  await b.close();
})();
