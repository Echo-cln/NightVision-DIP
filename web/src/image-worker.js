import {processPixels} from './processing.js';

self.onmessage = ({data: {id, pixels, width, height, params}}) => {
  try {
    const result = processPixels(new Uint8ClampedArray(pixels), width, height, params);
    self.postMessage({id, result}, [result.data.buffer]);
  } catch (error) {
    self.postMessage({id, error: error.message || '图像计算失败'});
  }
};
