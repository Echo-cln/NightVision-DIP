import {canvasFor, loadImage} from './dom.js';
import {processPixels} from './processing.js';

export class ImageEngine {
  #worker;
  #pending = new Map();
  #serial = 0;
  #source;
  #decoded;
  constructor() {
    try {
      this.#worker = new Worker(new URL('./image-worker.js', import.meta.url), {type: 'module'});
      this.#worker.onmessage = ({data: {id, result, error}}) => {
        const job = this.#pending.get(id);
        if (!job) return;
        this.#pending.delete(id);
        if (error) job.reject(new Error(error)); else job.resolve(result);
      };
      this.#worker.onerror = () => {
        for (const job of this.#pending.values()) job.reject(new Error('图像处理线程启动失败，请重试'));
        this.#pending.clear();
        this.#worker.terminate();
        this.#worker = null;
      };
    } catch { this.#worker = null; }
  }
  async process(source, params, maxEdge) {
    if (this.#source !== source) {
      this.#source = source;
      this.#decoded = loadImage(source);
    }
    const image = await this.#decoded;
    const canvas = canvasFor(image, maxEdge);
    const context = canvas.getContext('2d', {willReadFrequently: true});
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
    let output;
    if (this.#worker) {
      const id = ++this.#serial;
      output = await new Promise((resolve, reject) => {
        this.#pending.set(id, {resolve, reject});
        this.#worker.postMessage({id, pixels: pixels.data.buffer, width: canvas.width,
          height: canvas.height, params}, [pixels.data.buffer]);
      });
    } else output = processPixels(pixels.data, canvas.width, canvas.height, params);
    context.putImageData(new ImageData(output.data, canvas.width, canvas.height), 0, 0);
    return {...output, url: canvas.toDataURL('image/jpeg', .94),
      sourceSize: [image.width, image.height], size: [canvas.width, canvas.height]};
  }
}
