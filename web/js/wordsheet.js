// Bottom sheet shown when a word is tapped: meaning, IPA, example, listen, save to review.
import { lookup, normalizeWord, translate } from './dict.js';
import { speaker, toast } from './speech.js';
import { Progress, Review } from './store.js';
import { esc } from './ui.js';

/** Review card for a vocabulary word; same id whether it was saved from a list or from a news story. */
export const wordCard = (e) => ({
  id: `v:${e.w}`,
  front: e.w,
  back: e.vi,
  extra: [e.ipa, e.ex].filter(Boolean).join(' · ') || null,
});

const POS = { n: 'danh từ', v: 'động từ', adj: 'tính từ', adv: 'trạng từ', prep: 'giới từ', pron: 'đại từ', conj: 'liên từ', det: 'từ hạn định', num: 'số từ', phr: 'cụm từ', excl: 'thán từ' };

export function openWordSheet(raw) {
  const word = normalizeWord(raw);
  if (!word) return;
  speaker.say(word);
  let entry = lookup(word);
  const el = document.createElement('div');
  el.className = 'sheet-back';
  document.body.appendChild(el);

  const render = (loading = false) => {
    const e = entry;
    const learned = e && Progress.get().learnedWords.includes(e.w);
    const saved = e && Review.has(`v:${e.w}`);
    el.innerHTML = `<div class="sheet" role="dialog" aria-label="Nghĩa của từ">
      <div class="sheet-grip"></div>
      <div class="row center-v gap">
        <div class="grow">
          <div class="word-big">${esc(e?.w || word)}</div>
          <div class="muted">${esc([e?.ipa, POS[e?.pos]].filter(Boolean).join(' · '))}</div>
        </div>
        <button class="say" data-x="say" style="--s:48px;--a:var(--sky)" aria-label="Nghe">
          <svg viewBox="0 0 24 24"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z" fill="currentColor"/></svg>
        </button>
      </div>
      <div class="word-vi" style="color:var(--sky)">${loading ? '<span class="muted small">Đang dịch…</span>' : esc(e?.vi || 'Chưa có nghĩa')}</div>
      ${e?.ex ? `<div class="tip" style="background:var(--sun-l)"><div class="italic">“${esc(e.ex)}”</div>${e.exVi ? `<div class="muted small">${esc(e.exVi)}</div>` : ''}</div>` : ''}
      ${e?.online ? '<div class="muted tiny">Nghĩa dịch tự động (từ này chưa có trong 3000 từ).</div>' : ''}
      <div class="row gap">
        <button class="btn outline grow" data-x="learned" ${!e?.vi ? 'disabled' : ''}>${learned ? '✅ Đã thuộc' : '☐ Đã thuộc'}</button>
        <button class="btn grow" data-x="save" ${!e?.vi || saved ? 'disabled' : ''} style="--a:var(--lavender)">${saved ? '🔁 Đã có trong ôn tập' : '＋ Ôn tập'}</button>
      </div>
    </div>`;
  };

  render(!entry);
  if (!entry)
    translate(word)
      .then((vi) => { entry = { w: word, ipa: '', pos: '', vi, ex: '', online: true }; render(); })
      .catch((err) => { entry = { w: word, ipa: '', pos: '', vi: '', ex: '' }; render(); toast(err.message); });

  el.addEventListener('click', (ev) => {
    const x = ev.target.closest('[data-x]')?.dataset.x;
    if (ev.target === el) return el.remove();
    if (x === 'say') speaker.say(entry?.w || word);
    if (x === 'learned') { Progress.toggleWord(entry.w); render(); }
    if (x === 'save') {
      Review.add([wordCard(entry)]);
      toast('Đã thêm vào Ôn tập hôm nay 🔁');
      render();
    }
  });
}
