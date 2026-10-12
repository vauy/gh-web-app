'use strict';

const crypto = require('crypto');
const config = require('./config');
const store = require('./store');
const { ghRequest, installationToken } = require('./auth');

// 校验 GitHub Webhook 签名（X-Hub-Signature-256）
function verifySignature(rawBody, signature) {
  if (!config.webhookSecret) return true; // 未配置密钥时不校验（本地调试方便）
  if (!signature) return false;
  const expected = 'sha256=' + crypto.createHmac('sha256', config.webhookSecret).update(rawBody).digest('hex');
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signature));
}

// 机器人：新 Issue / 新 PR 自动发欢迎评论（使用安装令牌，以应用身份回复）
async function welcomeBot(payload) {
  if (!config.botWelcome || !payload.installation || !payload.repository) return;
  const repo = payload.repository.full_name;
  try {
    const token = await installationToken(payload.installation.id);
    if (payload.event === 'issues' && payload.issue) {
      await ghRequest('POST', `${config.apiBase}/repos/${repo}/issues/${payload.issue.number}/comments`, {
        token,
        body: { body: `👋 感谢提交 Issue #${payload.issue.number}！我会尽快处理。` },
      });
    } else if (payload.event === 'pull_request' && payload.pull_request) {
      await ghRequest('POST', `${config.apiBase}/repos/${repo}/issues/${payload.pull_request.number}/comments`, {
        token,
        body: { body: `🚀 收到 Pull Request #${payload.pull_request.number}，正在等待 review。` },
      });
    }
  } catch (e) {
    console.error('[bot] 自动回复失败:', e.message);
  }
}

function webhookHandler(req, res) {
  const event = req.headers['x-github-event'] || 'unknown';
  const signature = req.headers['x-hub-signature-256'];
  if (!verifySignature(req.body, signature)) {
    return res.status(401).json({ ok: false, message: '签名校验失败' });
  }

  let payload;
  try {
    payload = JSON.parse(req.body.toString('utf8'));
  } catch {
    return res.status(400).json({ ok: false, message: '无效的 JSON payload' });
  }

  store.addEvent({
    id: crypto.randomBytes(6).toString('hex'),
    event,
    action: payload.action || '',
    repo: (payload.repository && payload.repository.full_name) || '',
    sender: (payload.sender && payload.sender.login) || '',
    at: new Date().toISOString(),
  });

  welcomeBot({ ...payload, event });

  res.json({ ok: true, message: `已接收 ${event} 事件` });
}

module.exports = { webhookHandler };
