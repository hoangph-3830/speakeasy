import { ACTIVITIES, doneCount, level } from '../logic.js';
import { speaker, toast } from '../speech.js';
import { AiSettings, Progress, Review, exportBackup, importBackup } from '../store.js';
import { bar, catColors, esc, openDialog, sayBtn, section } from '../ui.js';

const BADGES = [
  ['🌱', 'Khởi đầu', 'Hoàn thành 1 bài', (p) => p.done.length > 0],
  ['🔥', 'Chăm chỉ', 'Chuỗi 3 ngày', (p) => p.bestStreak >= 3],
  ['🗓️', 'Một tuần', 'Chuỗi 7 ngày', (p) => p.bestStreak >= 7],
  ['📚', 'Mọt từ', 'Thuộc 30 từ', (p) => p.learnedWords.length >= 30],
  ['🎤', 'Mạnh dạn', 'Luyện nói 5 chủ đề', (p) => p.done.filter((d) => d.endsWith(':SPEAK')).length >= 5],
  ['⭐', 'Toàn diện', 'Xong trọn 1 chủ đề', (_, full) => full >= 1],
  ['✈️', 'Sẵn sàng đi xa', 'Xong 4 chủ đề', (_, full) => full >= 4],
  ['🏆', 'Bậc thầy', 'Đạt 500 XP', (p) => p.xp >= 500],
];

export function progressScreen(app) {
  const stat = (emoji, value, label, bg) =>
    `<div class="stat" style="background:var(--${bg})"><div class="emo">${emoji}</div><b>${value}</b><div class="muted tiny">${label}</div></div>`;

  return {
    view() {
      const p = Progress.get();
      const full = app.content.topics.filter((t) => doneCount(p, t.id) === ACTIVITIES.length).length;
      const key = AiSettings.get().apiKey;
      return `<div class="screen"><div class="scroll pad" data-scroll="me">
        <h1>Tiến độ của ${esc(p.name || 'bạn')} 📈</h1>
        <div class="row gap">${stat('🔥', p.streak, 'ngày liên tiếp', 'peach-l')}${stat('⚡', p.xp, 'tổng XP', 'sky-l')}${stat('📖', p.learnedWords.length, 'từ đã thuộc', 'mint-l')}</div>
        <div class="card" style="background:var(--lavender-l)">
          <div class="row center-v"><div class="t-title grow" style="color:var(--lavender)">Cấp ${level(p)}</div><span class="muted">${p.xp % 100}/100 XP</span></div>
          <div class="mt-s">${bar((p.xp % 100) / 100, 'lavender', '#fff')}</div>
        </div>

        ${section('Theo chủ đề')}
        <div class="card">${app.content.categories.map((c) => {
          const topics = app.content.topics.filter((t) => t.category === c.id);
          const done = topics.reduce((s, t) => s + doneCount(p, t.id), 0);
          const total = topics.length * ACTIVITIES.length;
          return `<div class="row center-v gap-s cat-row"><span class="emo">${c.emoji}</span>
            <div class="grow"><div class="row"><span class="semi grow">${esc(c.name)}</span><span class="muted">${done}/${total}</span></div>${bar(done / total, catColors(c.id)[0])}</div></div>`;
        }).join('')}</div>

        ${section('Huy hiệu')}
        <div class="badges">${BADGES.map(([emoji, title, hint, earned]) => `
          <div class="badge-cell ${earned(p, full) ? 'earned' : ''}"><div class="emo-l">${emoji}</div><div class="tiny bold">${esc(title)}</div><div class="micro muted">${esc(hint)}</div></div>`).join('')}
        </div>

        ${section('Cài đặt')}
        <div class="card settings">
          <div class="semi">Mục tiêu mỗi ngày</div>
          <div class="row gap-s">${[[20, 'Nhẹ nhàng'], [30, 'Vừa phải'], [50, 'Chăm chỉ'], [80, 'Siêu tốc']].map(([g, l]) =>
            `<button class="filter grow ${p.dailyGoal === g ? 'on' : ''}" data-act="goal" data-g="${g}">${l}<br><span class="tiny">${g} XP</span></button>`).join('')}</div>

          <div class="row center-v mt"><span class="semi grow">Tốc độ giọng đọc: ${p.speechRate.toFixed(1)}x</span>${sayBtn('Nice to meet you. How are you today?')}</div>
          <input type="range" min="0.5" max="1.3" step="0.1" value="${p.speechRate}" data-input="rate" class="range">

          <div class="semi mt">Giọng bản xứ</div>
          <div class="row gap-s">${[['US', '🇺🇸 Anh - Mỹ'], ['UK', '🇬🇧 Anh - Anh']].map(([code, l]) =>
            `<button class="filter grow ${p.accent === code ? 'on' : ''}" data-act="accent" data-c="${code}">${l}</button>`).join('')}</div>
          <div class="semi mt">Giọng đọc</div>
          <div class="voices">${voiceList(p)}</div>
          <p class="muted tiny">Muốn giọng mượt hơn: trên iPad/iPhone vào Cài đặt › Trợ năng › Nội dung được đọc › Giọng nói › Tiếng Anh, tải giọng có chữ “Nâng cao” hoặc “Cao cấp” (ví dụ Ava, Zoe, Evan) rồi quay lại chọn ở đây. Trên máy tính, trình duyệt Edge có sẵn giọng “Natural” rất tự nhiên.</p>

          <div class="semi mt">🤖 Gemini API key (cho tính năng Nói với AI)</div>
          <div class="row gap-s">
            <input class="field grow" type="password" data-input="key" value="${esc(key)}" placeholder="Dán key từ aistudio.google.com/apikey" autocomplete="off" autocapitalize="off" spellcheck="false">
            <button class="btn small-btn" data-act="saveKey">Lưu</button>
          </div>
          <p class="muted tiny">Key chỉ lưu trên máy này và chỉ gửi tới Google Gemini.</p>

          <div class="semi mt">📰 Proxy đọc báo (tuỳ chọn)</div>
          <div class="row gap-s">
            <input class="field grow" data-input="proxy" value="${esc(AiSettings.get().newsProxy || '')}" placeholder="https://ten-cua-ban.workers.dev/?url=" autocomplete="off" autocapitalize="off" spellcheck="false">
            <button class="btn small-btn" data-act="saveProxy">Lưu</button>
          </div>
          <p class="muted tiny">Để trống thì app dùng các proxy công cộng (đôi khi bận). Muốn tin tải ổn định, tự dựng proxy miễn phí theo hướng dẫn trong web/tools/README.md.</p>

          <div class="semi mt">💾 Sao lưu tiến độ</div>
          <div class="row gap-s">
            <button class="btn outline grow" data-act="export">Tải file sao lưu</button>
            <label class="btn outline grow">Khôi phục từ file<input type="file" accept="application/json,.json" data-input="import" hidden></label>
          </div>
          <p class="muted tiny">Dữ liệu nằm trong Safari trên máy này. Thỉnh thoảng sao lưu để không mất tiến độ, hoặc để chuyển sang máy khác.</p>

          <button class="link mt" data-act="reset" style="color:var(--coral)">Xoá tiến độ & học lại từ đầu</button>
        </div>
        <p class="muted small center">SpeakEasy dựa trên mã nguồn mở Engrisk (MIT). Bài học chạy offline, dữ liệu chỉ lưu trên máy bạn.</p>
      </div></div>`;
    },
    actions: {
      goal: (d) => Progress.set({ dailyGoal: +d.g }),
      rate: (v, el) => {
        const rate = Math.round(+v * 10) / 10;
        speaker.rate = rate;
        Progress.set({ speechRate: rate });
        el.closest('.settings').querySelector('.semi.grow').textContent = `Tốc độ giọng đọc: ${rate.toFixed(1)}x`;
      },
      accent: (d) => {
        Progress.set({ accent: d.c, voiceName: '' });
        speaker.accent = d.c;
        speaker.voiceName = '';
        speaker.say('Hello! Lovely to meet you.');
      },
      voice: (d) => {
        Progress.set({ voiceName: d.name });
        speaker.voiceName = d.name;
        speaker.say(SAMPLE);
      },
      tryVoice: (d) => {
        const keep = speaker.voiceName;
        speaker.voiceName = d.name;
        speaker.say(SAMPLE);
        speaker.voiceName = keep;
        return false;
      },
      key: (v) => { pendingKey = v; },
      proxy: (v) => { pendingProxy = v; },
      saveProxy: () => {
        AiSettings.set({ newsProxy: (pendingProxy ?? AiSettings.get().newsProxy ?? '').trim() });
        toast(AiSettings.get().newsProxy ? 'Đã lưu proxy ✅' : 'Đã xoá proxy, dùng proxy công cộng');
      },
      saveKey: () => {
        AiSettings.set({ apiKey: (pendingKey ?? AiSettings.get().apiKey).trim() });
        toast(AiSettings.get().apiKey ? 'Đã lưu Gemini API key ✅' : 'Đã xoá API key');
      },
      export: () => {
        const blob = new Blob([exportBackup()], { type: 'application/json' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `speakeasy-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(a.href), 1000);
        return false;
      },
      import: async (v, el) => {
        const file = el.files?.[0];
        if (!file) return;
        try {
          importBackup(await file.text());
          const p = Progress.get();
          speaker.rate = p.speechRate;
          speaker.accent = p.accent;
          speaker.voiceName = p.voiceName || '';
          toast('Đã khôi phục tiến độ ✅');
          app.update();
        } catch (e) {
          toast(e.message || 'Không đọc được file sao lưu.');
        }
      },
      reset: () => {
        openDialog({
          title: 'Xoá toàn bộ tiến độ?',
          body: '<p>XP, chuỗi ngày, từ đã thuộc và huy hiệu sẽ bị xoá. Không thể hoàn tác.</p>',
          buttons: [
            { label: 'Huỷ' },
            { label: 'Xoá', primary: true, color: 'coral', onClick: () => { Progress.reset(); Review.reset(); app.update(); } },
          ],
        });
        return false;
      },
    },
  };
}

let pendingKey = null;

const SAMPLE = 'This is how I sound when I read the news to you. Is it clear and natural?';

/** Installed English voices, best first, with "automatic" on top. */
function voiceList(p) {
  const voices = speaker.englishVoices().slice(0, 12);
  if (!voices.length) return '<p class="muted small">Máy chưa có giọng tiếng Anh nào, hoặc trình duyệt đang tải danh sách giọng. Mở lại trang này sau vài giây.</p>';
  const best = voices[0];
  const row = (name, label, sub) => `
    <div class="voice-row ${p.voiceName === name ? 'on' : ''}">
      <button class="grow voice-pick" data-act="voice" data-name="${esc(name)}">
        <div class="semi">${p.voiceName === name ? '● ' : '○ '}${esc(label)}</div><div class="muted tiny">${esc(sub)}</div>
      </button>
      <button class="chip-toggle" data-act="tryVoice" data-name="${esc(name || best.name)}">▶ Nghe thử</button>
    </div>`;
  return row('', 'Tự động (giọng tốt nhất)', `Hiện đang dùng: ${best.name}`) +
    voices.map((v) => row(v.name, v.name, `${v.lang}${/natural|neural|premium|enhanced/i.test(v.name) ? ' · chất lượng cao' : ''}${v.localService ? '' : ' · cần mạng'}`)).join('');
}
let pendingProxy = null;
