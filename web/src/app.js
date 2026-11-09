import {state, records, neutral, DEMO_IMAGE, setRecords, setFilterValue,
  setThemeValue, resetResult, allParameters, setParameter} from './model.js';
import {$, safe, loadImage, canvasFor, readFile, download, downloadJson} from './dom.js';
import {icon} from './icons.js';
import * as views from './views.js';
import {readSetting, writeSetting, readRecords, dbPut, dbGet, dbClear} from './storage.js';
import {ImageEngine} from './engine.js';
import {statistics, fft1D} from './processing.js';
import {referenceMetrics} from './quality.js';

const engine = new ImageEngine();
let toastTimer, previewFrame = 0, previewLoop = null, sourceSequence = 0;
let liveStream = null, reference = null, roiStart = null, roiDraft = null, zoom = 1;
let dialogOpener = null, dialogGeneration = 0;

function toast(message) {
  clearTimeout(toastTimer);
  $('#toast').innerHTML = `<div class="toast" role="status">${safe(message)}</div>`;
  toastTimer = setTimeout(() => { $('#toast').innerHTML = ''; }, 2600);
}

function render({preserveScroll = false} = {}) {
  const scroll = $('#viewport').scrollTop;
  $('#viewport').innerHTML = state.page === 'home' ? views.home()
    : state.page === 'editor' ? views.editor() : views.gallery();
  $('#bottom').innerHTML = state.page === 'editor' ? views.actionbar() : views.bottomNav();
  $('#viewport').scrollTop = preserveScroll ? scroll : 0;
  wireCompare();
  updateSaveButtons();
}

function updateSaveButtons() {
  for (const button of document.querySelectorAll('[data-action="saveRecord"], [data-action="downloadResult"]')) {
    button.disabled = state.saving || !state.processed;
  }
  for (const control of document.querySelectorAll('input[type="range"], select')) {
    control.disabled = state.saving;
  }
}

function nav(page) {
  if (!['home', 'gallery'].includes(page) || state.saving) return;
  sourceSequence++;
  closeSheet();
  state.page = page;
  state.previewVersion++;
  render();
}

function openEditor(mode) {
  closeSheet();
  sourceSequence++;
  Object.assign(state, neutral, {page: 'editor', mode, source: null, name: '', manual: false});
  resetResult();
  reference = null;
  render();
}

function applyTheme(theme, refresh = true) {
  const selected = ['green', 'berry', 'amber'].includes(theme) ? theme : 'green';
  setThemeValue(selected);
  document.documentElement.dataset.theme = selected;
  $('meta[name="theme-color"]').content = getComputedStyle(document.documentElement)
    .getPropertyValue('--bg').trim();
  writeSetting('nv-theme', selected);
  if (refresh) render({preserveScroll: true});
}

function setFilter(filter) { setFilterValue(filter); render(); }
function setView(mode) {
  state.viewMode = state.processed && ['result', 'original', 'compare'].includes(mode) ? mode : 'result';
  $('#picture-area').innerHTML = views.imageArea();
  wireCompare();
}

function refreshPicture() {
  const compare = $('#comparison-result');
  const single = state.manual ? $('#manual-image') : $('#single-image');
  if (compare) compare.src = state.result;
  else if (single) single.src = state.viewMode === 'original' ? state.source : state.result;
  else { $('#picture-area').innerHTML = views.imageArea(); wireCompare(); }
}

function wireCompare() {
  const frame = $('#interactive-compare');
  if (!frame) return;
  const handle = frame.querySelector('.compare-grip');
  const apply = value => {
    state.split = Math.max(0, Math.min(100, value));
    frame.style.setProperty('--split', state.split + '%');
    handle.setAttribute('aria-valuenow', Math.round(state.split));
  };
  const move = event => {
    const rect = frame.getBoundingClientRect();
    if (rect.width) apply((event.clientX - rect.left) / rect.width * 100);
  };
  handle.onpointerdown = event => {
    event.preventDefault(); handle.setPointerCapture(event.pointerId); move(event);
  };
  handle.onpointermove = event => { if (handle.hasPointerCapture(event.pointerId)) move(event); };
  handle.onpointerup = event => {
    if (handle.hasPointerCapture(event.pointerId)) handle.releasePointerCapture(event.pointerId);
  };
  handle.onkeydown = event => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    apply(event.key === 'Home' ? 0 : event.key === 'End' ? 100
      : state.split + (event.key === 'ArrowRight' ? 3 : -3));
  };
}

function setStatus(text = '', error = false) {
  const status = $('#live-status');
  if (!status) return;
  status.hidden = !text;
  status.textContent = text;
  status.setAttribute('role', error ? 'alert' : 'status');
}

async function processImage(silent = false, version = ++state.previewVersion, maxEdge = 960) {
  if (!state.source || state.page !== 'editor') return false;
  const source = state.source, params = allParameters();
  setStatus('正在处理…');
  try {
    const output = await engine.process(source, params, maxEdge);
    if (version !== state.previewVersion || state.source !== source || state.page !== 'editor') return false;
    const savedId = state.renderedVersion === version ? state.savedId : null;
    Object.assign(state, output.params);
    Object.assign(state, {
      result: output.url, processed: true, renderedVersion: version, renderedEdge: maxEdge,
      savedId, analysis: {...output.original, width: output.sourceSize[0], height: output.sourceSize[1]},
      report: {...output, data: undefined, url: undefined, mode: state.mode, method: state.method,
        manual: state.manual, params: allParameters()},
      roiCrops: null, rating: '',
    });
    if (!silent) render({preserveScroll: true});
    else {
      refreshPicture();
      const report = $('#report-body');
      if (report) report.innerHTML = views.resultDetails();
      $('#bottom').innerHTML = views.actionbar();
      updateSaveButtons();
    }
    setStatus();
    if (reference) await computeReference();
    return true;
  } catch (error) {
    if (version === state.previewVersion && state.page === 'editor') {
      setStatus('处理失败，请重试或更换图片', true);
      toast(error.message || '图片处理失败');
    }
    return false;
  }
}

function schedulePreview() {
  if (previewFrame || previewLoop) return;
  previewFrame = requestAnimationFrame(() => {
    previewFrame = 0;
    previewLoop = (async () => {
      while (state.source && state.page === 'editor' && state.renderedVersion !== state.previewVersion) {
        const version = state.previewVersion;
        const success = await processImage(true, version, 640);
        if (!success && version === state.previewVersion) break;
      }
    })().finally(() => { previewLoop = null; });
  });
}

async function flushPreview() {
  if (previewFrame) { cancelAnimationFrame(previewFrame); previewFrame = 0; }
  if (previewLoop) await previewLoop;
  if (!state.result || state.renderedVersion !== state.previewVersion || state.renderedEdge < 1600) {
    return processImage(true, state.previewVersion, 1600);
  }
  return state.page === 'editor';
}

function setProcessingMode(manual) {
  if (state.manual === manual || state.saving) return;
  state.manual = manual;
  state.method = manual ? 'Gamma' : state.mode === 'enhance' ? '自适应' : '自动复原';
  state.savedId = null;
  state.previewVersion++;
  render({preserveScroll: true});
  if (state.source) schedulePreview();
}

function updateParam(key, value, unit = '') {
  if (state.viewMode === 'original') setView('result');
  if (!setParameter(key, value)) return;
  const output = $('#out-' + key), input = $('#' + key);
  if (output) output.textContent = state[key] + unit;
  input?.setAttribute('aria-valuetext', state[key] + unit);
  schedulePreview();
}

function setOption(key, value) {
  const allowed = {method: ['Gamma', '直方图均衡', '白平衡'],
    colorSpace: ['YCbCr', 'HSV'], denoiseMethod: ['none', 'gaussian', 'median']};
  if (!allowed[key]?.includes(value)) return;
  state[key] = value;
  if (key === 'denoiseMethod' && value !== 'none' && state.noise === 0) state.noise = 30;
  if (!state.manual) { state.manual = true; if (key !== 'method') state.method = 'Gamma'; }
  state.savedId = null;
  state.previewVersion++;
  render({preserveScroll: true});
  $('.advanced-front').open = true;
  schedulePreview();
}

function resetControls() {
  Object.assign(state, neutral, {savedId: null, viewMode: 'result'});
  state.previewVersion++;
  render({preserveScroll: true});
  schedulePreview();
}

function pickFile() { closeSheet(); $('#fileInput').click(); }
function chooseImage() {
  sheet('更换图片', `<button class="option" data-action="pickFile"><span class="icon">${icon('image')}</span><strong>导入照片</strong></button><button class="option" data-action="pickCamera"><span class="icon">${icon('camera')}</span><strong>现场拍照</strong></button>`);
}

async function installSource(source, name, sourceType, sequence) {
  const image = await loadImage(source);
  if (sequence !== sourceSequence) return;
  closeSheet();
  if (state.manual) Object.assign(state, neutral);
  Object.assign(state, {source, name, sourceType, scene: '自选图片', page: 'editor'});
  resetResult(); reference = null;
  state.analysis = {width: image.width, height: image.height};
  render();
  await processImage();
}

async function acceptFile(file, camera = false) {
  if (!file) return;
  if (!file.type.startsWith('image/')) return toast('请选择 JPG、PNG 或 WebP 图片');
  if (file.size > 25 * 1024 * 1024) return toast('图片超过 25 MB，请先缩小');
  const sequence = ++sourceSequence;
  try {
    const data = await readFile(file);
    if (sequence !== sourceSequence) return;
    await installSource(data, file.name.replace(/\.[^.]+$/, '').slice(0, 60), camera ? '现场拍摄' : '相册导入', sequence);
  } catch { toast('图片读取失败，请换一张图片'); }
}

async function loadSample() {
  const sequence = ++sourceSequence;
  try { await installSource(DEMO_IMAGE, '夜间道路示例', '示例影像', sequence); }
  catch { toast('示例读取失败，请导入自己的图片'); }
}

function sheet(title, content) {
  closeSheet();
  dialogOpener = document.activeElement;
  $('#overlay').innerHTML = `<div class="sheet-overlay" data-backdrop><section class="sheet" role="dialog" aria-modal="true" aria-label="${safe(title)}"><div class="sheet-handle"></div><div class="sheet-head"><h2>${safe(title)}</h2><button class="icon-btn" data-action="closeSheet" aria-label="关闭">${icon('close')}</button></div>${content}</section></div>`;
  $('#overlay button')?.focus();
}

function closeSheet() {
  dialogGeneration++;
  if (liveStream) { liveStream.getTracks().forEach(track => track.stop()); liveStream = null; }
  $('#overlay').innerHTML = '';
  roiStart = null;
  if (dialogOpener?.isConnected) dialogOpener.focus();
  dialogOpener = null;
}

function about() {
  sheet('关于夜视大师', `<div class="info-box"><strong>夜视大师</strong><br>图像增强与颜色校正</div><p>支持亮度、对比度、降噪、饱和度和色温调整，以及亮度均衡与白平衡处理。</p><p>照片在本机处理。保存记录后可再次查看原图、结果和参数；清理浏览器数据会删除本地记录。</p><p>示例照片：Josh Hild / Unsplash。</p><button class="primary" data-action="closeSheet">关闭</button>`);
}

function fullImage(type) {
  zoom = 1;
  const original = type === 'original', src = original ? state.source : state.result;
  if (!src) return;
  closeSheet(); dialogOpener = document.activeElement;
  $('#overlay').innerHTML = `<div class="fullscreen" role="dialog" aria-modal="true" aria-label="图片大图"><header><button class="icon-btn" data-action="closeSheet" aria-label="关闭大图">${icon('close')}</button><span>${original ? '原图' : '处理后'}</span><span class="zoom-tools"><button data-action="zoomImage" data-arg0="-0.5" aria-label="缩小图片">−</button><output id="zoom-value">100%</output><button data-action="zoomImage" data-arg0="0.5" aria-label="放大图片">＋</button></span></header><div class="zoom-scroll"><img id="zoom-image" src="${src}" alt="完整大图"></div></div>`;
  $('#overlay button').focus();
}

function zoomImage(step) {
  zoom = Math.max(1, Math.min(3, zoom + step));
  $('#zoom-image').style.width = zoom * 100 + '%';
  $('#zoom-value').textContent = Math.round(zoom * 100) + '%';
}

async function pickCamera() {
  sheet('现场拍摄', `<video id="live-camera" class="camera-preview" autoplay playsinline muted></video><p id="camera-note">正在连接摄像头…</p><div class="camera-actions"><button class="secondary" data-action="systemCamera">系统相机</button><button class="primary" data-action="captureLive">${icon('camera')}拍摄</button></div>`);
  const generation = dialogGeneration;
  if (!navigator.mediaDevices?.getUserMedia) {
    $('#camera-note').textContent = '请使用系统相机，或通过 HTTPS 访问以开启预览。'; return;
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({video: {facingMode: {ideal: 'environment'}}, audio: false});
    if (generation !== dialogGeneration || !$('#live-camera')) { stream.getTracks().forEach(track => track.stop()); return; }
    liveStream = stream;
    $('#live-camera').srcObject = stream;
    $('#camera-note').textContent = '相机已就绪';
  } catch { if ($('#camera-note')) $('#camera-note').textContent = '未能访问摄像头，请允许权限或使用系统相机。'; }
}

function systemCamera() { closeSheet(); $('#cameraInput').click(); }
async function captureLive() {
  const video = $('#live-camera');
  if (!video?.videoWidth) return toast('相机尚未就绪');
  const canvas = document.createElement('canvas');
  canvas.width = video.videoWidth; canvas.height = video.videoHeight;
  canvas.getContext('2d').drawImage(video, 0, 0);
  const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', .95));
  if (!blob) return toast('拍摄失败，请重试');
  closeSheet();
  await acceptFile(new File([blob], '现场拍摄.jpg', {type: 'image/jpeg'}), true);
}

async function saveRecord() {
  if (state.saving) return;
  state.saving = true; updateSaveButtons();
  try {
    if (!await flushPreview()) return;
    const id = state.savedId || 'nv_' + crypto.randomUUID();
    const snapshot = {id, original: state.source, result: state.result, analysis: state.analysis,
      report: state.report, roi: state.roi, roiCrops: state.roiCrops, rating: state.rating};
    const image = await loadImage(snapshot.result);
    const meta = {id, name: state.name, scene: state.scene, sourceType: state.sourceType,
      mode: state.mode, method: state.method, manual: state.manual,
      thumb: canvasFor(image, 300).toDataURL('image/jpeg', .8), timestamp: Date.now(),
      time: new Date().toLocaleString('zh-CN', {month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'}),
      params: {gamma: state.report.gamma, contrast: state.report.gain, whiteBalance: state.report.wb},
      frontParams: allParameters(), duration: state.report.duration,
      brightness: [state.report.original.mean, state.report.result.mean], rating: state.rating, status: '已保存'};
    await dbPut({...snapshot, meta});
    setRecords([meta, ...records.filter(record => record.id !== id)]);
    state.savedId = id;
    $('#bottom').innerHTML = views.actionbar();
    toast('记录已保存');
  } catch { toast('记录保存失败，请先下载图片或检查浏览器存储空间'); }
  finally { state.saving = false; updateSaveButtons(); }
}

async function viewRecord(index) {
  const meta = records[index];
  if (!meta) return;
  try {
    const saved = await dbGet(meta.id);
    if (!saved) return toast('本地图片已被清理，请重新导入');
    closeSheet(); resetResult();
    Object.assign(state, neutral, meta.frontParams || saved.report?.params || {}, {
      page: 'editor', mode: meta.mode || 'enhance', source: saved.original, result: saved.result,
      name: meta.name, sourceType: meta.sourceType || '历史记录', scene: meta.scene,
      processed: true, viewMode: 'result', analysis: saved.analysis, report: saved.report,
      roi: saved.roi, roiCrops: saved.roiCrops, rating: saved.rating || '', savedId: meta.id,
      manual: !!meta.manual, method: meta.method, renderedVersion: state.previewVersion,
      renderedEdge: Math.max(...saved.report.size),
    });
    state.brightness = Math.round((100 - state.gamma) / .6);
    reference = null; render();
  } catch { toast('记录读取失败，请重试'); }
}

async function downloadResult() {
  if (state.saving) return;
  state.saving = true; updateSaveButtons();
  try {
    if (!await flushPreview()) return;
    download(state.result, '夜视大师_' + state.name.replace(/[\\/:*?"<>|]/g, '_') + '.jpg');
    toast('已发起图片保存');
  } finally { state.saving = false; updateSaveButtons(); }
}

function exportParameters() { downloadJson({version: 1, parameters: allParameters()}, 'NightVision-参数.json'); }
function exportRecords() {
  if (!records.length) return toast('还没有可导出的记录');
  downloadJson(records.map(({thumb, ...record}) => record), '夜视大师_处理记录.json');
}
function recordMenu() {
  sheet('管理处理记录', `<button class="option" data-action="exportRecords"><span class="icon">${icon('download')}</span><strong>导出处理记录</strong></button><button class="option" data-action="confirmClear"><span class="icon">${icon('reset')}</span><strong>清空本地记录</strong></button>`);
}
function confirmClear() {
  sheet('清空全部记录？', `<p>这会删除当前浏览器中的原图、结果与参数记录，已下载的图片不受影响。</p><button class="primary" data-action="clearRecords">确认清空</button><button class="reset-link" data-action="closeSheet">保留记录</button>`);
}
async function clearRecords() {
  try { await dbClear(); writeSetting('nightvision-records', '[]'); setRecords([]); closeSheet(); render(); toast('本地记录已清空'); }
  catch { toast('清理失败，请稍后重试'); }
}

function pickReference() { $('#referenceInput').click(); }
async function computeReference() {
  if (!reference || !state.result) return;
  const resultUrl = state.result;
  const image = await loadImage(resultUrl), ratio = image.width / image.height;
  if (resultUrl !== state.result) return;
  if (Math.abs(ratio - reference.width / reference.height) > .015) {
    state.referenceText = '参考图比例不同，请提供同场景、同裁剪的清晰参考图。';
  } else {
    const width = Math.max(8, Math.min(256, image.width)), height = Math.max(8, Math.round(width / ratio));
    const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
    const context = canvas.getContext('2d', {willReadFrequently: true});
    context.drawImage(image, 0, 0, width, height);
    const a = context.getImageData(0, 0, width, height).data;
    context.drawImage(reference, 0, 0, width, height);
    const b = context.getImageData(0, 0, width, height).data;
    const metrics = referenceMetrics(a, b, width, height);
    state.referenceText = `PSNR ${isFinite(metrics.psnr) ? metrics.psnr.toFixed(2) : '∞'} dB · SSIM ${metrics.ssim.toFixed(4)}。${width}×${height} 像素，8×8 非重叠窗口；请确认场景与像素对齐。`;
  }
  const element = $('#reference-value');
  if (element) element.textContent = state.referenceText;
}

function rate(value) {
  if (!['更清晰', '接近原图', '出现失真'].includes(value)) return;
  state.rating = value;
  const element = $('#report-body');
  if (element) element.innerHTML = views.resultDetails();
  toast('评价已记录，点击保存记录后保留');
}

function selectRoi() {
  if (!state.result) return;
  roiDraft = null;
  sheet('框选目标区域', `<p>在原图上拖动框选需要观察的区域。</p><div class="roi-select" id="roi-select"><img src="${state.source}" alt="在原图上框选目标区域"><div id="roi-rect"></div></div><p id="roi-note">请按住并拖动框选</p><button class="primary" id="roi-confirm" disabled data-action="confirmRoi">对比所选区域</button>`);
  const box = $('#roi-select');
  const point = event => { const rect = box.getBoundingClientRect(); return {
    x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
    y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height))}; };
  box.onpointerdown = event => { roiStart = point(event); box.setPointerCapture(event.pointerId); };
  box.onpointermove = event => {
    if (!roiStart) return;
    const p = point(event);
    roiDraft = {x: Math.min(p.x, roiStart.x), y: Math.min(p.y, roiStart.y),
      w: Math.abs(p.x - roiStart.x), h: Math.abs(p.y - roiStart.y)};
    $('#roi-rect').style.cssText = `display:block;left:${roiDraft.x * 100}%;top:${roiDraft.y * 100}%;width:${roiDraft.w * 100}%;height:${roiDraft.h * 100}%`;
  };
  box.onpointerup = () => {
    roiStart = null;
    const valid = roiDraft && roiDraft.w > .04 && roiDraft.h > .04;
    $('#roi-confirm').disabled = !valid;
    $('#roi-note').textContent = valid ? '区域已选定' : '区域太小，请重新框选';
  };
  box.onpointercancel = () => { roiStart = null; };
}

async function cropRegion(source, region) {
  const image = await loadImage(source), canvas = document.createElement('canvas');
  const width = Math.max(1, Math.round(image.width * region.w)), height = Math.max(1, Math.round(image.height * region.h));
  const ratio = Math.min(1, 400 / Math.max(width, height));
  canvas.width = Math.max(1, Math.round(width * ratio)); canvas.height = Math.max(1, Math.round(height * ratio));
  const context = canvas.getContext('2d', {willReadFrequently: true});
  context.drawImage(image, image.width * region.x, image.height * region.y, width, height, 0, 0, canvas.width, canvas.height);
  return {src: canvas.toDataURL('image/jpeg', .94), mean: statistics(context.getImageData(0, 0, canvas.width, canvas.height), canvas.width, canvas.height).mean};
}

async function confirmRoi() {
  if (!roiDraft) return;
  try {
    const region = {...roiDraft}, result = state.result;
    const [a, b] = await Promise.all([cropRegion(state.source, region), cropRegion(result, region)]);
    if (result !== state.result) return;
    state.roi = region;
    state.roiCrops = {original: a.src, result: b.src, beforeMean: a.mean, afterMean: b.mean};
    closeSheet();
    $('#roi-comparison').innerHTML = views.roiComparison();
  } catch { toast('区域读取失败，请重新框选'); }
}

async function showSpectrum() {
  if (!state.source) return;
  sheet('图像频谱', `<p>64×64 灰度采样，减均值并施加 Hann 窗；显示居中对数幅度。</p><canvas id="real-spectrum" width="256" height="256" class="spectrum-canvas"></canvas><p>中心附近为低频，向外为高频。边缘与噪声都可能贡献高频成分。</p>`);
  const generation = dialogGeneration;
  const image = await loadImage(state.source), n = 64, canvas = document.createElement('canvas');
  canvas.width = canvas.height = n;
  const context = canvas.getContext('2d'); context.drawImage(image, 0, 0, n, n);
  const pixels = context.getImageData(0, 0, n, n).data, re = new Float64Array(n * n), im = new Float64Array(n * n);
  let mean = 0;
  for (let i = 0; i < n * n; i++) { re[i] = .299 * pixels[i * 4] + .587 * pixels[i * 4 + 1] + .114 * pixels[i * 4 + 2]; mean += re[i]; }
  mean /= n * n;
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) re[y * n + x] = (re[y * n + x] - mean) * (.5 - .5 * Math.cos(2 * Math.PI * x / (n - 1))) * (.5 - .5 * Math.cos(2 * Math.PI * y / (n - 1)));
  for (let y = 0; y < n; y++) { const a = re.slice(y * n, y * n + n), b = im.slice(y * n, y * n + n); fft1D(a, b); re.set(a, y * n); im.set(b, y * n); }
  for (let x = 0; x < n; x++) {
    const a = new Float64Array(n), b = new Float64Array(n);
    for (let y = 0; y < n; y++) { a[y] = re[y * n + x]; b[y] = im[y * n + x]; }
    fft1D(a, b);
    for (let y = 0; y < n; y++) { re[y * n + x] = a[y]; im[y * n + x] = b[y]; }
  }
  const magnitudes = Float64Array.from(re, (value, i) => Math.log1p(Math.hypot(value, im[i])));
  const max = Math.max(...magnitudes) || 1, output = context.createImageData(n, n);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    const value = Math.round(255 * magnitudes[((y + n / 2) % n) * n + (x + n / 2) % n] / max), i = (y * n + x) * 4;
    output.data[i] = output.data[i + 1] = output.data[i + 2] = value; output.data[i + 3] = 255;
  }
  context.putImageData(output, 0, 0);
  const target = $('#real-spectrum');
  if (generation === dialogGeneration && target) { const ctx = target.getContext('2d'); ctx.imageSmoothingEnabled = false; ctx.drawImage(canvas, 0, 0, 256, 256); }
}

// One delegated listener handles controls created by any renderer or modal.
const actions = {
  nav, openEditor, applyTheme, setFilter, setView, fullImage, resetControls,
  pickFile, chooseImage, pickCamera, loadSample, closeSheet, about,
  saveRecord, downloadResult, viewRecord: index => viewRecord(Number(index)),
  setProcessingMode: value => setProcessingMode(value === 'true'),
  processImage: () => { state.savedId = null; return processImage(); },
  exportParameters, showSpectrum, systemCamera, captureLive, recordMenu,
  exportRecords, confirmClear, clearRecords, pickReference, selectRoi, confirmRoi,
  rate, zoomImage: value => zoomImage(Number(value)),
};

document.addEventListener('click', async event => {
  if (event.target.matches('[data-backdrop]')) { closeSheet(); return; }
  const button = event.target.closest('[data-action]');
  if (!button || button.disabled || state.saving) return;
  const action = actions[button.dataset.action];
  if (!action) return;
  const args = Object.keys(button.dataset).filter(key => /^arg\d+$/.test(key))
    .sort((a, b) => Number(a.slice(3)) - Number(b.slice(3))).map(key => button.dataset[key]);
  try { await action(...args); } catch (error) { toast(error.message || '操作失败，请重试'); }
});
document.addEventListener('input', event => {
  if (state.saving || !event.target.dataset.param) return;
  updateParam(event.target.dataset.param, event.target.value, event.target.dataset.unit || '');
});
document.addEventListener('change', event => {
  if (!state.saving && event.target.dataset.option) setOption(event.target.dataset.option, event.target.value);
});
document.addEventListener('keydown', event => {
  const dialog = $('#overlay [role="dialog"]');
  if (!dialog) return;
  if (event.key === 'Escape') { event.preventDefault(); closeSheet(); }
  if (event.key !== 'Tab') return;
  const elements = [...dialog.querySelectorAll('button:not(:disabled), input, select, [tabindex="0"]')];
  const first = elements[0], last = elements.at(-1);
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
});

$('#fileInput').addEventListener('change', async event => { const file = event.target.files[0]; event.target.value = ''; await acceptFile(file); });
$('#cameraInput').addEventListener('change', async event => { const file = event.target.files[0]; event.target.value = ''; await acceptFile(file, true); });
$('#referenceInput').addEventListener('change', async event => {
  const file = event.target.files[0]; event.target.value = '';
  if (!file) return;
  if (file.size > 25 * 1024 * 1024) return toast('参考图超过 25 MB');
  try { reference = await loadImage(await readFile(file)); await computeReference(); }
  catch { toast('参考图加载失败'); }
});
document.querySelectorAll('[data-icon]').forEach(element => { element.innerHTML = icon(element.dataset.icon); });
applyTheme(readSetting('nv-theme', 'green'), false);
render();
try {
  setRecords(await readRecords());
  if (state.page === 'gallery') render({preserveScroll: true});
} catch { toast('本地记录暂时不可用，仍可处理并下载图片'); }
window.addEventListener('pagehide', () => { liveStream?.getTracks().forEach(track => track.stop()); });
