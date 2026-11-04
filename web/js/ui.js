// Small HTML building blocks shared by the screens (the web side of ui/components/Common.kt).

export const esc = (s) =>
  String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

/** Category accent colors: [strong, light] CSS variable names. */
export const catColors = (id) =>
  ({ work: ['sky', 'sky-l'], life: ['mint', 'mint-l'], travel: ['peach', 'peach-l'], social: ['lavender', 'lavender-l'] })[id] || ['sky', 'sky-l'];

/** Sets --a / --al on a container so everything inside picks up the accent. */
export const accent = (strong, light) => `style="--a:var(--${strong});--al:var(--${light || strong + '-l'})"`;

export const topBar = (title, subtitle, trailing = '') => `
  <header class="topbar">
    <button class="icon-btn" data-act="back" aria-label="Quay lại">
      <svg viewBox="0 0 24 24" width="24" height="24"><path d="M20 11H7.8l5.6-5.6L12 4l-8 8 8 8 1.4-1.4L7.8 13H20z" fill="currentColor"/></svg>
    </button>
    <div class="grow">
      <div class="t-title one-line">${esc(title)}</div>
      ${subtitle ? `<div class="muted small one-line">${esc(subtitle)}</div>` : ''}
    </div>
    ${trailing}
  </header>`;

/** Speaker button; pulses while its own text is being read aloud (see app.js highlight). */
export const sayBtn = (text, { size = 40, slow = false, pitch } = {}) => `
  <button class="say" data-act="say" data-say="${esc(text)}" ${slow ? 'data-slow="1"' : ''} ${pitch ? `data-pitch="${pitch}"` : ''}
    style="--s:${size}px" aria-label="${slow ? 'Nghe chậm' : 'Nghe'}">
    ${slow ? '<span style="font-size:calc(var(--s)*.42)">🐢</span>'
      : '<svg viewBox="0 0 24 24"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z" fill="currentColor"/></svg>'}
  </button>`;

export const micBtn = (act, size = 72, extra = '') => `
  <button class="mic" data-act="${act}" ${extra} style="--s:${size}px" aria-label="Nói">
    <svg viewBox="0 0 24 24"><path d="M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5.3-3a5.3 5.3 0 0 1-10.6 0H5a7 7 0 0 0 6 6.9V21h2v-3.1a7 7 0 0 0 6-6.9h-1.7z" fill="currentColor"/></svg>
  </button>`;

export const bigBtn = (label, act, { color, disabled = false, data = '' } = {}) =>
  `<button class="btn big" data-act="${act}" ${data} ${disabled ? 'disabled' : ''} ${color ? `style="--a:var(--${color})"` : ''}>${esc(label)}</button>`;

export const outBtn = (label, act, { disabled = false, data = '' } = {}) =>
  `<button class="btn outline" data-act="${act}" ${data} ${disabled ? 'disabled' : ''}>${esc(label)}</button>`;

export const pill = (text, color, bg) => `<span class="pill" style="color:var(--${color});background:${bg}">${esc(text)}</span>`;

export const bar = (progress, color = 'a', track = 'var(--line)') =>
  `<div class="bar" style="background:${track}"><i style="width:${Math.round(Math.max(0, Math.min(1, progress)) * 100)}%;background:var(--${color})"></i></div>`;

export function ring(progress, size, color, inner) {
  const r = (size - 8) / 2;
  const c = 2 * Math.PI * r;
  return `<div class="ring" style="width:${size}px;height:${size}px">
    <svg width="${size}" height="${size}">
      <circle cx="${size / 2}" cy="${size / 2}" r="${r}" stroke="${color}" stroke-opacity=".22" stroke-width="8" fill="none"/>
      ${progress > 0 ? `<circle cx="${size / 2}" cy="${size / 2}" r="${r}" stroke="${color}" stroke-width="8" fill="none" stroke-linecap="round"
        stroke-dasharray="${c}" stroke-dashoffset="${c * (1 - Math.min(1, progress))}" transform="rotate(-90 ${size / 2} ${size / 2})"/>` : ''}
    </svg><div class="ring-in">${inner}</div></div>`;
}

export const badge = (emoji, bg = 'var(--sky-l)', size = 48) =>
  `<div class="badge" style="width:${size}px;height:${size}px;background:${bg};font-size:${size * 0.5}px;border-radius:${size / 3}px">${emoji}</div>`;

export const section = (title, action = '') => `<div class="section"><h2>${esc(title)}</h2>${action}</div>`;

/** Sentence with each word coloured by whether the recognizer heard it. */
export const scored = (result, cls = '') =>
  `<span class="scored ${cls}">${result.words.map(([w, ok]) => `<b class="${ok ? 'ok' : 'bad'}">${esc(w)}</b>`).join(' ')}</span>`;

export const toggleChip = (label, on, act) =>
  `<button class="chip-toggle ${on ? 'on' : ''}" data-act="${act}">${esc(label)}</button>`;

// ---- Dialogs ----------------------------------------------------------------

/**
 * Modal dialog. `buttons`: [{label, primary, color, onClick(dialogEl)}]. Returns a close function.
 * Clicking a button closes the dialog unless its onClick returns false.
 */
export function openDialog({ icon = '', title = '', body = '', buttons = [], onDismiss }) {
  const el = document.createElement('div');
  el.className = 'dialog-back';
  el.innerHTML = `<div class="dialog" role="dialog" aria-modal="true">
      ${icon ? `<div class="dialog-icon">${icon}</div>` : ''}
      <div class="dialog-title">${esc(title)}</div>
      <div class="dialog-body">${body}</div>
      <div class="dialog-btns">${buttons
        .map((b, i) => `<button class="btn ${b.primary ? '' : 'text'}" data-i="${i}" ${b.color ? `style="--a:var(--${b.color})"` : ''}>${esc(b.label)}</button>`)
        .join('')}</div>
    </div>`;
  const close = () => el.remove();
  el.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-i]');
    if (btn) {
      if (buttons[+btn.dataset.i].onClick?.(el) !== false) close();
    } else if (e.target === el) {
      close();
      onDismiss?.();
    }
  });
  document.body.appendChild(el);
  setTimeout(() => el.querySelector('input,textarea')?.focus(), 50);
  return close;
}

/** Celebration shown when an activity is finished. */
export function completeDialog({ title, message, xp, onDismiss, onAgain }) {
  return openDialog({
    icon: '🎉',
    title,
    body: `<p>${esc(message)}</p><div class="center">${pill(`+${xp} XP`, 'xp-ink', 'var(--sun-soft)')}</div>`,
    buttons: [
      ...(onAgain ? [{ label: 'Làm lại', onClick: onAgain }] : []),
      { label: 'Tiếp tục', primary: true, onClick: onDismiss },
    ],
    onDismiss,
  });
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
