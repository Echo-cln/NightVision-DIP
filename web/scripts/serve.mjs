import {createServer} from 'node:http';
import {readFile, stat} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const base = fileURLToPath(new URL('..', import.meta.url));
const root = path.resolve(base, process.argv.includes('--dist') ? 'dist' : '.');
const port = Number(process.env.PORT || 4173);
const host = process.env.HOST || '127.0.0.1';
const types = {'.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.jpg': 'image/jpeg', '.json': 'application/json'};

const server = createServer(async (request, response) => {
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('Referrer-Policy', 'same-origin');
  response.setHeader('Permissions-Policy', 'camera=(self), microphone=()');
  response.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; worker-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
  if (!['GET', 'HEAD'].includes(request.method)) {
    response.writeHead(405, {'Allow': 'GET, HEAD'}); response.end(); return;
  }
  try {
    const url = new URL(request.url, 'http://localhost');
    let relative = decodeURIComponent(url.pathname).replace(/^\/+/, '') || 'index.html';
    let file = path.resolve(root, relative);
    if (file !== root && !file.startsWith(root + path.sep)) throw new Error('Forbidden');
    if ((await stat(file)).isDirectory()) file = path.join(file, 'index.html');
    const bytes = await readFile(file);
    response.writeHead(200, {'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache'});
    response.end(request.method === 'HEAD' ? undefined : bytes);
  } catch {
    response.writeHead(404, {'Content-Type': 'text/plain; charset=utf-8'});
    response.end('未找到此文件');
  }
});
server.listen(port, host, () => console.log(`夜视大师：http://${host}:${port}`));
