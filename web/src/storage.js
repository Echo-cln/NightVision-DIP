const DATABASE = 'nightvision-local-images';
export function readSetting(key, fallback) {
  try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; }
}
export function writeSetting(key, value) {
  try { localStorage.setItem(key, value); return true; } catch { return false; }
}

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains('images')) {
        request.result.createObjectStore('images', {keyPath: 'id'});
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(new Error('请关闭其他夜视大师标签页后重试'));
  });
}

async function transaction(mode, operation) {
  const db = await openDatabase();
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction('images', mode);
      const request = operation(tx.objectStore('images'));
      let result;
      request.onsuccess = () => { result = request.result; };
      tx.oncomplete = () => resolve(result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error || new Error('存储操作已取消'));
    });
  } finally { db.close(); }
}

export const dbPut = item => transaction('readwrite', store => store.put(item));
export const dbGet = id => transaction('readonly', store => store.get(id));
export const dbClear = () => transaction('readwrite', store => store.clear());

export async function readRecords() {
  // IndexedDB is authoritative. Metadata and pixels commit together, so a full
  // localStorage quota cannot leave a success message with a missing record.
  const rows = await transaction('readonly', store => store.getAll());
  let legacy = [];
  try { legacy = JSON.parse(readSetting('nightvision-records', '[]')); } catch { /* no metadata */ }
  return rows.map(row => row.meta || legacy.find(item => item.id === row.id))
    .filter(Boolean).sort((a, b) => b.timestamp - a.timestamp);
}
