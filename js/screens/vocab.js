// "3000 từ vựng": themed word groups with a word list, flashcards, a quick quiz and SRS enrolment.
import { pick, shuffled } from '../logic.js';
import { speaker, toast } from '../speech.js';
import { Progress, Review } from '../store.js';
import { accent, badge, bar, bigBtn, completeDialog, esc, outBtn, sayBtn, topBar } from '../ui.js';
import { wordCard } from '../wordsheet.js';

const loading = (title) => `<div class="screen">${topBar(title)}<div class="empty"><div class="huge">📚</div><p class="muted">Đang tải từ vựng…</p></div></div>`;

/** Ensures the word list is loaded, re-rendering once it arrives. */
function needVocab(app) {
  if (app.vocab) return true;
  app.loadVocab().then(app.update).catch(() => toast('Không tải được từ vựng. Kiểm tra mạng rồi mở lại.'));
  return false;
}

const wordRow = (w, learned) => `
  <div class="card word-row">
    <div class="row center-v gap-s">
      <div class="grow">
        <div><b class="t-sub">${esc(w.w)}</b> <span class="muted small">${esc(w.ipa)}</span> ${w.pos ? `<span class="pos">${esc(w.pos)}</span>` : ''}</div>
        <div style="color:var(--a)" class="semi">${esc(w.vi)}</div>
      </div>
      ${sayBtn(w.w, { size: 38 })}
      <button class="tick ${learned ? 'on' : ''}" data-act="learn" data-w="${esc(w.w)}" aria-label="Đã thuộc">✓</button>
    </div>
    ${w.ex ? `<div class="row center-v gap-s"><div class="grow small"><div class="italic">${esc(w.ex)}</div><div class="muted">${esc(w.exVi || '')}</div></div>${sayBtn(w.ex, { size: 30 })}</div>` : ''}
  </div>`;

export function hubScreen(app) {
  let query = '';
  let typing = null;
  return {
    view() {
      if (!needVocab(app)) return loading('3000 từ vựng');
      const learned = new Set(Progress.get().learnedWords);
      const groups = app.vocab.groups;
      const all = groups.flatMap((g) => g.words);
      const known = all.filter((w) => learned.has(w.w)).length;
      const q = query.trim().toLowerCase();
      const hits = q ? all.filter((w) => w.w.toLowerCase().startsWith(q) || w.vi.toLowerCase().includes(q)).slice(0, 40) : [];
      return `<div class="screen" ${accent('sky')}>
        ${topBar('3000 từ vựng thông dụng', `${groups.length} chủ đề · ${all.length} từ`)}
        <div class="scroll pad" data-scroll="vocab">
          <div class="card tile" style="background:var(--sky-l)">
            <div class="badge" style="width:64px;height:64px;background:#fff;font-size:30px;border-radius:20px">📚</div>
            <div class="grow">
              <div class="t-title">${known} / ${all.length} từ đã thuộc</div>
              <div class="mt-xs">${bar(known / Math.max(1, all.length), 'sky', '#fff')}</div>
              <div class="muted small mt-xs">Mỗi ngày học một nhóm, thêm vào Ôn tập để nhớ lâu.</div>
            </div>
          </div>
          <input class="field" type="search" data-input="query" value="${esc(query)}" placeholder="🔎 Tìm từ tiếng Anh hoặc nghĩa tiếng Việt…" autocapitalize="off" autocorrect="off" spellcheck="false">
          <div class="hits">${q ? (hits.length ? hits.map((w) => wordRow(w, learned.has(w.w))).join('') : '<p class="muted">Không tìm thấy từ nào.</p>') : ''}</div>
          ${q ? '' : `<div class="grid2">${groups.map((g) => {
            const n = g.words.filter((w) => learned.has(w.w)).length;
            return `<button class="card tile" data-act="nav" data-to="vocab/${g.id}">
              ${badge(g.emoji, 'var(--sky-l)', 50)}
              <div class="grow"><div class="t-sub">${esc(g.title)}</div><div class="muted small">${n}/${g.words.length} từ</div><div class="mt-xs">${bar(n / g.words.length, n === g.words.length ? 'mint' : 'sky')}</div></div>
            </button>`;
          }).join('')}</div>`}
        </div>
      </div>`;
    },
    actions: {
      // Search results update in place so the field keeps focus while typing.
      query: (v) => {
        query = v;
        clearTimeout(typing);
        typing = setTimeout(() => {
          app.update();
          const f = document.querySelector('[data-input=query]');
          f?.focus();
          f?.setSelectionRange(v.length, v.length);
        }, 250);
      },
      learn: (d) => { Progress.toggleWord(d.w); },
    },
  };
}

export function groupScreen(app, id) {
  let mode = 'list';
  let cards = [], index = 0, flipped = false;
  let quiz = [], qi = 0, picked = null, correct = 0;

  const group = () => app.vocab?.groups.find((g) => g.id === id);

  function startCards() {
    const g = group();
    const learned = new Set(Progress.get().learnedWords);
    const fresh = g.words.filter((w) => !learned.has(w.w));
    cards = shuffled(fresh.length ? fresh : g.words);
    index = 0; flipped = false; mode = 'cards';
  }

  function startQuiz() {
    const g = group();
    const pool = app.vocab.groups.flatMap((x) => x.words);
    quiz = shuffled(g.words).slice(0, 10).map((w) => {
      const others = new Set();
      while (others.size < 3) { const o = pick(g.words.length > 8 ? g.words : pool); if (o.vi !== w.vi) others.add(o.vi); }
      return { word: w, options: shuffled([...others, w.vi]) };
    });
    qi = 0; picked = null; correct = 0; mode = 'quiz';
    setTimeout(() => speaker.say(quiz[0].word.w), 250);
  }

  const listView = (g) => {
    const learned = new Set(Progress.get().learnedWords);
    return `
      <div class="row gap wrap-s">
        ${bigBtn('🃏 Học thẻ', 'cards')}
        ${bigBtn('🎯 Kiểm tra', 'quiz', { color: 'mint' })}
      </div>
      ${outBtn(g.words.every((w) => Review.has(`v:${w.w}`)) ? '🔁 Cả nhóm đã có trong Ôn tập' : '🔁 Thêm cả nhóm vào Ôn tập', 'enroll')}
      ${g.words.map((w) => wordRow(w, learned.has(w.w))).join('')}`;
  };

  const cardsView = () => {
    const w = cards[index];
    const known = Progress.get().learnedWords.includes(w.w);
    return `
      ${bar((index + 1) / cards.length)}
      <div class="muted center">${index + 1} / ${cards.length} · chạm thẻ để lật</div>
      <div class="flip ${flipped ? 'flipped' : ''}" data-act="flip">
        <div class="flip-in">
          <div class="face front" style="background:var(--al)">
            <div class="word-big">${esc(w.w)}</div>
            <div class="muted ipa">${esc(w.ipa)}</div>
            <div class="mt">${sayBtn(w.w, { size: 56 })}</div>
          </div>
          <div class="face back">
            <div class="word-vi" style="color:var(--a)">${esc(w.vi)}</div>
            <div class="italic mt">“${esc(w.ex)}”</div>
            <div class="muted small">${esc(w.exVi || '')}</div>
            <div class="mt">${sayBtn(w.ex)}</div>
          </div>
        </div>
      </div>
      <div class="row gap">
        <button class="btn outline grow ${!known ? 'sel-peach' : ''}" data-act="known" data-v="0" style="color:var(--peach)">🤔 Chưa thuộc</button>
        <button class="btn outline grow ${known ? 'sel-mint' : ''}" data-act="known" data-v="1" style="color:var(--mint)">✅ Đã thuộc</button>
      </div>
      <div class="row gap">
        ${outBtn('← Trước', 'prev', { disabled: index === 0 })}
        ${index === cards.length - 1 ? bigBtn('Xong', 'list', { color: 'mint' }) : bigBtn('Tiếp →', 'next')}
      </div>`;
  };

  const quizView = () => {
    const q = quiz[qi];
    return `
      ${bar(qi / quiz.length)}
      <div class="muted small">Câu ${qi + 1}/${quiz.length} · đúng ${correct}</div>
      <div class="prompt"><div class="word-big">${esc(q.word.w)}</div><div class="muted small">${esc(q.word.ipa)}</div></div>
      <div class="row center-h">${sayBtn(q.word.w, { size: 56 })}</div>
      <div class="stack">${q.options.map((o, i) => {
        const state = picked == null ? '' : o === q.word.vi ? 'right' : i === picked ? 'wrong' : '';
        return `<button class="option ${state}" data-act="answer" data-i="${i}" ${picked != null ? 'disabled' : ''}>${esc(o)}</button>`;
      }).join('')}</div>
      ${picked != null ? `<div class="answer ${q.options[picked] === q.word.vi ? 'ok' : 'bad'}">
        <div class="answer-title">${q.options[picked] === q.word.vi ? '🎉 Chính xác!' : '😅 Chưa đúng rồi'}</div>
        <div class="italic">${esc(q.word.ex)}</div>
        ${bigBtn(qi < quiz.length - 1 ? 'Tiếp tục' : 'Xem kết quả', 'nextQ', { color: q.options[picked] === q.word.vi ? 'mint' : 'coral' })}
      </div>` : ''}`;
  };

  return {
    view() {
      if (!needVocab(app)) return loading('Từ vựng');
      const g = group();
      if (!g) return loading('Từ vựng');
      const learned = g.words.filter((w) => Progress.get().learnedWords.includes(w.w)).length;
      const body = { list: () => listView(g), cards: cardsView, quiz: quizView }[mode]();
      return `<div class="screen" ${accent('sky')}>
        ${topBar(`${g.emoji} ${g.title}`, `${learned}/${g.words.length} từ đã thuộc`)}
        <div class="scroll pad ${mode === 'cards' ? 'flash-wrap' : ''}" data-scroll="vg-${mode}">${body}</div>
      </div>`;
    },
    actions: {
      back: () => {
        if (mode !== 'list') { mode = 'list'; app.update(); } else app.back();
      },
      list: () => { mode = 'list'; },
      cards: startCards,
      quiz: startQuiz,
      learn: (d) => { Progress.toggleWord(d.w); },
      enroll: () => {
        const n = Review.add(group().words.map(wordCard));
        toast(n ? `Đã thêm ${n} từ vào Ôn tập hôm nay 🔁` : 'Cả nhóm đã có trong Ôn tập rồi.');
      },
      flip: (d, el) => { flipped = !flipped; el.classList.toggle('flipped', flipped); return false; },
      known: (d) => {
        const w = cards[index].w;
        if (Progress.get().learnedWords.includes(w) !== (d.v === '1')) Progress.toggleWord(w);
      },
      prev: () => { index--; flipped = false; },
      next: () => { index++; flipped = false; },
      answer: (d) => {
        picked = +d.i;
        if (quiz[qi].options[picked] === quiz[qi].word.vi) correct++;
      },
      nextQ: () => {
        if (qi < quiz.length - 1) {
          qi++; picked = null;
          setTimeout(() => speaker.say(quiz[qi].word.w), 200);
          return;
        }
        Progress.addXp(correct);
        completeDialog({
          title: correct === quiz.length ? 'Hoàn hảo! 🏆' : 'Làm tốt lắm!',
          message: `Bạn trả lời đúng ${correct}/${quiz.length} từ.`,
          xp: correct,
          onDismiss: () => { mode = 'list'; app.update(); },
          onAgain: () => { startQuiz(); app.update(); },
        });
        return false;
      },
    },
  };
}
