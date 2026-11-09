import {readdir, readFile, access} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('..', import.meta.url));
let count = 0;
for (const name of await readdir(path.join(root, 'src'))) {
  if (!name.endsWith('.js')) continue;
  const file = path.join(root, 'src', name), source = await readFile(file, 'utf8');
  const result = spawnSync(process.execPath, ['--check', file], {encoding: 'utf8'});
  if (result.status) throw new Error(result.stderr);
  if (/\bon(?:click|input|change)\s*=\s*["']/.test(source)) throw new Error(`${name}: 内联事件处理器`);
  if (/\beval\s*\(|new Function\s*\(/.test(source)) throw new Error(`${name}: 动态执行代码`);
  for (const match of source.matchAll(/from ['"](\.[^'"]+)['"]/g)) {
    await access(path.resolve(path.dirname(file), match[1]));
  }
  count++;
}
console.log(`检查通过：${count} 个 JavaScript 模块，引用路径完整，无内联事件处理器。`);
