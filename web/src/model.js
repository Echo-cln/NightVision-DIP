export const DEMO_IMAGE = new URL('../assets/night-road.jpg', import.meta.url).href;

export const neutral = Object.freeze({
  brightness: 0, gamma: 100, contrast: 0, noise: 0,
  saturation: 100, temperature: 0, colorSpace: 'YCbCr',
  denoiseMethod: 'none', method: 'Gamma',
});

export const state = {
  ...neutral, page: 'home', mode: 'enhance', source: null, result: null,
  name: '', sourceType: '', scene: '', manual: false, processed: false,
  viewMode: 'result', split: 50, analysis: null, report: null, roi: null,
  roiCrops: null, rating: '', savedId: null, previewVersion: 0,
  renderedVersion: -1, renderedEdge: 0, saving: false, referenceText: '',
};

export let records = [];
export let filter = 'all';
export let currentTheme = 'green';
export function setRecords(value) { records = value; }
export function setFilterValue(value) { filter = value; }
export function setThemeValue(value) { currentTheme = value; }

export function resetResult() {
  state.previewVersion++;
  Object.assign(state, {
    result: null, processed: false, report: null, analysis: null,
    roi: null, roiCrops: null, rating: '', savedId: null,
    viewMode: 'result', split: 50, renderedVersion: -1,
    renderedEdge: 0, referenceText: '',
  });
}

export function allParameters() {
  const keys = ['mode', 'manual', 'method', 'brightness', 'gamma', 'contrast',
    'noise', 'saturation', 'temperature', 'colorSpace', 'denoiseMethod'];
  return Object.fromEntries(keys.map(key => [key, state[key]]));
}

export function setParameter(key, value) {
  const limits = {brightness: [-100, 100], contrast: [-100, 100], noise: [0, 100],
    saturation: [0, 200], temperature: [-40, 40]};
  if (!Object.hasOwn(limits, key) || !Number.isFinite(Number(value))) return false;
  const [min, max] = limits[key];
  state[key] = Math.max(min, Math.min(max, Number(value)));
  if (key === 'brightness') state.gamma = 100 - state.brightness * .6;
  if (key === 'noise' && state.noise > 0 && state.denoiseMethod === 'none') {
    state.denoiseMethod = 'gaussian';
  }
  state.savedId = null;
  state.previewVersion++;
  return true;
}
