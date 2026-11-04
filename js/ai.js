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

export async function reply(scenario, level, history) {
  const apiKey = AiSettings.get().apiKey.trim();
  if (!apiKey) throw new AiError('Chưa có Gemini API key. Vào tab Tiến độ › Cài đặt để nhập key (miễn phí tại aistudio.google.com).');
  const body = JSON.stringify(requestBody(scenario, level, history));
  let lastError = 'Gemini đang quá tải, thử lại sau ít phút nhé.';
  // Free-tier models get overloaded (503) or retired for new keys (404), so try a short list,
  // starting with the model that answered last time.
  for (const model of [...new Set([workingModel, ...MODELS].filter(Boolean))]) {
    let res, text;
    try {
      // Flash models can think for a while before the first byte arrives.
      res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
        body,
        signal: AbortSignal.timeout ? AbortSignal.timeout(60000) : undefined,
      });
      text = await res.text();
    } catch {
      lastError = 'Không kết nối được. Kiểm tra mạng rồi thử lại.';
      continue;
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
