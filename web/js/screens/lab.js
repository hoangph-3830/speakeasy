// ELSA-style pronunciation lab: tip → hear minimal pairs → say words → say sentences.
import { QUEST, bestSentenceScore, judgeWord, percent, pick, speakingScore } from '../logic.js';
import { listen, speaker } from '../speech.js';
import { Progress } from '../store.js';
import { accent, badge, bar, bigBtn, completeDialog, esc, micBtn, ring, sayBtn, scored, topBar } from '../ui.js';

/** Colour for a 0-100 pronunciation score: green / amber / red. */
const scoreColor = (s) => (s >= 80 ? 'mint' : s >= 50 ? 'peach' : 'coral');

export function hubScreen(app) {
  const lessons = app.pronunciation.lessons;
  return {
    view() {
      const p = Progress.get();
      const score = speakingScore(p);
      return `<div class="screen" ${accent('lavender')}>
        ${topBar('Phòng phát âm', 'Sửa đúng những âm người Việt hay sai')}
        <div class="scroll pad" data-scroll="lab">
          <div class="card tile" style="background:var(--lavender-l)">
            ${ring((score ?? 0) / 100, 76, `var(--${score == null ? 'lavender' : scoreColor(score)})`, `<b class="h-sm">${score ?? '–'}</b>`)}
            <div class="grow">
              <div class="t-title">Điểm phát âm</div>
              <div class="muted small">${score == null ? 'Làm một bài để biết điểm của bạn.' : `Trung bình ${Object.keys(p.soundScores).length}/${lessons.length} bài. Luyện âm có điểm đỏ trước nhé!`}</div>
            </div>
          </div>
          <div class="row gap">
            <button class="card quick" style="background:var(--lavender-l)" data-act="nav" data-to="lab/ipa">🔤 Bảng phiên âm IPA</button>
            <button class="card quick" style="background:var(--peach-l)" data-act="nav" data-to="lab/twisters">👅 Câu líu lưỡi</button>
          </div>
          <div class="grid2">
          ${lessons.map((l) => {
            const s = p.soundScores[l.id];
            const sub = l.pairs?.length
              ? `${l.ipa} · ${l.pairs.map((x) => `${x[0]}/${x[1]}`).join(', ').slice(0, 34)}…`
              : `${l.sentences.length} câu nói nhanh như người bản xứ`;
            return `<button class="card tile" data-act="nav" data-to="lab/${l.id}">
              ${badge(l.emoji, 'var(--lavender-l)', 50)}
              <div class="grow">
                <div class="t-sub">${esc(l.title)}</div>
                <div class="muted small one-line">${esc(sub)}</div>
                ${s != null ? `<div class="mt-s">${bar(s / 100, scoreColor(s))}</div>` : ''}
              </div>
              <b style="color:var(--${s != null ? scoreColor(s) : 'lavender'})">${s ?? 'Mới'}</b>
            </button>`;
          }).join('')}
          </div>
        </div>
      </div>`;
    },
  };
}

export function lessonScreen(app, id) {
  const lesson = app.pronunciation.lessons.find((l) => l.id === id);
  if (!lesson) return null;
  const pairs = lesson.pairs || [];
  // Linking and stress lessons have no minimal pairs, so they skip straight to sentences.
  const steps = pairs.length ? ['TIP', 'LISTEN', 'WORDS', 'SENTENCES'] : ['TIP', 'SENTENCES'];
  let step = 0;
  // Score parts, each 0-100; averaged when the lesson ends.
  const parts = [];

  // LISTEN
  const rounds = pairs.length ? Array.from({ length: 6 }, () => [pick(pairs), Math.floor(Math.random() * 2)]) : [];
  let li = 0, lCorrect = 0, lPicked = null;
  // WORDS
  let wi = 0, wClear = 0, verdict = null, gotIt = false;
  // SENTENCES
  let si = 0, result = null;
  const best = {};

  function next(partScore) {
    if (partScore != null) parts.push(partScore);
    if (step < steps.length - 1) {
      step++;
      if (steps[step] === 'LISTEN') sayListen();
      return;
    }
    const score = parts.length ? Math.floor(parts.reduce((a, b) => a + b, 0) / parts.length) : 0;
    Progress.recordSound(lesson.id, score);
    const xp = 10 + Math.floor(score / 10);
    Progress.addXp(xp);
    completeDialog({
      title: score >= 80 ? 'Phát âm chuẩn lắm! 🎯' : 'Tiến bộ rồi đó!',
      message: `Điểm bài “${lesson.title}”: ${score}/100. Luyện lại mỗi ngày để giữ điểm xanh.`,
      xp,
      onDismiss: app.back,
    });
  }

  const sayListen = () => { const [pair, ans] = rounds[li]; speaker.say(pair[ans]); };

  const tipView = () => `
    <div class="big-card" style="background:var(--lavender-l)">
      <div class="huge">${lesson.emoji}</div><div class="h-md" style="color:var(--lavender)">${esc(lesson.ipa)}</div>
    </div>
    <h3>👄 Cách đặt miệng</h3>
    <p>${esc(lesson.tip)}</p>
    ${pairs.length ? `<h3>Nghe sự khác nhau</h3>${pairs.slice(0, 3).map(([a, b]) => `
      <div class="row center-v gap pair">
        <b class="grow" style="color:var(--mint)">${esc(a)}</b>${sayBtn(a)}
        <span class="grow muted">${esc(b)}</span>${sayBtn(b)}
      </div>`).join('')}` : ''}
    ${bigBtn('Bắt đầu luyện →', 'tipNext')}`;

  /** Minimal-pair discrimination: hear one of two look-alike words, pick which. */
  const listenView = () => {
    const [pair, answer] = rounds[li];
    return `<div class="center">
      <h3>🎧 Bạn nghe thấy từ nào?</h3>
      <div class="muted">Câu ${li + 1}/${rounds.length}</div>
      <div class="row center-h gap mt">${sayBtn(pair[answer], { size: 64 })}${sayBtn(pair[answer], { size: 64, slow: true })}</div>
      <div class="row gap mt">${pair.map((w, k) => {
        const state = lPicked == null ? '' : k === answer ? 'right' : k === lPicked ? 'wrong' : '';
        return `<button class="option pair-opt grow ${state}" data-act="lPick" data-k="${k}" ${lPicked != null ? 'disabled' : ''}>${esc(w)}</button>`;
      }).join('')}</div>
      ${lPicked != null ? `
        <p class="bold" style="color:var(--${lPicked === answer ? 'mint' : 'coral'})">${lPicked === answer ? '🎉 Đúng rồi! Tai bạn tinh lắm.' : `😅 Đó là “${esc(pair[answer])}”. Nghe lại cả hai từ nhé.`}</p>
        ${bigBtn(li < rounds.length - 1 ? 'Tiếp →' : 'Sang phần nói →', 'lNext')}` : ''}
    </div>`;
  };

  /** Say the target word of each pair; the recognizer acts as the native listener. */
  const wordsView = () => {
    const [target, lookAlike] = pairs[wi];
    const msg = !verdict ? '<p class="muted">Nghe mẫu rồi chạm 🎤 và nói rõ từ này.</p>'
      : verdict.kind === 'clear' ? `<p class="bold" style="color:var(--mint)">✅ Chuẩn! Người bản xứ sẽ nghe đúng “${esc(target)}”.</p>`
      : verdict.kind === 'confused' ? `<p class="bold" style="color:var(--coral)">❌ Nghe thành “${esc(verdict.heardAs)}” rồi. Xem lại mẹo đặt miệng và thử lại.</p>`
      : `<p class="bold" style="color:var(--peach)">🤔 Máy nghe được “${esc(verdict.heard)}”. Nói chậm và rõ hơn nhé.</p>`;
    const lastWord = wi === pairs.length - 1;
    return `<div class="center">
      <h3>🎤 Nói từ này</h3>
      <div class="muted">Từ ${wi + 1}/${pairs.length}</div>
      <div class="big-card" style="background:var(--lavender-l)">
        <div class="word-big">${esc(target)}</div>
        <div class="muted">đừng để thành “${esc(lookAlike)}”</div>
        <div class="row center-h gap mt">${sayBtn(target, { size: 52 })}${sayBtn(target, { size: 52, slow: true })}</div>
      </div>
      ${micBtn('wSpeak', 80)}
      ${msg}
      ${bigBtn(!lastWord && !verdict ? 'Bỏ qua →' : !lastWord ? 'Tiếp →' : 'Sang phần câu →', 'wNext')}
    </div>`;
  };

  const sentencesView = () => {
    const s = lesson.sentences[si];
    const last = si === lesson.sentences.length - 1;
    return `<div class="center">
      <h3>🗣️ Nói cả câu</h3>
      <div class="muted">Câu ${si + 1}/${lesson.sentences.length}</div>
      <div class="big-card" style="background:var(--lavender-l)">
        ${result ? scored(result, 'h-sm') : `<div class="h-sm">${esc(s.en)}</div>`}
        ${s.sounds ? `<div class="sounds">🔊 Nghe như: ${esc(s.sounds)}</div>` : ''}
        <div class="muted mt-s">${esc(s.vi)}</div>
        <div class="row center-h gap mt">${sayBtn(s.en, { size: 52 })}${sayBtn(s.en, { size: 52, slow: true })}</div>
      </div>
      ${micBtn('sSpeak', 80)}
      ${result ? `<div class="h-md" style="color:var(--${scoreColor(result.score)})">${result.score}</div><div>${esc(result.feedback)}</div>`
        : '<p class="muted">Nghe mẫu, chú ý cách nối âm và nhấn giọng, rồi nói theo.</p>'}
      ${bigBtn(!last ? (result ? 'Tiếp →' : 'Bỏ qua →') : 'Hoàn thành', 'sNext', { color: last ? 'mint' : 'lavender' })}
    </div>`;
  };

  return {
    view: () => `<div class="screen" ${accent('lavender')}>
      ${topBar(lesson.title, `Bước ${step + 1}/${steps.length}`)}
      <div class="pad-x">${bar((step + 1) / steps.length)}</div>
      <div class="scroll pad" data-scroll="lab-${step}">
        ${{ TIP: tipView, LISTEN: listenView, WORDS: wordsView, SENTENCES: sentencesView }[steps[step]]()}
      </div>
    </div>`,
    actions: {
      tipNext: () => next(null),
      lPick: (d) => {
        lPicked = +d.k;
        if (lPicked === rounds[li][1]) lCorrect++;
      },
      lNext: () => {
        if (li < rounds.length - 1) { li++; lPicked = null; sayListen(); return; }
        Progress.count(QUEST.LISTEN);
        next(percent(lCorrect, rounds.length));
      },
      wSpeak: () => {
        const [target, lookAlike] = pairs[wi];
        listen(`Say: ${target}`, {
          onResult: (alts) => {
            Progress.count(QUEST.SPEAK);
            verdict = judgeWord(target, lookAlike, alts);
            if (verdict.kind === 'clear' && !gotIt) { gotIt = true; wClear++; }
            app.update();
          },
        });
        return false;
      },
      wNext: () => {
        if (wi < pairs.length - 1) { wi++; verdict = null; gotIt = false; return; }
        next(percent(wClear, pairs.length));
      },
      sSpeak: () => {
        const i = si;
        const s = lesson.sentences[i];
        listen(s.en, {
          onResult: (alts) => {
            Progress.count(QUEST.SPEAK);
            const r = bestSentenceScore(s.en, alts);
            if (i !== si) return;
            result = r;
            if ((best[i] ?? -1) < r.score) best[i] = r.score;
            app.update();
          },
        });
        return false;
      },
      sNext: () => {
        if (si < lesson.sentences.length - 1) { si++; result = null; return; }
        const n = lesson.sentences.length;
        next(Math.floor(lesson.sentences.reduce((sum, _, k) => sum + (best[k] ?? 0), 0) / n));
      },
    },
  };
}
