import { loadReview } from './store.js';

const el = (id) => document.getElementById(id);

const taskId = new URLSearchParams(window.location.search).get('taskId');
const review = taskId ? loadReview(taskId) : null;

if (!review) {
  el('empty').hidden = false;
} else {
  el('content').hidden = false;
  // textContent: gelen metin HTML olarak yorumlanmaz.
  el('code-block').textContent = review.code;
  el('result-block').textContent = review.result;
  el('meta').textContent = `${review.taskId} · ${new Date(review.createdAt).toLocaleString('tr-TR')}`;
  document.title = `Review ${review.taskId.slice(0, 8)} — CodeMentor`;

  // highlight.js yuklenmediyse (CDN kapali) sayfa duz monospace olarak kalir.
  if (window.hljs) {
    window.hljs.highlightElement(el('code-block'));
  }
}
