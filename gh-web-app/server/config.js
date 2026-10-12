'use strict';

const fs = require('fs');

function env(name, fallback = '') {
  const v = process.env[name];
  return v === undefined || v === '' ? fallback : v;
}

function privateKey() {
  const fromPath = env('GITHUB_APP_PRIVATE_KEY_PATH');
  if (fromPath) return fs.readFileSync(fromPath, 'utf8');
  return env('GITHUB_APP_PRIVATE_KEY');
}

const config = {
  port: parseInt(env('PORT', '3000'), 10),
  baseUrl: env('BASE_URL', 'http://localhost:3000').replace(/\/+$/, ''),
  appId: env('GITHUB_APP_ID'),
  privateKey: privateKey(),
  clientId: env('GITHUB_APP_CLIENT_ID'),
  clientSecret: env('GITHUB_APP_CLIENT_SECRET'),
  webhookSecret: env('GITHUB_APP_WEBHOOK_SECRET'),
  sessionSecret: env('SESSION_SECRET'),
  scopes: env('SCOPES', 'repo,workflow,notifications,read:org,gist'),
  botWelcome: env('BOT_WELCOME', 'true') === 'true',
  apiBase: 'https://api.github.com',
  oauthAuth: 'https://github.com/login/oauth/authorize',
  oauthToken: 'https://github.com/login/oauth/access_token',
};

// 未配置 session 密钥时随机生成（重启后会话失效），便于本地直接跑通
if (!config.sessionSecret) {
  const crypto = require('crypto');
  config.sessionSecret = crypto.randomBytes(32).toString('hex');
  console.warn('[config] SESSION_SECRET 未配置，已随机生成（仅本次运行有效）');
}

module.exports = config;
