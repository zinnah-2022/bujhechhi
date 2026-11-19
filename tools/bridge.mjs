// পরীক্ষার সার্ভার: আসল Kotlin ClassCore (JVM) + Node WebSocket + স্টুডেন্ট পেজ।
// চালানো: node tools/bridge.mjs <path-to-bridge.jar> [port]
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
const require = createRequire(process.env.NODE_PATH ? process.env.NODE_PATH + '/' : import.meta.url);
const { WebSocketServer } = require('ws');

const jar = process.argv[2];
const port = Number(process.argv[3] || 8080);
const assets = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../app/src/main/assets/student');
const java = spawn('java', ['-Dstdout.encoding=UTF-8', '-Dfile.encoding=UTF-8', '-cp', jar, 'com.edurip.bujhechhi.core.BridgeKt'], { stdio: ['pipe', 'pipe', 'inherit'] });
let replyWaiters = [];
const conns = new Map();
let seq = 0;
let frameSeq = 0;

let buf = '';
java.stdout.setEncoding('utf8');
java.stdout.on('data', (d) => {
  buf += d;
  let i;
  while ((i = buf.indexOf('\n')) >= 0) {
    const line = buf.slice(0, i); buf = buf.slice(i + 1);
    const kind = line[0];
    if (kind === 'S') { const j = line.indexOf(' ', 2); const id = line.slice(2, j); conns.get(id)?.send(line.slice(j + 1)); }
    else if (kind === 'Z') { conns.get(line.slice(2))?.close(); }
    else if (kind === 'R') { replyWaiters.shift()?.(line.slice(2)); }
  }
});

const mime = { '.html': 'text/html; charset=utf-8', '.woff2': 'font/woff2', '.css': 'text/css', '.js': 'application/javascript' };
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/drop') { for (const w of conns.values()) w.terminate(); res.writeHead(200); return res.end('dropped'); }
  if (url.pathname === '/ctl' && req.method === 'POST') {
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', () => {
      replyWaiters.push((r) => { res.writeHead(200, { 'content-type': 'application/json' }); res.end(r); });
      java.stdin.write('T ' + body + '\n');
    });
    return;
  }
  if (url.pathname === '/castframe') { frameSeq++; res.writeHead(200); return res.end(String(frameSeq)); }
  if (url.pathname === '/scr') {
    // আসল KtorHost-এর /scr রাউটের নকল: কোড যাচাই (আসল ClassCore), তারপর long-poll
    const c = url.searchParams.get('c') || '';
    const after = Number(url.searchParams.get('after') || 0);
    const allowed = () => new Promise((ok) => { replyWaiters.push((r) => ok(JSON.parse(r).ok)); java.stdin.write('T castok ' + c + '\n'); });
    (async () => {
      if (!(await allowed())) { res.writeHead(409); return res.end('off'); }
      const end = Date.now() + 3000;
      while (frameSeq <= after && Date.now() < end && (await allowed())) await new Promise((r) => setTimeout(r, 40));
      if (frameSeq <= after) { res.writeHead(204); return res.end(); }
      const hue = (frameSeq * 70) % 360;
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="540" height="960" viewBox="0 0 540 960"><rect width="540" height="960" fill="hsl(${hue},70%,85%)"/><text x="40" y="200" font-size="60" font-family="sans-serif">স্ক্রিন #${frameSeq}</text></svg>`;
      res.writeHead(200, { 'content-type': 'image/svg+xml', 'x-seq': String(frameSeq), 'cache-control': 'no-store' });
      res.end(svg);
    })();
    return;
  }
  const im = url.pathname.match(/^\/img\/(\d+)\/(\d+)$/);
  if (im) {
    replyWaiters.push((r) => {
      if (!JSON.parse(r).ok) { res.writeHead(404); return res.end('nf'); }
      const pg = Number(im[2]) + 1;
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="1100" viewBox="0 0 800 1100"><rect width="800" height="1100" fill="#fffef5"/><text x="60" y="120" font-size="54" font-family="sans-serif">প্রশ্ন — পাতা ${pg}</text><text x="60" y="230" font-size="40" font-family="sans-serif">১) ৩/৪ + ১/৪ = ?</text><text x="60" y="330" font-size="40" font-family="sans-serif">২) ২/৫ এর হর কত?</text><rect x="400" y="900" width="300" height="120" fill="#fcd34d"/></svg>`;
      const h = { 'content-type': 'image/svg+xml', 'cache-control': 'no-store' };
      if (url.searchParams.get('dl') === '1') h['content-disposition'] = 'attachment; filename="q.svg"';
      res.writeHead(200, h); res.end(svg);
    });
    java.stdin.write(`T imgok ${im[1]} ${im[2]} ${url.searchParams.get('c') || ''} ${url.searchParams.get('dl') === '1' ? 1 : 0}\n`);
    return;
  }
  let f = url.pathname === '/' ? 'index.html' : url.pathname.replace(/^\/(f|k)\//, '$1/');
  const full = path.join(assets, f);
  if (!full.startsWith(assets) || !fs.existsSync(full) || fs.statSync(full).isDirectory()) { res.writeHead(404); return res.end('nf'); }
  res.writeHead(200, { 'content-type': mime[path.extname(full)] || 'application/octet-stream', 'cache-control': 'no-store' });
  fs.createReadStream(full).pipe(res);
});
const wss = new WebSocketServer({ server, path: '/ws' });
wss.on('connection', (ws) => {
  const id = 'c' + ++seq;
  conns.set(id, ws);
  ws.on('message', (m) => java.stdin.write('M ' + id + ' ' + m.toString().replace(/\n/g, ' ') + '\n'));
  ws.on('close', () => { conns.delete(id); java.stdin.write('X ' + id + '\n'); });
});
server.listen(port, '127.0.0.1', () => console.log('listening ' + port));
process.on('SIGTERM', () => { java.kill(); process.exit(0); });
