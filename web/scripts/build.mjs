import {cp, mkdir, rm} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('..', import.meta.url));
const dist = path.join(root, 'dist');
await rm(dist, {recursive: true, force: true});
await mkdir(dist, {recursive: true});
for (const name of ['index.html', 'src', 'styles', 'assets']) {
  await cp(path.join(root, name), path.join(dist, name), {recursive: true});
}
console.log('构建完成：web/dist/');
