// Google Gemini (free tier) as a role-play partner that also corrects every learner line.
import { requestBody } from './logic.js';
import { AiSettings } from './store.js';

/** Google's rolling Flash aliases first, then a pinned fallback; same list as the Android app. */
const MODELS = ['gemini-flash-latest', 'gemini-3.5-flash', 'gemini-flash-lite-latest'];
let workingModel = null;

export class AiError extends Error {}

/** Pulls the answer text out of a generateContent response; a blocked prompt has no candidates. */
function replyText(root) {
  const parts = root?.candidates?.[0]?.content?.parts;
  const text = (parts || []).map((p) => p.text || '').join('');
  if (!text.trim()) throw new AiError('AI không trả lời câu này. Thử nói theo cách khác nhé.');
  return text;
}

function parse(text) {
  try {
    const r = JSON.parse(text);
    if (typeof r.reply !== 'string') throw new Error();
    return {
      reply: r.reply,
      reply_vi: r.reply_vi || '',
      correction: { has_issue: !!r.correction?.has_issue, corrected: r.correction?.corrected || '', explanation_vi: r.correction?.explanation_vi || '' },
      suggestions: Array.isArray(r.suggestions) ? r.suggestions : [],
    };
  } catch {
    throw new AiError('AI trả lời không đúng định dạng. Thử lại nhé.');
  }
}

/** How long one model may take before the next one is tried; a stuck request must not freeze the chat. */
const MODEL_TIMEOUT = 25000;

/**
 * `signal` lets the chat cancel; `onSlow(message)` reports when a model is slow and another is being tried.
 */
export async function reply(scenario, level, history, { signal, onSlow } = {}) {
  const apiKey = AiSettings.get().apiKey.trim();
  if (!apiKey) throw new AiError('Chưa có Gemini API key. Vào tab Tiến độ › Cài đặt để nhập key (miễn phí tại aistudio.google.com).');
  const body = JSON.stringify(requestBody(scenario, level, history));
  let lastError = 'Gemini đang quá tải, thử lại sau ít phút nhé.';
  // Free-tier models get overloaded (503) or retired for new keys (404), so try a short list,
  // starting with the model that answered last time.
  const models = [...new Set([workingModel, ...MODELS].filter(Boolean))];
  for (const [i, model] of models.entries()) {
    if (signal?.aborted) throw new AiError('Đã huỷ.');
    let res, text;
    // Our own timer (not AbortSignal.timeout) so a timeout can be told apart from a dropped connection.
    const ctrl = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; ctrl.abort(); }, MODEL_TIMEOUT);
    const cancel = () => ctrl.abort();
    signal?.addEventListener('abort', cancel);
    try {
      res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
        body,
        signal: ctrl.signal,
      });
      text = await res.text();
    } catch {
      if (signal?.aborted) throw new AiError('Đã huỷ.');
      lastError = timedOut
        ? 'Gemini đang phản hồi rất chậm (bản miễn phí hay bị quá tải). Đợi một chút rồi bấm Thử lại nhé.'
        : navigator.onLine === false
          ? 'Máy đang mất mạng. Kết nối lại rồi thử lại.'
          : 'Không gọi được Gemini. Kiểm tra mạng (hoặc VPN/tường lửa công ty) rồi thử lại.';
      if (i < models.length - 1) onSlow?.(timedOut ? 'Gemini đang chậm, đang thử máy chủ khác…' : 'Mạng chập chờn, đang thử lại…');
      continue;
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener('abort', cancel);
    }
    if (res.status === 503 || res.status === 500) {
      lastError = 'Gemini đang quá tải, thử lại sau ít phút nhé.';
      if (i < models.length - 1) onSlow?.('Gemini đang quá tải, đang thử máy chủ khác…');
    }
    if (res.status === 400 || res.status === 403) {
      if (/api key/i.test(text)) throw new AiError('Gemini API key không hợp lệ. Kiểm tra lại key trong Cài đặt.');
      throw new AiError(`Gemini từ chối yêu cầu (${res.status}). Thử lại sau nhé.`);
    }
    if (res.status === 429) lastError = 'Đã hết lượt miễn phí của Gemini, thử lại sau ít phút.';
    if (res.ok) {
      workingModel = model;
      let root;
      try {
        root = JSON.parse(text);
      } catch {
        throw new AiError('AI trả lời không đúng định dạng. Thử lại nhé.');
      }
      return parse(replyText(root));
    }
  }
  throw new AiError(lastError);
}
