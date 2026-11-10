/** RGB PSNR and mean luminance SSIM over non-overlapping 8x8 windows. */
export function referenceMetrics(a, b, width, height) {
  if (a.length !== b.length || a.length !== width * height * 4 || width < 8 || height < 8) {
    throw new Error('参考图与结果图需要相同尺寸，且至少为 8×8 像素');
  }
  let mse = 0, ssim = 0, blocks = 0;
  for (let i = 0; i < a.length; i += 4) {
    for (let k = 0; k < 3; k++) mse += (a[i + k] - b[i + k]) ** 2;
  }
  mse /= width * height * 3;
  for (let y = 0; y <= height - 8; y += 8) {
    for (let x = 0; x <= width - 8; x += 8) {
      let ma = 0, mb = 0, va = 0, vb = 0, cov = 0;
      const aa = [], bb = [];
      for (let dy = 0; dy < 8; dy++) for (let dx = 0; dx < 8; dx++) {
        const j = ((y + dy) * width + x + dx) * 4;
        const A = .299 * a[j] + .587 * a[j + 1] + .114 * a[j + 2];
        const B = .299 * b[j] + .587 * b[j + 1] + .114 * b[j + 2];
        aa.push(A); bb.push(B); ma += A; mb += B;
      }
      ma /= 64; mb /= 64;
      for (let i = 0; i < 64; i++) {
        va += (aa[i] - ma) ** 2; vb += (bb[i] - mb) ** 2;
        cov += (aa[i] - ma) * (bb[i] - mb);
      }
      va /= 63; vb /= 63; cov /= 63;
      ssim += ((2 * ma * mb + 6.5025) * (2 * cov + 58.5225)) /
        ((ma * ma + mb * mb + 6.5025) * (va + vb + 58.5225));
      blocks++;
    }
  }
  return {psnr: mse ? 10 * Math.log10(65025 / mse) : Infinity, ssim: ssim / blocks};
}
