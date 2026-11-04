// One topic's 4-step path: phrases & words, dialogue, speaking, and (in games.js) the review game.
import { ACTIVITIES, QUEST, compare, isDone, isUserLine } from '../logic.js';
import { listen, speaker } from '../speech.js';
import { Progress, Review } from '../store.js';
import {
  accent, badge, bar, bigBtn, catColors, completeDialog, esc, micBtn, outBtn, pill, sayBtn, scored, section, sleep, topBar,
} from '../ui.js';

const STEP_INFO = {
  PHRASES: 'Nghe mẫu câu thông dụng & lật thẻ từ vựng',
  DIALOGUE: 'Nghe đoạn hội thoại, rồi tự nói vai của bạn',
  SPEAK: 'Nói từng câu, app chấm điểm phát âm',
  GAME: 'Ghép từ, xếp câu, nghe chọn đáp án',
};
const STEP_PATH = { PHRASES: 'phrases', DIALOGUE: 'dialogue', SPEAK: 'speak' };

export function topicScreen(app, id) {
  const topic = app.topic(id);
  if (!topic) return null;
  const cat = app.category(topic.category);
  const [strong, light] = catColors(topic.category);
  return {
    view() {
      const p = Progress.get();
      return `<div class="screen" ${accent(strong, light)}>
        ${topBar(topic.title, topic.titleVi)}
        <div class="scroll pad" data-scroll="topic">
          <div class="hero" style="background:var(--al)">
            <div class="row gap-s">${pill(`${cat?.emoji} ${cat?.name}`, strong, '#fff')}${pill(`Trình độ ${topic.level}`, strong, '#fff')}</div>
            <div class="hero-emoji">${topic.emoji}</div>
            <div class="muted">${topic.phrases.length} mẫu câu · ${topic.words.length} từ vựng · ${topic.dialogue.lines.length} lượt hội thoại</div>
          </div>
          ${section('Lộ trình học')}
          <p class="muted">Làm lần lượt 4 bước: học mẫu câu → nghe & nhập vai hội thoại → luyện nói → chơi game ôn tập.</p>
          <div class="grid2">
          ${ACTIVITIES.map((a, i) => {
            const done = isDone(p, topic.id, a.id);
            const to = a.id === 'GAME' ? `game/${topic.id}/mix` : `topic/${topic.id}/${STEP_PATH[a.id]}`;
            return `<button class="card tile ${done ? 'done' : ''}" data-act="nav" data-to="${to}">
              ${badge(a.emoji, 'var(--al)', 52)}
              <div class="grow">
                <div class="tiny bold" style="color:var(--a)">Bước ${i + 1}</div>
                <div class="t-sub">${esc(a.label)}</div>
                <div class="muted small">${esc(STEP_INFO[a.id])}</div>
              </div>
              ${done ? '<span class="check">✓</span>' : `<div class="center muted"><div>›</div><div class="tiny">+${a.xp}</div></div>`}
            </button>`;
          }).join('')}
          </div>
        </div>
      </div>`;
    },
  };
}

// ---- Step 1: phrases, native expressions, flashcards -----------------------

export function phrasesScreen(app, id) {
  const topic = app.topic(id);
  if (!topic) return null;
  const [strong, light] = catColors(topic.category);
  let tab = 0;
  let index = 0;
  let flipped = false;

  const phraseList = () => `
    <p class="muted">Chạm 🔊 để nghe, 🐢 để nghe chậm. Đọc to theo nhé!</p>
    <div class="grid2">
    ${topic.phrases.map((p, i) => `
      <div class="card">
        <div class="row top gap-s">
          <b style="color:var(--a)">${i + 1}</b>
          <div class="grow"><div class="t-sub">${esc(p.en)}</div><div class="muted">${esc(p.vi)}</div></div>
          ${sayBtn(p.en)}${sayBtn(p.en, { slow: true })}
        </div>
        ${p.tip ? `<div class="tip" style="background:var(--sun-l)">💡 ${esc(p.tip)}</div>` : ''}
      </div>`).join('')}
    </div>
    ${bigBtn('Tiếp: Nói như người bản xứ →', 'tab', { data: 'data-tab="1"' })}`;

  /** Textbook English next to what natives actually say, so learners stop sounding translated. */
  const nativeList = () => `
    <p class="muted">Câu “sách vở” nghe vẫn đúng nhưng cứng. Đây là cách người bản xứ nói hằng ngày:</p>
    <div class="grid2">
    ${(topic.native || []).map((n) => `
      <div class="card">
        <div class="muted small strike">📖 ${esc(n.plain)}</div>
        <div class="row center-v gap-s mt-s">
          <div class="grow"><div class="t-sub" style="color:var(--a)">🗣️ ${esc(n.natural)}</div><div>${esc(n.vi)}</div></div>
          ${sayBtn(n.natural)}${sayBtn(n.natural, { slow: true })}
        </div>
        ${n.note ? `<div class="tip" style="background:var(--al)">💡 ${esc(n.note)}</div>` : ''}
      </div>`).join('')}
    </div>
    ${bigBtn('Tiếp: Thẻ từ vựng →', 'tab', { data: 'data-tab="2"' })}`;

  const flashcards = () => {
    const w = topic.words[index];
    const known = Progress.get().learnedWords.includes(w.word);
    const last = index === topic.words.length - 1;
    return `
      ${bar((index + 1) / topic.words.length)}
      <div class="muted center mt-s">${index + 1} / ${topic.words.length} · chạm thẻ để lật</div>
      <div class="flip ${flipped ? 'flipped' : ''}" data-act="flip">
        <div class="flip-in">
          <div class="face front" style="background:var(--al)">
            <div class="word-big">${esc(w.word)}</div>
            <div class="muted ipa">${esc(w.ipa)}</div>
            <div class="mt">${sayBtn(w.word, { size: 56 })}</div>
          </div>
          <div class="face back">
            <div class="word-vi" style="color:var(--a)">${esc(w.vi)}</div>
            <div class="italic mt">“${esc(w.example)}”</div>
            <div class="mt">${sayBtn(w.example)}</div>
          </div>
        </div>
      </div>
      <div class="row gap">
        <button class="btn outline grow ${!known ? 'sel-peach' : ''}" data-act="known" data-v="0" style="color:var(--peach)">🤔 Chưa thuộc</button>
        <button class="btn outline grow ${known ? 'sel-mint' : ''}" data-act="known" data-v="1" style="color:var(--mint)">✅ Đã thuộc</button>
      </div>
      <div class="row gap mt">
        ${outBtn('← Trước', 'prev', { disabled: index === 0 })}
        ${last ? bigBtn('Hoàn thành', 'finish', { color: 'mint' }) : bigBtn('Tiếp →', 'next')}
      </div>`;
  };

  return {
    view() {
      const tabs = ['💬 Mẫu câu', '🗣️ Bản xứ', '🃏 Từ vựng'];
      return `<div class="screen" ${accent(strong, light)}>
        ${topBar('Cụm từ & từ vựng', topic.title)}
        <div class="seg">${tabs.map((l, i) => `<button class="${tab === i ? 'on' : ''}" data-act="tab" data-tab="${i}">${l}</button>`).join('')}</div>
        <div class="scroll pad ${tab === 2 ? 'flash-wrap' : ''}" data-scroll="phrases-${tab}">
          ${[phraseList, nativeList, flashcards][tab]()}
        </div>
      </div>`;
    },
    actions: {
      tab: (d) => { tab = +d.tab; },
      // Flip in place so the CSS transition runs.
      flip: (d, el) => { flipped = !flipped; el.classList.toggle('flipped', flipped); return false; },
      known: (d) => {
        const w = topic.words[index].word;
        const known = Progress.get().learnedWords.includes(w);
        if (known !== (d.v === '1')) Progress.toggleWord(w);
      },
      prev: () => { if (index > 0) index--; flipped = false; },
      next: () => { index++; flipped = false; },
      finish: () => {
        const xp = Progress.complete(topic.id, 'PHRASES');
        Review.enroll(topic);
        completeDialog({
          title: 'Hoàn thành bài học!',
          message: `Bạn đã học ${topic.phrases.length} mẫu câu và ${topic.words.length} từ mới. Các thẻ đã được thêm vào Ôn tập hôm nay 🔁`,
          xp,
          onDismiss: app.back,
        });
        return false;
      },
    },
  };
}

// ---- Step 2: dialogue (listen / role-play / reflex) -------------------------

const MODES = [
  ['LISTEN', '🎧 Nghe'],
  ['ROLEPLAY', '🎭 Nhập vai'],
  // No English on screen: understand by ear, answer from the Vietnamese cue, mic opens by itself.
  ['REFLEX', '⚡ Phản xạ'],
];

export function dialogueScreen(app, id) {
  const topic = app.topic(id);
  if (!topic) return null;
  const lines = topic.dialogue.lines;
  const [strong, light] = catColors(topic.category);
  let mode = 'LISTEN';
  let showVi = true;
  let playingAll = false;
  let turn = 0;
  let scores = {};
  let revealed = new Set();
  // Bumped whenever the flow restarts, so stale async loops stop.
  let token = 0;

  const reset = () => { token++; speaker.stop(); playingAll = false; turn = 0; scores = {}; revealed = new Set(); };

  const scrollToTurn = () =>
    requestAnimationFrame(() => document.querySelector(`[data-line="${turn}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }));

  async function playAll() {
    const my = ++token;
    playingAll = true;
    for (let i = 0; i < lines.length; i++) {
      turn = i;
      app.update();
      scrollToTurn();
      await speaker.sayAndWait(lines[i].en);
      if (my !== token) return;
      await sleep(350);
      if (my !== token) return;
    }
    Progress.count(QUEST.LISTEN);
    playingAll = false;
    app.update();
  }

  // Role-play and reflex: the app voices the other person, then waits for the learner.
  async function runTurn() {
    const my = ++token;
    app.update();
    scrollToTurn();
    if (mode === 'LISTEN' || turn >= lines.length) return;
    if (!isUserLine(lines[turn])) {
      await sleep(300);
      if (my !== token) return;
      await speaker.sayAndWait(lines[turn].en);
      if (my !== token) return;
      turn++;
      runTurn();
    } else if (mode === 'REFLEX' && !scores[turn]) {
      // Answer straight away, like a real conversation.
      await sleep(400);
      if (my === token) speak();
    }
  }

  function speak() {
    const i = turn;
    listen(mode === 'REFLEX' ? lines[i].vi : lines[i].en, {
      onResult: (alts) => {
        Progress.count(QUEST.SPEAK);
        scores[i] = compare(lines[i].en, alts[0]);
        app.update();
      },
    });
  }

  const bubble = (line, i) => {
    const mine = isUserLine(line);
    const result = scores[i];
    const awaiting = mode !== 'LISTEN' && i === turn && mine;
    const hidden = mode === 'REFLEX' && !revealed.has(i) && !result;
    let text;
    if (hidden && mine)
      text = `<div>🇻🇳 ${esc(line.vi)}</div><button class="link tiny" data-act="reveal" data-i="${i}">Nói câu này bằng tiếng Anh · chạm để xem đáp án</button>`;
    else
      text = `<div class="row center-v gap-s">
        ${result ? scored(result, 'grow') : hidden ? `<button class="link grow muted" data-act="reveal" data-i="${i}">🎧 • • •  chạm để xem chữ</button>` : `<div class="grow">${esc(line.en)}</div>`}
        ${sayBtn(line.en, { size: 34 })}
      </div>`;
    return `<div class="bubble-row ${mine ? 'mine' : ''}" data-line="${i}">
      <div class="who">${esc(mine ? topic.dialogue.roleB : topic.dialogue.roleA)}</div>
      <div class="bubble ${awaiting ? 'awaiting' : ''}" data-hl="${esc(line.en)}">
        ${text}
        ${showVi && !hidden ? `<div class="muted small mt-xs">${esc(line.vi)}</div>` : ''}
        ${result ? `<div class="tiny mt-xs">${'⭐'.repeat(result.stars)}  ${result.score}% · ${esc(result.feedback)}</div>`
          : awaiting ? '<div class="bold mt-xs" style="color:var(--coral)">🎤 Đến lượt bạn!</div>' : ''}
      </div>
    </div>`;
  };

  const controls = () => {
    if (mode === 'LISTEN')
      return `${bigBtn(playingAll ? '⏸ Dừng' : '▶ Phát cả đoạn hội thoại', 'playAll')}
        <div class="mt-s">${outBtn('Sẵn sàng? Thử nhập vai 🎭', 'mode', { data: 'data-m="ROLEPLAY"' })}</div>`;
    if (turn >= lines.length) return bigBtn('Hoàn thành 🎉', 'finish', { color: 'mint' });
    if (isUserLine(lines[turn]))
      return `<div class="row center-v center-h gap-l">
        ${outBtn('Bỏ qua', 'skip')}${micBtn('speak')}${outBtn('Tiếp →', 'skip', { disabled: !scores[turn] })}
      </div>`;
    return `<div class="muted center pad-v">${esc(topic.dialogue.roleA)} đang nói… 🎧</div>`;
  };

  return {
    view() {
      const visible = mode === 'LISTEN' ? lines.length : Math.min(turn + 1, lines.length);
      const hint = {
        LISTEN: `Nghe ${topic.dialogue.roleA} nói chuyện với bạn. Chạm từng câu để nghe lại.`,
        ROLEPLAY: `Bạn là “${topic.dialogue.roleB}”. Khi đến lượt, chạm 🎤 và nói câu của bạn.`,
        REFLEX: `Không có chữ tiếng Anh! Nghe ${topic.dialogue.roleA} nói, nhìn gợi ý tiếng Việt và trả lời ngay.`,
      }[mode];
      return `<div class="screen" ${accent(strong, light)}>
        ${topBar('Hội thoại', topic.title, `<button class="chip-toggle ${showVi ? 'on' : ''}" data-act="vi">${showVi ? 'VI ✓' : 'VI'}</button>`)}
        <div class="seg">${MODES.map(([m, l]) => `<button class="${mode === m ? 'on' : ''}" data-act="mode" data-m="${m}">${l}</button>`).join('')}</div>
        <p class="muted pad-x">${esc(hint)}</p>
        <div class="scroll pad-x chat" data-scroll="dialogue">${lines.slice(0, visible).map(bubble).join('')}</div>
        <div class="bottom">${controls()}</div>
      </div>`;
    },
    leave: () => { token++; },
    actions: {
      vi: () => { showVi = !showVi; },
      mode: (d) => {
        reset();
        mode = d.m;
        if (mode !== 'LISTEN') { runTurn(); return false; }
      },
      playAll: () => {
        if (playingAll) { token++; speaker.stop(); playingAll = false; }
        else { playAll(); return false; }
      },
      reveal: (d) => { revealed.add(+d.i); },
      speak: () => { speak(); return false; },
      skip: () => { turn++; runTurn(); return false; },
      finish: () => {
        const stars = Object.values(scores).reduce((s, r) => s + r.stars, 0);
        const xp = Progress.complete(topic.id, 'DIALOGUE', stars);
        completeDialog({
          title: mode === 'REFLEX' ? 'Phản xạ cực nhanh! ⚡' : 'Nhập vai xuất sắc!',
          message: `Bạn đã nói ${Object.keys(scores).length}/${lines.filter(isUserLine).length} câu trong vai ${topic.dialogue.roleB}.`,
          xp,
          onDismiss: app.back,
          onAgain: () => { reset(); runTurn(); },
        });
        return false;
      },
    },
  };
}

// ---- Step 3: shadowing ------------------------------------------------------

/** Hear a phrase, say it, get a word-by-word score. */
export function speakScreen(app, id) {
  const topic = app.topic(id);
  if (!topic) return null;
  const [strong, light] = catColors(topic.category);
  // Phrases, then native expressions, then the learner's own dialogue lines.
  const seen = new Set();
  const sentences = [
    ...topic.phrases.map((p) => [p.en, p.vi]),
    ...(topic.native || []).map((n) => [n.natural, n.vi]),
    ...topic.dialogue.lines.filter(isUserLine).map((l) => [l.en, l.vi]),
  ].filter(([en]) => (seen.has(en) ? false : seen.add(en)));
  let index = 0;
  const best = {};
  let last = null;
  let heardText = '';

  return {
    view() {
      const [en, vi] = sentences[index];
      const r = last;
      const isLast = index === sentences.length - 1;
      return `<div class="screen" ${accent(strong, light)}>
        ${topBar('Luyện nói', topic.title)}
        <div class="scroll pad center" data-scroll="speak">
          ${bar((index + 1) / sentences.length)}
          <div class="muted mt-s">Câu ${index + 1} / ${sentences.length}</div>
          <div class="big-card" style="background:var(--al)">
            ${r ? scored(r, 'h-sm') : `<div class="h-sm">${esc(en)}</div>`}
            <div class="muted mt-s">${esc(vi)}</div>
            <div class="row center-h gap mt">${sayBtn(en, { size: 48 })}${sayBtn(en, { size: 48, slow: true })}</div>
          </div>
          ${r ? `<div class="stars">${'⭐'.repeat(r.stars) || '☆'}</div>
              <div class="h-md" style="color:var(--${r.stars >= 2 ? 'mint' : 'a'})">${r.score}%</div>
              <div>${esc(r.feedback)}</div>
              <div class="muted small mt-xs">Máy nghe được: “${esc(heardText)}”</div>`
            : `<p class="muted">Nghe mẫu 🔊 rồi chạm 🎤 và đọc to câu trên.<br>Từ xanh = nói đúng, từ đỏ = cần luyện thêm.</p>`}
        </div>
        <div class="bottom center">
          ${micBtn('speak', 80)}
          <div class="row gap mt">
            ${outBtn('← Trước', 'prev', { disabled: index === 0 })}
            ${isLast ? bigBtn('Hoàn thành', 'finish', { color: 'mint' }) : bigBtn(last ? 'Tiếp →' : 'Bỏ qua →', 'next')}
          </div>
        </div>
      </div>`;
    },
    actions: {
      speak: () => {
        const i = index;
        const en = sentences[i][0];
        listen(en, {
          onResult: (alts) => {
            Progress.count(QUEST.SPEAK);
            const r = compare(en, alts[0]);
            if (i !== index) return;
            last = r;
            heardText = alts[0];
            if ((best[i]?.score ?? -1) < r.score) best[i] = r;
            app.update();
          },
        });
        return false;
      },
      prev: () => { index--; last = null; },
      next: () => { index++; last = null; },
      finish: () => {
        const all = Object.values(best);
        const xp = Progress.complete(topic.id, 'SPEAK', Math.floor(all.reduce((s, r) => s + r.stars, 0) / 2));
        const avg = all.length ? Math.floor(all.reduce((s, r) => s + r.score, 0) / all.length) : 0;
        completeDialog({
          title: 'Bạn nói hay lắm!',
          message: `Đã luyện ${all.length}/${sentences.length} câu · điểm trung bình ${avg}%.`,
          xp,
          onDismiss: app.back,
        });
        return false;
      },
    },
  };
}
