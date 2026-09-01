/**
 * Review sonuclarinin tarayici tarafi deposu.
 *
 * Ana ekran ile sonuc sekmesi ayri window'lardir; veri URL ile tasinamaz
 * (kod 10.000 karaktere kadar cikabilir). Ayni origin'de olduklari icin
 * localStorage ortak alan olarak kullanilir.
 */
const KEY_PREFIX = 'review:';
const MAX_ENTRIES = 20;

export function saveReview(review) {
  try {
    localStorage.setItem(KEY_PREFIX + review.taskId, JSON.stringify(review));
    prune();
  } catch {
    // Private mode / kota dolu: sonuc yine de ana ekranda gorunur, sekme bos durum gosterir.
  }
}

export function loadReview(taskId) {
  try {
    const raw = localStorage.getItem(KEY_PREFIX + taskId);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function prune() {
  const keys = Object.keys(localStorage).filter((key) => key.startsWith(KEY_PREFIX));
  if (keys.length <= MAX_ENTRIES) {
    return;
  }
  keys
    .map((key) => {
      let createdAt = 0;
      try {
        createdAt = JSON.parse(localStorage.getItem(key))?.createdAt ?? 0;
      } catch {
        createdAt = 0;
      }
      return { key, createdAt };
    })
    .sort((a, b) => a.createdAt - b.createdAt)
    .slice(0, keys.length - MAX_ENTRIES)
    .forEach(({ key }) => localStorage.removeItem(key));
}
