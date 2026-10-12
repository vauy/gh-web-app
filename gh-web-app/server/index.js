'use strict';

const path = require('path');
const crypto = require('crypto');
const express = require('express');
const config = require('./config');
const store = require('./store');
const auth = require('./auth');
const { ghProxy } = require('./proxy');
const { webhookHandler } = require('./webhook');

const app = express();
const COOKIE = 'ghapp_session';
const SESSION_TTL = 7 * 24 * 3600 * 1000; // 7 天

// ---- 会话 / Cookie 工具 ----
function parseCookies(header) {
  const out = {};
  if (!header) return out;
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

function sessionMiddleware(req, res, next) {
  req.sessionId = parseCookies(req.headers.cookie)[COOKIE] || null;
  next();
}

function setSessionCookie(res, sid) {
  res.setHeader('Set-Cookie', `${COOKIE}=${sid}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${SESSION_TTL / 1000}`);
}

// ---- 静态文件 ----
app.use(express.static(path.join(__dirname, '..', 'public')));

// ---- Webhook（需原始 body 用于签名校验，放在 json 解析之前）----
app.post('/webhook', express.raw({ type: '*/*', limit: '20mb' }), webhookHandler);

// ---- JSON 解析 ----
app.use(express.json({ limit: '20mb' }));

app.use(sessionMiddleware);

// ---- OAuth 登录 ----
app.get('/auth/login', (req, res) => {
  const state = auth.randomState();
  res.redirect(auth.buildOAuthUrl(state));
});

app.get('/auth/callback', async (req, res) => {
  const { code, state } = req.query;
  if (!code) return res.status(400).send('缺少 code 参数');
  try {
    const token = await auth.exchangeCode(code);
    const sid = crypto.randomBytes(24).toString('hex');
    store.setSession(sid, { type: 'user', token, user: null, createdAt: Date.now() });
    setSessionCookie(res, sid);
    res.redirect('/#/');
  } catch (e) {
    res.status(500).send(`登录失败：${e.message}`);
  }
});

app.get('/auth/logout', (req, res) => {
  if (req.sessionId) store.dropSession(req.sessionId);
  res.setHeader('Set-Cookie', `${COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`);
  res.redirect('/#/login');
});

// ---- 应用（安装令牌）登录 ----
// 列出应用已安装到的账号
app.get('/api/installations', async (req, res) => {
  try {
    const list = await auth.listInstallations();
    res.json(list);
  } catch (e) {
    res.status(500).json({ message: e.message });
  }
});

// 激活某个安装：以应用身份操作（机器人 / 集成）
app.post('/api/installations/:id/activate', async (req, res) => {
  try {
    const token = await auth.installationToken(req.params.id);
    const sid = crypto.randomBytes(24).toString('hex');
    store.setSession(sid, { type: 'app', token, installationId: req.params.id, user: null, createdAt: Date.now() });
    setSessionCookie(res, sid);
    res.json({ ok: true, sessionId: sid });
  } catch (e) {
    res.status(500).json({ message: e.message });
  }
});

// ---- 会话状态（前端引导用）----
app.get('/api/session', async (req, res) => {
  const s = req.sessionId ? store.getToken(req.sessionId) : null;
  if (!s) return res.json({ loggedIn: false });
  try {
    // 取 /user 缓存用户名
    if (!s.user) {
      const r = await fetch(`${config.apiBase}/user`, {
        headers: { Authorization: `Bearer ${s.token}`, Accept: 'application/vnd.github+json' },
      });
      if (r.ok) s.user = (await r.json()).login;
    }
    res.json({ loggedIn: true, type: s.type, user: s.user, installationId: s.installationId || null });
  } catch {
    res.json({ loggedIn: true, type: s.type, user: s.user });
  }
});

// ---- Webhook 事件日志 ----
app.get('/api/webhooks/events', (req, res) => {
  res.json(store.listEvents());
});

// ---- GitHub API 通用代理（覆盖网页版全部操作）----
app.use('/api/gh', ghProxy);

// ---- 启动 ----
const server = app.listen(config.port, () => {
  console.log(`\n  GitHub Web App 已启动`);
  console.log(`  访问地址:  ${config.baseUrl}`);
  console.log(`  OAuth 回调: ${config.baseUrl}/auth/callback`);
  console.log(`  Webhook:   ${config.baseUrl}/webhook\n`);
});

module.exports = server;
