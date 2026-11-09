export const $ = selector => document.querySelector(selector);
export const safe = value => String(value ?? '').replace(/[&<>"']/g, char => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[char]));

export async function loadImage(src) {
  const image = new Image();
  image.src = src;
  await image.decode();
  if (!image.naturalWidth) throw new Error('图片没有可读取的像素');
  return image;
}

export function canvasFor(image, maxEdge = 1600) {
  const canvas = document.createElement('canvas');
  const ratio = Math.min(1, maxEdge / Math.max(image.width, image.height));
  canvas.width = Math.max(1, Math.round(image.width * ratio));
  canvas.height = Math.max(1, Math.round(image.height * ratio));
  canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
  return canvas;
}

export function readFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

export function download(href, name) {
  const link = document.createElement('a');
  link.href = href;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
}

export function downloadJson(value, name) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)],
    {type: 'application/json'}));
  download(url, name);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
