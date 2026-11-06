// "Kiểm tra âm thanh" in Settings: speaks a test line, then shows what the speech engine reported,
// so a learner with no sound can send a screenshot instead of guessing.
import { speaker } from '../speech.js';
import { esc, openDialog } from '../ui.js';

const TEST = 'Hello! Can you hear me clearly?';

export const soundCheckRow = () => `
  <div class="semi mt">🔊 Không nghe thấy tiếng?</div>
  <button class="btn outline" data-act="soundCheck">🔊 Kiểm tra âm thanh</button>`;

function report() {
  const synth = window.speechSynthesis;
  const standalone = window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone;
  const v = speaker.voice();
  const rows = [
    ['Bộ đọc của trình duyệt', synth ? 'có' : 'KHÔNG CÓ'],
    ['Số giọng tiếng Anh', `${speaker.englishVoices().length} / ${speaker.voices.length} giọng`],
    ['Giọng đang dùng', v ? `${v.name} (${v.lang})` : 'mặc định của máy'],
    ['Đang đọc', synth ? `${synth.speaking ? 'có' : 'không'}${synth.paused ? ', đang tạm dừng' : ''}` : '-'],
    ['Chế độ âm thanh', navigator.audioSession?.type || 'không hỗ trợ'],
    ['Mở từ', standalone ? 'biểu tượng màn hình chính' : 'trình duyệt'],
    ['Sự kiện', (speaker.events || []).join(' · ') || 'chưa có'],
    ['Thiết bị', navigator.userAgent.replace(/^Mozilla\/5\.0 /, '').slice(0, 120)],
  ];
  const heard = (speaker.events || []).some((e) => / start/.test(e));
  return `<p>${heard ? '✅ Máy đã bắt đầu đọc. Nếu vẫn không nghe, kiểm tra âm lượng và nút tắt tiếng.' : '⚠️ Máy chưa phát được tiếng.'}</p>
    <ul class="small check-list">
      <li>Tăng âm lượng, tắt chế độ im lặng (Trung tâm điều khiển › biểu tượng chuông).</li>
      <li>Tắt Bluetooth nếu máy đang nối tai nghe/loa khác.</li>
      <li>Cài đặt › Trợ năng › Nội dung được đọc › Giọng nói › Tiếng Anh: tải một giọng (ví dụ Samantha).</li>
    </ul>
    <table class="diag">${rows.map(([k, val]) => `<tr><td class="muted">${esc(k)}</td><td>${esc(val)}</td></tr>`).join('')}</table>
    <p class="muted tiny">Vẫn không được? Chụp màn hình này gửi người hỗ trợ.</p>`;
}

export const soundCheckActions = {
  soundCheck: () => {
    speaker.events = [];
    speaker.say(TEST);
    openDialog({ icon: '🔊', title: 'Đang đọc thử…', body: '<p class="muted">Bạn có nghe câu “Hello! Can you hear me clearly?” không?</p>', buttons: [{ label: 'Đóng' }] });
    setTimeout(() => {
      const body = document.querySelector('.dialog-body');
      const title = document.querySelector('.dialog-title');
      if (body) body.innerHTML = report();
      if (title) title.textContent = 'Kết quả kiểm tra';
    }, 2500);
    return false;
  },
};
