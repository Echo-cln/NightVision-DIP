import test from 'node:test';
import assert from 'node:assert/strict';
import {processPixels, smallFilter, statistics, fft1D} from '../src/processing.js';
import {referenceMetrics} from '../src/quality.js';

function solid(value, width = 8, height = 8) {
  return Uint8ClampedArray.from({length: width * height * 4}, (_, i) => i % 4 === 3 ? 255 : value);
}
const neutral = {manual: true, method: 'Gamma', gamma: 100, contrast: 0, noise: 0,
  saturation: 100, temperature: 0, denoiseMethod: 'none', colorSpace: 'YCbCr'};

test('neutral adjustment preserves every RGB and alpha sample', () => {
  const pixels = Uint8ClampedArray.from({length: 8 * 8 * 4}, (_, i) => (i * 23) % 256);
  const expected = pixels.slice();
  processPixels(pixels, 8, 8, neutral);
  assert.deepEqual(pixels, expected);
});

test('brightness actually raises dark pixel values without changing alpha', () => {
  const output = processPixels(solid(25), 8, 8, {...neutral, gamma: 55});
  assert(output.result.mean > 60);
  assert.equal(output.data[3], 255);
});

test('white balance responds to a color cast', () => {
  const pixels = solid(40);
  for (let i = 0; i < pixels.length; i += 4) pixels[i] = 80;
  const before = pixels[0] - pixels[2];
  const out = processPixels(pixels, 8, 8, {...neutral, method: '白平衡'});
  assert(out.wb);
  assert(out.data[0] - out.data[2] < before);
});

test('temperature warms red and cools blue', () => {
  const out = processPixels(solid(90), 8, 8, {...neutral, temperature: 20});
  assert(out.data[0] > out.data[2]);
});

test('zero saturation produces equal RGB channels', () => {
  const pixels = solid(70); pixels[0] = 140; pixels[2] = 30;
  const out = processPixels(pixels, 8, 8, {...neutral, saturation: 0});
  assert.equal(out.data[0], out.data[1]); assert.equal(out.data[1], out.data[2]);
});

test('3x3 Gaussian kernel matches independently calculated center and corner', () => {
  const data = solid(0, 3, 3);
  data[(1 * 3 + 1) * 4] = 160;
  smallFilter({data}, 3, 3, 'gaussian', 100);
  assert.equal(data[(1 * 3 + 1) * 4], 40);
  assert.equal(data[0], 10);
  assert.equal(data[3], 255);
});

test('median removes an isolated impulse', () => {
  const data = solid(40, 3, 3); data.set([255, 255, 255, 255], 16);
  smallFilter({data}, 3, 3, 'median', 100);
  assert.equal(data[16], 40);
});

test('uniform histogram has no division-by-zero artifacts', () => {
  const out = processPixels(solid(80), 8, 8, {...neutral, method: '直方图均衡'});
  assert.equal(out.result.mean, 80);
});

test('histogram equalization still responds to brightness', () => {
  const pixels = solid(30);
  for (let i = 0; i < pixels.length; i += 4) pixels[i] = pixels[i + 1] = pixels[i + 2] = i % 128;
  const a = processPixels(pixels.slice(), 8, 8, {...neutral, method: '直方图均衡'});
  const b = processPixels(pixels.slice(), 8, 8, {...neutral, method: '直方图均衡', gamma: 55});
  assert.notDeepEqual(a.data, b.data);
});

test('automatic mode selects a stronger correction for a dark image', () => {
  const params = {manual: false, mode: 'enhance'};
  const snapshot = {...params};
  const dark = processPixels(solid(15), 8, 8, params);
  const bright = processPixels(solid(160), 8, 8, params);
  assert(dark.gamma < bright.gamma); assert.deepEqual(params, snapshot);
});

test('HSV and YCbCr paths return finite measured output', () => {
  for (const colorSpace of ['HSV', 'YCbCr']) {
    const out = processPixels(solid(35), 8, 8, {...neutral, colorSpace, gamma: 60});
    assert(Number.isFinite(out.result.mean)); assert(out.result.mean > out.original.mean);
  }
});

test('invalid dimensions reject instead of producing a successful report', () => {
  assert.throws(() => processPixels(solid(20), 7, 8, neutral), /尺寸/);
});

test('statistics distinguish dark from highlighted pixels', () => {
  assert.equal(statistics({data: solid(0)}, 8, 8).dark, 100);
  assert.equal(statistics({data: solid(255)}, 8, 8).highlights, 100);
});

test('reference metrics recognize identity and degradation', () => {
  const a = solid(50), b = solid(70);
  assert.equal(referenceMetrics(a, a, 8, 8).ssim, 1);
  assert.equal(referenceMetrics(a, a, 8, 8).psnr, Infinity);
  assert(referenceMetrics(a, b, 8, 8).ssim < 1);
  assert(Math.abs(referenceMetrics(a, b, 8, 8).psnr - 22.1102) < .001);
  assert.throws(() => referenceMetrics(a, b.slice(4), 8, 8), /尺寸/);
});

test('FFT impulse has a flat magnitude spectrum', () => {
  const re = new Float64Array(8), im = new Float64Array(8); re[0] = 1;
  fft1D(re, im);
  assert.deepEqual([...re], Array(8).fill(1)); assert.deepEqual([...im], Array(8).fill(0));
});
