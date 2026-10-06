// Usage: node render.mjs <trace.bin> <outDir> [frame,frame,...|all]
// Replays a GL trace in headless Chromium (SwiftShader WebGL2) and saves one PNG per captured frame.
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const globalRoots = ['/opt/node-tools/node_modules', process.env.NODE_PATH || ''];
let chromium;
for (const root of [''].concat(globalRoots)) {
  try { ({ chromium } = require(root ? path.join(root, 'playwright') : 'playwright')); break; } catch (e) { /* try next */ }
}
if (!chromium) { console.error('playwright not found (npm i -g playwright)'); process.exit(2); }

const [tracePath, outDir, framesArg = 'all'] = process.argv.slice(2);
if (!tracePath || !outDir) { console.error('usage: node render.mjs <trace.bin> <outDir> [frames|all]'); process.exit(2); }
fs.mkdirSync(outDir, { recursive: true });

const here = path.dirname(fileURLToPath(import.meta.url));
const traceB64 = fs.readFileSync(tracePath).toString('base64');

const browser = await chromium.launch({
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--disable-gpu-sandbox'],
});
const page = await browser.newPage();
page.on('console', (m) => console.log('[page]', m.text()));
page.on('pageerror', (e) => console.log('[pageerror]', e.message));
await page.goto(pathToFileURL(path.join(here, 'replay.html')).href);

const frames = framesArg === 'all' ? Array.from({ length: 100000 }, (_, i) => i) : framesArg.split(',').map(Number);
const result = await page.evaluate(([b64, f]) => window.replayTrace(b64, f), [traceB64, frames]);
for (const s of result.shots) {
  const file = path.join(outDir, `frame_${String(s.frame).padStart(4, '0')}.png`);
  fs.writeFileSync(file, Buffer.from(s.png.split(',')[1], 'base64'));
}
console.log(`replayed ${result.frames} frames (${result.width}x${result.height}), captured ${result.shots.length}, ${result.draws} draw calls`);
if (result.errors.length) { console.log('ERRORS:\n' + [...new Set(result.errors)].join('\n')); process.exitCode = 1; }
await browser.close();
