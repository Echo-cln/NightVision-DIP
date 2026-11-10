import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {state, neutral, resetResult, setParameter, setThemeValue, setRecords} from '../src/model.js';
import * as views from '../src/views.js';

function setup() {
  Object.assign(state, neutral, {page: 'editor', source: 'data:original', result: 'data:result',
    processed: true, manual: false, viewMode: 'result', split: 50, analysis: null, report: null});
}

test('default automatic editor has one result image and corner tools without display tabs', () => {
  setup(); const html = views.editor();
  assert.equal((views.imageArea().match(/<img /g) || []).length, 1);
  assert(views.imageArea().includes('data:result'));
  assert(html.includes('拖动对比')); assert(html.includes('查看原图'));
  assert(!html.includes('上下对比')); assert(!html.includes('picture-tabs'));
  assert(!html.includes('onclick='));
});

test('original toggle and drag comparison render their distinct image sources', () => {
  setup(); state.viewMode = 'original';
  assert(views.imageArea().includes('data:original')); assert(!views.imageArea().includes('data:result'));
  state.viewMode = 'compare';
  assert.equal((views.imageArea().match(/<img /g) || []).length, 2);
  assert(views.imageArea().includes('role="slider"'));
  assert(views.imageArea().includes('aria-valuenow="50"'));
});

test('manual page has five working parameter keys and no placeholder methods', () => {
  setup(); state.manual = true;
  const html = views.editor();
  assert.equal((html.match(/type="range"/g) || []).length, 5);
  for (const key of ['brightness', 'contrast', 'noise', 'saturation', 'temperature']) assert(html.includes(`data-param="${key}"`));
  assert(!/待接入|待后端|界面示意/.test(html));
});

test('parameter validation prevents unknown and out-of-range state', () => {
  setup(); const version = state.previewVersion;
  assert(!setParameter('page', 'home')); assert(!setParameter('noise', NaN));
  assert(setParameter('brightness', 999)); assert.equal(state.brightness, 100);
  assert.equal(state.gamma, 40); assert.equal(state.previewVersion, version + 1);
  assert(setParameter('noise', 25)); assert.equal(state.denoiseMethod, 'gaussian');
});

test('all three accepted themes remain available', () => {
  setup();
  for (const theme of ['green', 'berry', 'amber']) {
    setThemeValue(theme); const html = views.home();
    assert.equal((html.match(/class="theme-choice"/g) || []).length, 3);
    assert.equal((html.match(/aria-pressed="true"/g) || []).length, 1);
  }
});

test('new source resets comparison and prior saved result', () => {
  setup(); state.viewMode = 'compare'; state.savedId = 'old';
  resetResult(); assert.equal(state.viewMode, 'result'); assert.equal(state.savedId, null);
  assert.equal(state.result, null);
});

test('record display escapes imported names', () => {
  setup(); setRecords([{id: 'one', name: '<img onerror=alert(1)>', thumb: 'data:thumb', mode: 'enhance', time: 'now'}]);
  assert(!views.gallery().includes('<img onerror'));
  assert(views.gallery().includes('&lt;img'));
});

test('every rendered action has a controller entry and parseable data arguments', async () => {
  setup();
  const app = await readFile(new URL('../src/app.js', import.meta.url), 'utf8');
  const registry = app.slice(app.indexOf('const actions = {'), app.indexOf("document.addEventListener('click'"));
  const html = [views.home(), views.editor(), views.moduleInput(), views.gallery(), views.advancedControls()].join('');
  for (const match of html.matchAll(/data-action="([^"]+)"/g)) assert(registry.includes(match[1]), match[1]);
  assert(!html.includes('data-arg-')); assert(html.includes('data-arg0="enhance"'));
});
