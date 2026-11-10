import test from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {Worker} from 'node:worker_threads';

test('actual worker returns processed pixels and rejects a malformed job', async () => {
  const worker = new Worker(new URL('./worker-harness.mjs', import.meta.url));
  try {
    const [ready] = await once(worker, 'message'); assert(ready.ready);
    const data = Uint8ClampedArray.from({length: 256}, (_, i) => i % 4 === 3 ? 255 : 25);
    const first = once(worker, 'message');
    worker.postMessage({id: 1, pixels: data.buffer, width: 8, height: 8,
      params: {manual: true, gamma: 60, saturation: 100}}, [data.buffer]);
    assert.equal(data.byteLength, 0);
    const [message] = await first;
    assert.equal(message.id, 1); assert(message.result.result.mean > 25);
    const second = once(worker, 'message');
    worker.postMessage({id: 2, pixels: new ArrayBuffer(1), width: 8, height: 8, params: {}});
    const [failed] = await second; assert(failed.error); assert(!failed.result);
  } finally { await worker.terminate(); }
});
