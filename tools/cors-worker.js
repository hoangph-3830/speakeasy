// Cloudflare Worker: a small CORS proxy so the SpeakEasy web app can read news feeds and pages reliably.
// Only news hosts are allowed, so the worker can't be used as an open proxy.
const ALLOWED = [
  'feeds.bbci.co.uk', 'www.bbc.co.uk', 'www.bbc.com',
  'lite.cnn.com', 'www.cnn.com', 'edition.cnn.com',
  'news.google.com', // Reuters headlines
  'www.theguardian.com', 'feeds.npr.org', 'www.npr.org',
  'www.aljazeera.com', 'www.abc.net.au', 'theconversation.com',
];

export default {
  async fetch(request) {
    const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, OPTIONS' };
    if (request.method === 'OPTIONS') return new Response(null, { headers: cors });
    const target = new URL(request.url).searchParams.get('url');
    let url;
    try {
      url = new URL(target);
    } catch {
      return new Response('Missing or invalid ?url=', { status: 400, headers: cors });
    }
    if (url.protocol !== 'https:' || !ALLOWED.includes(url.hostname)) {
      return new Response('Host not allowed', { status: 403, headers: cors });
    }
    const upstream = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0 (SpeakEasy reader)' }, cf: { cacheTtl: 300 } });
    const headers = new Headers(cors);
    headers.set('Content-Type', upstream.headers.get('Content-Type') || 'text/html; charset=utf-8');
    headers.set('Cache-Control', 'public, max-age=300');
    return new Response(upstream.body, { status: upstream.status, headers });
  },
};
