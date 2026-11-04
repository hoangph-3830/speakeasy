import { GRADES, QUEST, preview, shuffled } from '../logic.js';
import { speaker } from '../speech.js';
import { Progress, Review } from '../store.js';
import { accent, bar, bigBtn, completeDialog, esc, sayBtn, sleep, topBar } from '../ui.js';

/** Daily spaced-repetition session over everything learned so far. */
export function reviewScreen(app) {
  // Ids still to show this session; a forgotten card goes back to the end.
  const queue = shuffled(Review.due()).slice(0, 30).map((c) => c.id);
  const total = queue.length;
  let done = 0;
  let flipped = false;
  let listenOnly = false;

  const card = () => Review.all().find((c) => c.id === queue[0]);
  const autoSay = () => {
    const c = card();
    if (c && !flipped) sleep(250).then(() => card() === c && !flipped && speaker.say(c.front));
  };

  return {
    enter: autoSay,
    view() {
      const c = card();
      const trailing = `<button class="chip-toggle ${listenOnly ? 'on' : ''}" data-act="listenOnly">${listenOnly ? '🎧 Chỉ nghe ✓' : '🎧 Chỉ nghe'}</button>`;
      const head = topBar('Ôn tập hôm nay', 'Lặp lại ngắt quãng · nhớ lâu hơn', trailing);
      if (!c)
        return `<div class="screen" ${accent('lavender')}>${head}
          <div class="empty">
            <div class="huge">${total === 0 ? '🌤️' : '🏁'}</div>
            <div class="t-title">${total === 0 ? 'Chưa có thẻ nào cần ôn' : 'Xong phần ôn hôm nay!'}</div>
            <p class="muted">${total === 0 ? 'Học xong bước “Cụm từ & từ vựng” của một chủ đề để thêm thẻ vào bộ ôn tập.' : 'Mai quay lại nhé, app sẽ nhắc đúng lúc bạn sắp quên.'}</p>
            ${bigBtn('Về trang chủ', 'back')}
          </div></div>`;
      return `<div class="screen" ${accent('lavender')}>${head}
        <div class="pad-x">${bar(done / Math.max(1, total))}<div class="muted small mt-xs">Còn ${queue.length} thẻ · đã ôn ${done}</div></div>
        <button class="review-card" data-act="flip">
          ${listenOnly && !flipped ? '<div class="huge">🎧</div><div class="muted">Nghe và tự nhắc lại, rồi lật thẻ</div>' : `<div class="h-sm">${esc(c.front)}</div>`}
          <div class="mt">${sayBtn(c.front, { size: 56 })}</div>
          ${flipped ? `<div class="word-vi mt" style="color:var(--lavender)">${esc(c.back)}</div>${c.extra ? `<div class="muted mt-s">${esc(c.extra)}</div>` : ''}` : ''}
        </button>
        <div class="bottom">
          ${!flipped ? bigBtn('Lật thẻ', 'flip') : `
            <div class="muted small">Bạn nhớ câu này thế nào?</div>
            <div class="row gap-s mt-xs">${GRADES.map((g) => `
              <button class="btn outline grade grow" style="--g:${g.color}" data-act="grade" data-g="${g.id}">
                <b>${g.emoji} ${g.label}</b><span class="tiny muted">${preview(c, g.id)}</span>
              </button>`).join('')}
            </div>`}
        </div>
      </div>`;
    },
    actions: {
      listenOnly: () => { listenOnly = !listenOnly; },
      flip: () => { flipped = true; },
      grade: (d) => {
        const c = card();
        const next = Review.grade(c, d.g);
        Progress.count(QUEST.REVIEW);
        queue.shift();
        if (next.due <= Review.today()) queue.push(c.id);
        else done++;
        flipped = false;
        if (!queue.length) {
          Progress.addXp(done);
          completeDialog({ title: 'Ôn tập xong!', message: `Bạn đã ôn ${done} thẻ. Trí nhớ dài hạn +1 💪`, xp: done });
        } else autoSay();
      },
    },
  };
}
