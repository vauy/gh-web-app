'use strict';

const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const config = require('./config');

const DEFAULT_ACCEPT = 'application/vnd.github+json';
const DEFAULT_VERSION = '2022-11-28';

// ---- 基础请求封装（带超时）----
async function ghRequest(method, url, { token, accept, body, headers = {} } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30000);
  try {
    const res = await fetch(url, {
      method,
      signal: controller.signal,
      headers: {
        Accept: accept || DEFAULT_ACCEPT,
        'X-GitHub-Api-Version': DEFAULT_VERSION,
        'User-Agent': 'gh-web-app',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    return res;
  } finally {
    clearTimeout(timer);
  }
}

// ---- GitHub App JWT（10 分钟内有效，用于应用级 API）----
function appJwt() {
  const now = Math.floor(Date.now() / 1000);
  return jwt.sign({ iat: now, exp: now + 540, iss: config.appId }, config.privateKey, {
    algorithm: 'RS256',
  });
}

// ---- 获取安装（installation）访问令牌 ----
async function installationToken(installationId) {
  const res = await ghRequest(
    'POST',
    `${config.apiBase}/app/installations/${installationId}/access_tokens`,
    { token: appJwt() }
  );
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(`获取安装令牌失败(${res.status}): ${err.message || res.statusText}`);
  }
  const data = await res.json();
  return data.token;
}

// ---- 列出应用被安装到了哪些账号 ----
async function listInstallations() {
  const res = await ghRequest('GET', `${config.apiBase}/app/installations?per_page=100`, {
    token: appJwt(),
  });
  if (!res.ok) throw new Error(`获取安装列表失败(${res.status})`);
  return res.json();
}

// ---- OAuth：构造授权跳转地址 ----
function buildOAuthUrl(state) {
  const params = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: `${config.baseUrl}/auth/callback`,
    scope: config.scopes,
    state,
    allow_signup: 'true',
  });
  return `${config.oauthAuth}?${params.toString()}`;
}

// ---- OAuth：用 code 换用户访问令牌 ----
async function exchangeCode(code) {
  const params = new URLSearchParams({
    client_id: config.clientId,
    client_secret: config.clientSecret,
    code,
    redirect_uri: `${config.baseUrl}/auth/callback`,
  });
  const res = await ghRequest('POST', config.oauthToken, {
    body: Object.fromEntries(params),
    headers: { 'Content-Type': 'application/json' },
    accept: 'application/json',
  });
  const data = await res.json();
  if (data.error) throw new Error(`OAuth 失败: ${data.error_description || data.error}`);
  return data.access_token;
}

// ---- 生成 OAuth state 并校验 ----
function randomState() {
  return crypto.randomBytes(16).toString('hex');
}

module.exports = {
  ghRequest,
  appJwt,
  installationToken,
  listInstallations,
  buildOAuthUrl,
  exchangeCode,
  randomState,
  DEFAULT_ACCEPT,
};
