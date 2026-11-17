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

const mime = { '.html': 'text/html; charset=utf-8', '.woff2': 'font/woff2' };
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
  let f = url.pathname === '/' ? 'index.html' : url.pathname.replace(/^\/f\//, 'f/');
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
