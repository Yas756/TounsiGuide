// Serves the Angular e2e build (e2e/.stack/web/browser) with the single-page fallback to index.html.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { STACK_DIR, PORTS } from './config.mjs';

const root = path.join(STACK_DIR, 'web', 'browser');
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.map': 'application/json',
  '.woff2': 'font/woff2', '.woff': 'font/woff', '.txt': 'text/plain' };
if (!fs.existsSync(path.join(root, 'index.html'))) {
  console.error(`No build in ${root}. Run "npm run build-stack" first.`);
  process.exit(1);
}
http.createServer((req, res) => {
  const url = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  let file = path.normalize(path.join(root, url));
  if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(root, 'index.html');
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] ?? 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
}).listen(PORTS.web, () => console.log(`E2E web app on http://localhost:${PORTS.web}`));
