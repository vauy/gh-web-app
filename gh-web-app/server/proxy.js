'use strict';

const config = require('./config');
const store = require('./store');

/**
 * GitHub REST API 代理。
 * 任何网页版能力（仓库/文件/Issue/PR/分支/Release/Actions/通知/搜索…）都是 GitHub API 的子集，
 * 因此一个通用代理 + 前端封装即可覆盖"网页版的所有操作"。
 * 客户端可用 `_accept=xxx` 查询参数覆盖 Accept 头（例如渲染 README 的 HTML 响应）。
 */
async function ghProxy(req, res) {
  // 会话里必须存有可用令牌
  const session = req.sessionId ? store.getToken(req.sessionId) : null;
  if (!session) {
    return res.status(401).json({ message: '未登录。请先在右上角使用 GitHub 账号登录。' });
  }

  // 取出 /api/gh/ 之后的路径
  const marker = '/api/gh/';
  const idx = req.originalUrl.indexOf(marker);
  let target = idx >= 0 ? req.originalUrl.slice(idx + marker.length) : '';
  target = target.split('?')[0].replace(/\/+$/, ''); // 去掉 query 与多余斜杠
  if (!target) return res.status(400).json({ message: '缺少 GitHub API 路径，例如 /api/gh/user' });

  const url = `${config.apiBase}/${target}`;
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(req.query)) {
    if (!k.startsWith('_')) qs.append(k, v);
  }
  const q = qs.toString();
  const finalUrl = q ? `${url}?${q}` : url;

  const accept = req.query._accept || req.get('x-accept');
  const headers = {};
  if (accept) headers.Accept = accept;

  const hasBody = req.method !== 'GET' && req.method !== 'HEAD' && req.body !== undefined;
  let bodyStr;
  if (hasBody) bodyStr = JSON.stringify(req.body);

  try {
    const r = await fetch(finalUrl, {
      method: req.method,
      headers: {
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'User-Agent': 'gh-web-app',
        Authorization: `Bearer ${session.token}`,
        ...headers,
        ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
      },
      body: hasBody ? bodyStr : undefined,
    });

    const ct = r.headers.get('content-type') || '';
    const text = await r.text();

    if (!r.ok) {
      let payload;
      try { payload = JSON.parse(text); } catch { payload = { message: text.slice(0, 800) }; }
      // 触发限流时给提示
      if (r.status === 403 && text.includes('rate limit')) payload.hint = '已触发 GitHub API 限流，请稍后再试';
      return res.status(r.status).json(payload);
    }

    if (ct.includes('application/json') || ct.includes('application/scim+json')) {
      res.json(JSON.parse(text));
    } else {
      res.set('Content-Type', ct || 'text/plain');
      res.send(text);
    }
  } catch (e) {
    res.status(502).json({ message: `GitHub API 请求失败: ${e.message}` });
  }
}

module.exports = { ghProxy };
