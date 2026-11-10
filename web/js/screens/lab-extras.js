// Extra corners of the pronunciation lab: the IPA chart (tap a symbol, hear a word with it) and
// tongue twisters scored with the speech recognizer.
import { QUEST, bestSentenceScore } from '../logic.js';
import { listen, speaker } from '../speech.js';
import { Progress } from '../store.js';
import { accent, esc, micBtn, sayBtn, scored, topBar } from '../ui.js';

const GROUPS = [['vowel', 'Nguyên âm đơn'], ['diphthong', 'Nguyên âm đôi'], ['consonant', 'Phụ âm']];
const scoreColor = (s) => (s >= 80 ? 'mint' : s >= 50 ? 'peach' : 'coral');

export function ipaScreen(app) {
  let picked = null;
  const sounds = app.pronunciation.ipa || [];
  return {
    view: () => {
      const s = sounds.find((x) => x.symbol === picked);
      return `<div class="screen" ${accent('lavender')}>
        ${topBar('🔤 Bảng phiên âm IPA', `${sounds.length} âm tiếng Anh · chạm để nghe`)}
        <div class="scroll pad" data-scroll="ipa">
          ${s ? `<div class="card ipa-pick"><div class="ipa-big">/${esc(s.symbol)}/</div>
            <div class="grow"><div class="t-sub">${esc(s.example)}</div><div class="muted small">${esc(s.hint)}</div></div>
            ${sayBtn(s.example, { size: 48 })}${sayBtn(s.example, { size: 48, slow: true })}</div>`
            : '<p class="muted">Chạm vào một ký hiệu để nghe từ ví dụ và mẹo phát âm.</p>'}
          ${GROUPS.map(([type, label]) => `<div class="section"><h2>${label}</h2></div>
            <div class="ipa-grid">${sounds.filter((x) => x.type === type).map((x) => `
              <button class="ipa-cell ${x.symbol === picked ? 'on' : ''}" data-act="pick" data-s="${esc(x.symbol)}">
                <b>/${esc(x.symbol)}/</b><span class="tiny muted">${esc(x.example)}</span></button>`).join('')}</div>`).join('')}
        </div>
      </div>`;
    },
    actions: {
      pick: (d) => {
        picked = d.s;
        speaker.say(sounds.find((x) => x.symbol === d.s).example);
      },
    },
  };
}

const LEVELS = [['easy', '🟢 Dễ'], ['medium', '🟡 Vừa'], ['hard', '🔴 Khó']];

export function twistersScreen(app) {
  const list = app.pronunciation.twisters || [];
  let level = 'easy', open = null, result = null;
  return {
    view: () => {
      const best = Progress.get().twisterBest || {};
      return `<div class="screen" ${accent('lavender')}>
        ${topBar('👅 Câu líu lưỡi', 'Nói nhanh mà vẫn rõ từng âm')}
        <div class="scroll pad" data-scroll="twisters">
          <div class="seg" style="--al:var(--lavender-l)">${LEVELS.map(([id, label]) =>
            `<button class="${level === id ? 'on' : ''}" data-act="level" data-l="${id}">${label}</button>`).join('')}</div>
          ${list.filter((t) => t.level === level).map((t) => {
            const b = best[t.id];
            const isOpen = open === t.id;
            return `<div class="card twister ${isOpen ? 'open' : ''}">
              <button class="row center-v gap-s grow twister-head" data-act="open" data-id="${t.id}">
                <div class="grow"><div class="semi">${esc(t.en)}</div><div class="muted tiny">${esc(t.focus)}</div></div>
                ${b != null ? `<b style="color:var(--${scoreColor(b)})">${b}</b>` : '<span class="muted tiny">Mới</span>'}
              </button>
              ${isOpen ? `<div class="center">
                ${result ? `<div class="h-sm">${scored(result)}</div><div class="h-md" style="color:var(--${scoreColor(result.score)})">${result.score}</div>` : ''}
                <div class="muted small mt-s">${esc(t.vi)}</div>
                <div class="row center-h gap mt">${sayBtn(t.en, { size: 52 })}${sayBtn(t.en, { size: 52, slow: true })}${micBtn('speak', 64, `data-id="${t.id}"`)}</div>
                <p class="muted tiny">Nghe chậm trước, nói chậm cho đúng rồi mới tăng tốc.</p>
              </div>` : ''}
            </div>`;
          }).join('')}
        </div>
      </div>`;
    },
    actions: {
      level: (d) => { level = d.l; open = null; result = null; },
      open: (d) => { open = open === d.id ? null : d.id; result = null; },
      speak: (d) => {
        const t = list.find((x) => x.id === d.id);
        listen(t.en, {
          onResult: (alts) => {
            Progress.count(QUEST.SPEAK);
            result = bestSentenceScore(t.en, alts);
            const best = Progress.get().twisterBest || {};
            if ((best[t.id] ?? -1) < result.score) Progress.set({ twisterBest: { ...best, [t.id]: result.score } });
            app.update();
          },
        });
        return false;
      },
    },
  };
}
