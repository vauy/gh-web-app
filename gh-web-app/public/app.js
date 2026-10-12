'use strict';

/* ================= 工具 ================= */
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const time = iso => (iso ? new Date(iso).toLocaleString('zh-CN') : '');
const num = n => (n == null ? '' : n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n));
const encPath = p => (p || '').split('/').filter(Boolean).map(encodeURIComponent).join('/');

function toast(msg, type = '') {
  const el = document.createElement('div');
  el.className = 'toast ' + type;
  el.textContent = msg;
  $('#toast').appendChild(el);
  setTimeout(() => el.remove(), 3200);
}

function openModal(html) {
  $('#modalRoot').innerHTML = `<div class="overlay" onclick="if(event.target===this)closeModal()"><div class="modal">${html}</div></div>`;
}
const closeModal = () => ($('#modalRoot').innerHTML = '');

/* 统一 API 调用（代理到 GitHub REST API，覆盖网页版全部操作） */
async function api(method, path, { body, accept } = {}) {
  let url = '/api/gh/' + path;
  if (accept) url += (url.includes('?') ? '&' : '?') + '_accept=' + encodeURIComponent(accept);
  const res = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = text;
  try { data = text ? JSON.parse(text) : null; } catch { /* 文本响应 */ }
  if (res.status === 401) {
    toast('未登录或登录已过期，请重新登录', 'err');
    goto('/login');
    throw new Error('未登录');
  }
  if (!res.ok) {
    const msg = (data && data.message) || (typeof data === 'string' ? data.slice(0, 300) : 'HTTP ' + res.status);
    throw new Error(msg + (data && data.hint ? '（' + data.hint + '）' : ''));
  }
  return data;
}

function goto(h) { location.hash = h; }
function parseHash() {
  let h = location.hash.slice(1) || '/';
  const i = h.indexOf('?');
  const qs = i >= 0 ? new URLSearchParams(h.slice(i + 1)) : new URLSearchParams();
  return { path: (i >= 0 ? h.slice(0, i) : h).split('/').filter(Boolean), params: qs };
}
const SP = x => x && x[0]; // get (plural).length shorthands

/* ================= 全局状态 ================= */
const state = { loggedIn: false, user: null, type: 'user', booted: false };
const repoState = { owner: '', repo: '', tab: 'code', dir: '', fileMode: null, fileData: null };

const NAV = [
  { label: '首页', icon: '🏠', href: '/' },
  { label: '我的仓库', icon: '📦', href: '/repos' },
  { label: 'Issues', icon: '🐛', href: '/issues' },
  { label: 'Pull Requests', icon: '🔀', href: '/pulls' },
  { label: '通知', icon: '🔔', href: '/notifications' },
  { label: 'Gists', icon: '📝', href: '/gists' },
  { label: '搜索', icon: '🔍', href: '/search' },
  { label: 'Webhook 事件', icon: '⚡', href: '/events' },
  { label: 'API 调试台', icon: '🧪', href: '/explorer' },
];

function renderSidebar() {
  $('#sidebar').innerHTML = NAV.map(n => {
    const cur = parseHash().path[0] || '';
    const active = (n.href === '/' ? cur === '' : cur === n.href.replace('/', '')) ? 'active' : '';
    return `<div class="nav-item ${active}" onclick="goto('${n.href}')"><span>${n.icon}</span><span>${n.label}</span></div>`;
  }).join('');
}

function renderUserArea() {
  const el = $('#userArea');
  if (!state.loggedIn) {
    el.innerHTML = `<button onclick="goto('/login')" class="primary">登录</button>`;
  } else {
    el.innerHTML = `
      <span class="muted">${state.type === 'app' ? '🤖 应用身份' : '账号身份'}</span>
      <span>${esc(state.user || '')}</span>
      <a href="/auth/logout" class="muted" title="退出登录">退出</a>`;
  }
}

async function loadSession() {
  try {
    const s = await (await fetch('/api/session')).json();
    state.loggedIn = !!s.loggedIn;
    state.user = s.user || null;
    state.type = s.type || 'user';
  } catch { state.loggedIn = false; }
  state.booted = true;
  renderUserArea();
  renderSidebar();
}

/* ================= 路由 ================= */
window.addEventListener('hashchange', route);
async function route() {
  if (!state.booted) return; // 首次启动由 boot() 驱动
  const { path: p } = parseHash();
  const main = $('#main');
  if (!state.loggedIn && p[0] !== 'login') { renderLogin(); return; }
  try {
    switch (p[0]) {
      case undefined: case '': await renderDashboard(); break;
      case 'repos': await renderRepos(); break;
      case 'repo': p.length >= 3 ? await renderRepo(p[1], p[2]) : await renderRepos(); break;
      case 'issues': await renderGlobalIssues(); break;
      case 'pulls': await renderGlobalPulls(); break;
      case 'search': await renderSearch(); break;
      case 'notifications': await renderNotifications(); break;
      case 'gists': await renderGists(); break;
      case 'events': await renderEvents(); break;
      case 'explorer': await renderExplorer(); break;
      case 'login': renderLogin(); break;
      default: await renderDashboard();
    }
  } catch (e) {
    $('#main').innerHTML = `<div class="card"><h3 style="color:var(--red)">出错了</h3><div>${esc(e.message)}</div></div>`;
  }
}
async function boot() {
  await loadSession();
  route();
}

/* ================= 页面：登录 ================= */
function renderLogin() {
  $('#main').innerHTML = `
    <div class="login-wrap">
      <div class="login-box">
        <h2>👋 欢迎使用 GitHub Web App</h2>
        <p class="muted">登录后可操作仓库、Issue、PR、文件、Release、Actions 等网页版全部功能</p>
        <a class="login-btn" href="/auth/login">
          <svg viewBox="0 0 16 16" width="22" height="22"><path fill="#fff" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>
          使用 GitHub 账号登录
        </a>
        <p class="muted" style="margin-top:18px;font-size:12.5px">
          ① 在 GitHub 创建/注册本应用 ② 配置 .env ③ 启动服务。<br>详见项目 README。也可用下方"应用身份"直接操作已安装的仓库。
        </p>
      </div>
      <div class="card" style="margin-top:16px">
        <h3>🤖 应用身份（机器人 / 集成）</h3>
        <p class="muted" style="margin:0 0 10px">以 GitHub App 身份激活某个已安装的账号，无需账号授权即可操作。先注册 App 并安装到账号。</p>
        <div id="installList" class="center"><span class="spinner"></span></div>
      </div>
    </div>`;
  loadInstallList();
}

async function loadInstallList() {
  try {
    const list = await (await fetch('/api/installations')).json();
    const box = $('#installList');
    if (!list.length) {
      box.innerHTML = `<div class="muted">尚未安装到任何账号。<br>点击右上角 Settings → Developer settings → GitHub Apps → 你的应用 → Install App。</div>`;
      return;
    }
    box.innerHTML = `<ul class="list">` + list.map(x => `
      <li style="display:flex;align-items:center;gap:10px">
        <span>${esc(x.account.login)}</span>
        <span class="badge accent">${x.target_type}</span>
        <button class="primary" style="margin-left:auto" onclick="activateInstall(${x.id}, this)">以应用身份激活</button>
      </li>`).join('') + `</ul>`;
  } catch (e) {
    $('#installList').innerHTML = `<div class="muted">应用未注册或私钥未配置：${esc(e.message)}</div>`;
  }
}

async function activateInstall(id, btn) {
  btn.disabled = true; btn.textContent = '激活中…';
  try {
    const r = await fetch(`/api/installations/${id}/activate`, { method: 'POST' });
    const d = await r.json();
    if (!r.ok) throw new Error(d.message);
    toast('已切换为应用身份', 'ok');
    await loadSession();
    goto('/');
  } catch (e) { toast(e.message, 'err'); btn.disabled = false; btn.textContent = '以应用身份激活'; }
}

/* ================= 页面：仪表盘 ================= */
async function renderDashboard() {
  $('#main').innerHTML = `<div class="center"><span class="spinner"></span></div>`;
  const [me, starred] = await Promise.all([
    api('GET', 'user'),
    api('GET', 'user/starred?per_page=12').catch(() => []),
  ]);
  $('#main').innerHTML = `
    <div class="card" style="display:flex;align-items:center;gap:16px">
      <img src="${esc(me.avatar_url)}" width="72" height="72" style="border-radius:50%">
      <div>
        <h2 style="margin:0">${esc(me.name || me.login)} <span class="muted" style="font-size:14px">@${esc(me.login)}</span></h2>
        <div class="muted">${esc(me.bio || '')}</div>
        <div class="meta" style="display:flex;gap:14px;margin-top:6px">
          <span>📦 仓库 <b>${num(me.public_repos)}</b></span>
          <span>👥 粉丝 <b>${num(me.followers)}</b></span>
          <span>👣 关注 <b>${num(me.following)}</b></span>
          ${me.location ? `<span>📍 ${esc(me.location)}</span>` : ''}
          ${me.blog ? `<span>🔗 <a href="${esc(me.blog)}" target="_blank">${esc(me.blog)}</a></span>` : ''}
        </div>
      </div>
    </div>
    <div style="display:flex;gap:12px;margin-bottom:14px">
      <button class="primary" onclick="goto('/repos')">我的仓库</button>
      <button onclick="goto('/issues')">我的 Issues</button>
      <button onclick="goto('/pulls')">我的 Pull Requests</button>
      <button onclick="goto('/notifications')">未读通知</button>
    </div>
    <h3>⭐ 收藏的仓库</h3>
    ${starred.length ? `<div class="grid">` + starred.map(starCard).join('') + `</div>` : `<div class="muted">还没有收藏的仓库</div>`}`;
}

const starCard = r => `
  <div class="card" style="margin:0;cursor:pointer" onclick="goto('/repo/${esc(r.owner.login)}/${esc(r.name)}')">
    <div style="font-weight:600;color:var(--accent)">${esc(r.full_name)}</div>
    <div class="desc muted" style="height:38px;overflow:hidden">${esc(r.description || '暂无描述')}</div>
    <div class="muted" style="margin-top:8px;font-size:12px">⭐ ${num(r.stargazers_count)} · 🍴 ${num(r.forks_count)} · ${r.language || ''}</div>
  </div>`;

/* ================= 页面：我的仓库 ================= */
async function renderRepos() {
  $('#main').innerHTML = `
    <div class="actions-row">
      <h2 style="margin:0">我的仓库</h2>
      <input id="repoFilter" placeholder="过滤…" style="width:200px" oninput="filterRepos(this.value)">
      <button class="primary" style="margin-left:auto" onclick="newRepoForm()">+ 新建仓库</button>
    </div>
    <div id="repoList" class="center"><span class="spinner"></span></div>`;
  const repos = await api('GET', 'user/repos?per_page=100&sort=updated');
  window.__repos = repos;
  renderRepoList(repos);
}
function filterRepos(kw) {
  kw = (kw || '').toLowerCase();
  renderRepoList(window.__repos.filter(r => !kw || r.name.toLowerCase().includes(kw) || (r.description || '').toLowerCase().includes(kw)));
}
function renderRepoList(repos) {
  $('#repoList').innerHTML = repos.length ? `<ul class="list">` + repos.map(r => `
    <li>
      <div style="display:flex;align-items:center;gap:12px">
        <div style="flex:1;cursor:pointer" onclick="goto('/repo/${esc(r.owner.login)}/${esc(r.name)}')">
          <div class="title" style="color:var(--accent)">${esc(r.full_name)}</div>
          <div class="desc">${esc(r.description || '暂无描述')}</div>
        </div>
        <span class="muted" style="font-size:12px">⭐ ${num(r.stargazers_count)} · 🍴 ${num(r.forks_count)}</span>
        <span class="badge">${esc(r.language || '')}</span>
        ${r.private ? '<span class="badge yellow">私有</span>' : ''}
        <button onclick="starRepo('${esc(r.full_name)}', ${r.stargazers_count > 0 ? 'true' : 'false'}, this)">${r.stargazers_count > 0 ? '取消收藏' : '⭐ 收藏'}</button>
      </div>
    </li>`).join('') + `</ul>` : `<div class="muted">没有仓库</div>`;
}
async function starRepo(fullName, starred, btn) {
  btn.disabled = true;
  try {
    if (starred) { await api('DELETE', `user/starred/${fullName}`); toast('已取消收藏', 'ok'); }
    else { await api('PUT', `user/starred/${fullName}`); toast('已收藏 ⭐', 'ok'); }
    renderRepos();
  } catch (e) { toast(e.message, 'err'); btn.disabled = false; }
}

function newRepoForm() {
  openModal(`
    <h3>新建仓库</h3>
    <div class="form-row"><label>仓库名称 *</label><input id="f_name" placeholder="my-new-repo"></div>
    <div class="form-row"><label>描述</label><input id="f_desc" placeholder="简短描述"></div>
    <div class="form-row"><label>可见性</label>
      <select id="f_private"><option value="false">公开</option><option value="true">私有</option></select></div>
    <div class="form-row"><label>自动创建 README</label>
      <select id="f_readme"><option value="false">否</option><option value="true">是</option></select></div>
    <div class="modal-actions">
      <button onclick="closeModal()">取消</button>
      <button class="primary" onclick="doCreateRepo()">创建</button>
    </div>`);
}
async function doCreateRepo() {
  const name = $('#f_name').value.trim();
  if (!name) return toast('请输入仓库名称', 'err');
  const r = await api('POST', 'user/repos', { body: { name, description: $('#f_desc').value, private: $('#f_private').value === 'true', auto_init: $('#f_readme').value === 'true' } });
  closeModal(); toast('仓库已创建', 'ok'); goto(`/repo/${r.owner.login}/${r.name}`);
}

/* ================= 页面：仓库详情 ================= */
const TABS = [['code', '📁 代码'], ['issues', '🐛 Issues'], ['pulls', '🔀 Pull Requests'], ['commits', '🕘 提交'], ['branches', '🌿 分支'], ['releases', '🏷️ Releases'], ['actions', '⚙️ Actions']];

async function renderRepo(owner, repo) {
  repoState.owner = owner; repoState.repo = repo;
  const params = parseHash().params.get('tab');
  repoState.tab = TABS.some(t => t[0] === params) ? params : repoState.tab || 'code';

  const r = await api('GET', `repos/${owner}/${repo}`);
  $('#main').innerHTML = `
    <div class="repo-header">
      <a href="/${r.owner.login}" style="color:var(--muted)">${esc(r.owner.login)}</a><span class="muted">/</span>
      <a href="#" style="font-size:20px;font-weight:700;color:var(--text)">${esc(r.name)}</a>
      ${r.private ? '<span class="badge yellow">私有</span>' : '<span class="badge green">公开</span>'}
      <div class="meta">
        <span>⭐ <b>${num(r.stargazers_count)}</b></span><span>🍴 <b>${num(r.forks_count)}</b></span>
        <span>🐛 <b>${num(r.open_issues_count)}</b></span><span>🌿 ${esc(r.default_branch)}</span>
        ${r.language ? `<span>${esc(r.language)}</span>` : ''}
      </div>
      <button style="margin-left:auto" onclick="toggleStar('${esc(r.full_name)}', ${r.stargazers_count > 0}, this)">${r.stargazers_count > 0 ? '取消收藏' : '⭐ 收藏'}</button>
      <button onclick="toggleWatch('${esc(r.full_name)}', this)">${r.subscribers_count > 0 ? '取消关注' : '👀 关注'}</button>
    </div>
    ${r.description ? `<div class="muted" style="margin-bottom:14px">${esc(r.description)}</div>` : ''}
    <div class="tabs">${TABS.map(t => `<div class="tab ${t[0] === repoState.tab ? 'active' : ''}" onclick="goto('/repo/${esc(owner)}/${esc(repo)}?tab=${t[0]}')">${t[1]}</div>`).join('')}</div>
    <div id="tabBody"><div class="center"><span class="spinner"></span></div></div>`;

  if (r.stargazers_count > 0) { /* 无操作 */ }
  await renderTab();
}

async function renderTab() {
  const tab = repoState.tab;
  const { owner, repo } = repoState;
  const body = $('#tabBody');
  body.innerHTML = `<div class="center"><span class="spinner"></span></div>`;
  try {
    if (tab === 'code') await codeBrowse();
    else if (tab === 'issues') await issuesList();
    else if (tab === 'pulls') await pullsList();
    else if (tab === 'commits') await commitsList();
    else if (tab === 'branches') await branchesList();
    else if (tab === 'releases') await releasesList();
    else if (tab === 'actions') await actionsList();
    else body.innerHTML = '';
  } catch (e) {
    body.innerHTML = `<div class="card"><h3 style="color:var(--red)">出错了</h3><div>${esc(e.message)}</div></div>`;
  }
}

async function toggleStar(fullName, starred, btn) {
  btn.disabled = true;
  try {
    if (starred) { await api('DELETE', `user/starred/${fullName}`); toast('已取消收藏', 'ok'); }
    else { await api('PUT', `user/starred/${fullName}`); toast('已收藏', 'ok'); }
    await renderRepo(repoState.owner, repoState.repo);
  } catch (e) { toast(e.message, 'err'); btn.disabled = false; }
}
async function toggleWatch(fullName, btn) {
  btn.disabled = true;
  try {
    await api('PUT', `user/subscriptions/${fullName}`);
    toast('已关注 👀', 'ok');
    await renderRepo(repoState.owner, repoState.repo);
  } catch (e) { toast(e.message, 'err'); btn.disabled = false; }
}

/* ---- 代码浏览 ---- */
async function codeBrowse() {
  const { owner, repo } = repoState;
  const p = repoState.dir;
  const [dir, readme] = await Promise.all([
    api('GET', `repos/${owner}/${repo}/contents/${encPath(p)}`),
    p ? Promise.resolve(null) : api('GET', `repos/${owner}/${repo}/readme`, { accept: 'application/vnd.github.html+json' }).catch(() => null),
  ]);
  const bread = p.split('/').filter(Boolean);
  $('#tabBody').innerHTML = `
    <div class="actions-row">
      <div class="breadcrumb">
        <span style="cursor:pointer" onclick="repoState.dir='';codeBrowse()">${esc(repo)}</span>
        ${bread.map((s, i) => {
          const up = bread.slice(0, i + 1).join('/');
          return `<span class="sep">/</span><span style="cursor:pointer" onclick="repoState.dir='${esc(up)}';codeBrowse()">${esc(s)}</span>`;
        }).join('')}
      </div>
      <button style="margin-left:auto" onclick="newFileForm()">+ 新建文件</button>
      <button onclick="uploadForm()">⬆ 上传文件</button>
    </div>
    <div class="card" style="padding:6px">
      ${dir.map(entryRow).join('') || '<div class="muted center">目录为空</div>'}
    </div>
    ${readme ? `<h3 style="margin:18px 0 8px">README.md</h3><div class="card rendered">${readme}</div>` : ''}`;
}
const entryRow = e => `
  <div class="file-row" onclick="${e.type === 'dir' ? `repoState.dir='${esc(e.path)}';codeBrowse()` : `viewFile('${esc(e.path)}')`}">
    <span>${e.type === 'dir' ? '📁' : '📄'}</span>
    <span class="name">${esc(e.name)}</span>
    <span class="actions">
      ${e.type === 'file' ? `<button onclick="event.stopPropagation();editFile('${esc(e.path)}')">编辑</button>
      <button class="danger" onclick="event.stopPropagation();deleteFile('${esc(e.path)}')">删除</button>
      <a class="muted" style="font-size:12px" href="${esc(e.html_url)}" target="_blank">GitHub ↗</a>` : ''}
    </span>
  </div>`;

async function viewFile(path) {
  const { owner, repo } = repoState;
  const data = await api('GET', `repos/${owner}/${repo}/contents/${encPath(path)}`, { accept: 'application/vnd.github.html+json' });
  $('#tabBody').innerHTML = `
    <div class="actions-row">
      <button onclick="repoState.dir='';codeBrowse()">← 返回</button>
      <span class="mono">${esc(path)}</span>
      <span style="margin-left:auto"></span>
      <button onclick="editFile('${esc(path)}')">编辑</button>
      <button class="danger" onclick="deleteFile('${esc(path)}')">删除</button>
      <button onclick="apiRaw('${esc(path)}')">📋 源码</button>
    </div>
    <div class="card rendered">${typeof data === 'string' ? data : `<pre>${esc(atob(data.content))}</pre>`}</div>`;
}
async function apiRaw(path) {
  const { owner, repo } = repoState;
  const res = await fetch(`/api/gh/repos/${owner}/${repo}/contents/${encPath(path)}`, { headers: { 'X-Accept': 'application/vnd.github.raw+json' } });
  if (!res.ok) return toast('获取失败', 'err');
  const text = await res.text();
  openModal(`<h3>${esc(path)}</h3><pre style="max-height:60vh;overflow:auto;font-size:12px">${esc(text)}</pre>
    <div class="modal-actions"><button onclick="closeModal()">关闭</button></div>`);
}

async function getFileMeta(path) {
  const { owner, repo } = repoState;
  return api('GET', `repos/${owner}/${repo}/contents/${encPath(path)}`);
}

function editFile(path) {
  repoState.fileMode = 'edit'; repoState.fileData = { path };
  getFileMeta(path).then(meta => {
    repoState.fileData.sha = meta.sha;
    let content = '';
    if (meta.encoding === 'base64') content = decodeBase64(meta.content);
    openModal(`
      <h3>编辑 ${esc(path)}</h3>
      <div class="form-row"><label>提交信息 *</label><input id="f_msg" value="update ${esc(path.split('/').pop())}"></div>
      <div class="form-row"><label>内容</label><textarea id="f_content" rows="16">${esc(content)}</textarea></div>
      <div class="modal-actions">
        <button onclick="closeModal()">取消</button>
        <button class="primary" onclick="doCommitFile()">提交更改</button>
      </div>`);
  }).catch(e => toast(e.message, 'err'));
}

function decodeBase64(b64) {
  const bin = atob(b64);
  const bytes = Uint8Array.from(bin, c => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
const encodeBase64 = str => btoa(unescape(encodeURIComponent(str)));

function newFileForm() {
  const baseDir = repoState.dir ? repoState.dir + '/' : '';
  openModal(`
    <h3>新建文件（${esc(baseDir || '仓库根目录')}）</h3>
    <div class="form-row"><label>文件名（可含子目录）*</label><input id="f_name" placeholder="${esc(baseDir)}name.md"></div>
    <div class="form-row"><label>提交信息 *</label><input id="f_msg" value="Create new file"></div>
    <div class="form-row"><label>内容</label><textarea id="f_content" rows="14" placeholder="文件内容…"></textarea></div>
    <div class="modal-actions">
      <button onclick="closeModal()">取消</button>
      <button class="primary" onclick="doNewFile()">创建</button>
    </div>`);
}
function uploadForm() {
  openModal(`
    <h3>上传文件（${esc(repoState.dir ? repoState.dir + '/' : '仓库根目录')}）</h3>
    <div class="form-row"><label>选择文件 *</label><input type="file" id="f_upload"></div>
    <div class="form-row"><label>目标路径（默认文件名）</label><input id="f_path" placeholder="可选"></div>
    <div class="form-row"><label>提交信息 *</label><input id="f_msg" value="Upload file"></div>
    <div class="modal-actions"><button onclick="closeModal()">取消</button><button class="primary" onclick="doUpload()">上传</button></div>`);
}

async function doNewFile() {
  const name = $('#f_name').value.trim();
  if (!name) return toast('请输入文件名', 'err');
  const { owner, repo } = repoState;
  const path = (repoState.dir ? repoState.dir + '/' : '') + name;
  await api('PUT', `repos/${owner}/${repo}/contents/${encPath(path)}`, { body: { message: $('#f_msg').value, content: encodeBase64($('#f_content').value), branch: 'main' } }).catch(e => e);
  // main 分支可能不存在，尝试默认分支
  const r = await api('GET', `repos/${owner}/${repo}`);
  await api('PUT', `repos/${owner}/${repo}/contents/${encPath(path)}`, { body: { message: $('#f_msg').value, content: encodeBase64($('#f_content').value), branch: r.default_branch } });
  closeModal(); toast('文件已创建', 'ok'); codeBrowse();
}
async function doUpload() {
  const f = $('#f_upload').files[0];
  if (!f) return toast('请选择文件', 'err');
  const path = $('#f_path').value.trim() || f.name;
  const b64 = await fileToBase64(f);
  const r = await api('GET', `repos/${repoState.owner}/${repoState.repo}`);
  await api('PUT', `repos/${repoState.owner}/${repoState.repo}/contents/${encPath(path)}`, { body: { message: $('#f_msg').value, content: b64, branch: r.default_branch } });
  closeModal(); toast('上传成功', 'ok'); codeBrowse();
}
function fileToBase64(f) {
  return new Promise((res, rej) => {
    const rd = new FileReader();
    rd.onload = () => res(String(rd.result).split(',')[1]);
    rd.onerror = rej;
    rd.readAsDataURL(f);
  });
}
async function doCommitFile() {
  const { owner, repo } = repoState;
  const { path, sha } = repoState.fileData;
  const r = await api('GET', `repos/${owner}/${repo}`);
  const branch = r.default_branch;
  await api('PUT', `repos/${owner}/${repo}/contents/${encPath(path)}`, { body: { message: $('#f_msg').value, content: encodeBase64($('#f_content').value), sha, branch } });
  closeModal(); toast('已提交更改', 'ok'); viewFile(path);
}
async function deleteFile(path) {
  if (!confirm(`确定删除 ${path} ？`)) return;
  const { owner, repo } = repoState;
  const meta = await getFileMeta(path);
  const r = await api('GET', `repos/${owner}/${repo}`);
  await api('DELETE', `repos/${owner}/${repo}/contents/${encPath(path)}`, { body: { message: `delete ${path}`, sha: meta.sha, branch: r.default_branch } });
  toast('已删除', 'ok'); codeBrowse();
}

/* ---- Issues ---- */
let issueFilter = 'open';
async function issuesList() {
  const { owner, repo } = repoState;
  const issues = await api('GET', `repos/${owner}/${repo}/issues?state=${issueFilter}&per_page=50`);
  $('#tabBody').innerHTML = `
    <div class="actions-row">
      ${['open', 'closed', 'all'].map(s => `<button class="${s === issueFilter ? 'primary' : ''}" onclick="issueFilter='${s}';issuesList()">${s === 'open' ? '进行中' : s === 'closed' ? '已关闭' : '全部'}</button>`).join('')}
      <button style="margin-left:auto" class="primary" onclick="newIssueForm()">+ 新建 Issue</button>
    </div>
    <ul class="list card" style="padding:0">
      ${issues.map(i => `
        <li style="cursor:pointer" onclick="issueDetail(${i.number})">
          <div style="display:flex;gap:10px;align-items:flex-start">
            <span>${i.state === 'open' ? '<span style="color:var(--green)">🟢</span>' : '<span style="color:var(--red)">🔴</span>'}</span>
            <div style="flex:1">
              <div class="title">${esc(i.title)} <span class="muted">#${i.number}</span></div>
              <div class="desc">#${i.number} 由 ${esc(i.user.login)} 于 ${time(i.created_at)} 提出 · ${num(i.comments)} 条评论</div>
            </div>
            ${(i.labels || []).map(l => `<span class="badge" style="background:#${l.color}22;color:#${l.color}">${esc(l.name)}</span>`).join('')}
          </div>
        </li>`).join('') || '<div class="muted center">没有 Issues</div>'}
    </ul>`;
}

function newIssueForm() {
  openModal(`
    <h3>新建 Issue</h3>
    <div class="form-row"><label>标题 *</label><input id="i_title"></div>
    <div class="form-row"><label>内容</label><textarea id="i_body" rows="8"></textarea></div>
    <div class="modal-actions"><button onclick="closeModal()">取消</button><button class="primary" onclick="doNewIssue()">提交</button></div>`);
}
async function doNewIssue() {
  const { owner, repo } = repoState;
  const i = await api('POST', `repos/${owner}/${repo}/issues`, { body: { title: $('#i_title').value, body: $('#i_body').value } });
  closeModal(); toast('Issue 已创建', 'ok'); issueDetail(i.number);
}

async function issueDetail(n) {
  const { owner, repo } = repoState;
  const [i, comments] = await Promise.all([
    api('GET', `repos/${owner}/${repo}/issues/${n}`),
    api('GET', `repos/${owner}/${repo}/issues/${n}/comments`),
  ]);
  $('#tabBody').innerHTML = `
    <div class="actions-row">
      <button onclick="issuesList()">← 返回列表</button>
      <span style="font-weight:600;font-size:16px">${esc(i.title)} <span class="muted">#${n}</span></span>
      <span class="badge ${i.state === 'open' ? 'green' : 'red'}" style="margin-left:6px">${i.state}</span>
      <span style="margin-left:auto"></span>
      <button onclick="editIssue(${n})">编辑</button>
      ${i.state === 'open'
        ? `<button onclick="setIssueState(${n},'closed')">关闭</button>`
        : `<button class="primary" onclick="setIssueState(${n},'open')">重新打开</button>`}
    </div>
    <div class="card rendered" style="white-space:pre-wrap">${esc(i.body || '')}</div>
    <h3>评论 (${num(comments.length)})</h3>
    <ul class="list card" style="padding:0">
      ${comments.map(c => `
        <li><b>${esc(c.user.login)}</b> <span class="muted" style="font-size:12px">${time(c.created_at)}</span>
          <div style="white-space:pre-wrap">${esc(c.body || '')}</div>
        </li>`).join('') || '<div class="muted center">暂无评论</div>'}
    </ul>
    <div class="form-row"><label>添加评论</label><textarea id="c_body" rows="3"></textarea></div>
    <button class="primary" onclick="addComment(${n})">评论</button>`;
}
async function setIssueState(n, state) {
  await api('PATCH', `repos/${repoState.owner}/${repoState.repo}/issues/${n}`, { body: { state } });
  toast(state === 'closed' ? '已关闭' : '已重新打开', 'ok'); issueDetail(n);
}
async function addComment(n) {
  await api('POST', `repos/${repoState.owner}/${repoState.repo}/issues/${n}/comments`, { body: { body: $('#c_body').value } });
  toast('评论成功', 'ok'); issueDetail(n);
}
function editIssue(n) {
  const { owner, repo } = repoState;
  api('GET', `repos/${owner}/${repo}/issues/${n}`).then(i => {
    openModal(`
      <h3>编辑 Issue #${n}</h3>
      <div class="form-row"><label>标题 *</label><input id="e_title" value="${esc(i.title)}"></div>
      <div class="form-row"><label>内容</label><textarea id="e_body" rows="8">${esc(i.body || '')}</textarea></div>
      <div class="modal-actions"><button onclick="closeModal()">取消</button><button class="primary" onclick="doEditIssue(${n})">保存</button></div>`);
  });
}
async function doEditIssue(n) {
  await api('PATCH', `repos/${repoState.owner}/${repoState.repo}/issues/${n}`, { body: { title: $('#e_title').value, body: $('#e_body').value } });
  closeModal(); toast('已保存', 'ok'); issueDetail(n);
}

/* ---- Pull Requests ---- */
let prFilter = 'open';
async function pullsList() {
  const { owner, repo } = repoState;
  const prs = await api('GET', `repos/${owner}/${repo}/pulls?state=${prFilter}&per_page=50`);
  $('#tabBody').innerHTML = `
    <div class="actions-row">
      ${['open', 'closed', 'all'].map(s => `<button class="${s === prFilter ? 'primary' : ''}" onclick="prFilter='${s}';pullsList()">${s === 'open' ? '进行中' : s === 'closed' ? '已合并/关闭' : '全部'}</button>`).join('')}
      <button style="margin-left:auto" class="primary" onclick="newPRForm()">+ 新建 Pull Request</button>
    </div>
    <ul class="list card" style="padding:0">
      ${prs.map(pr => `
        <li style="cursor:pointer" onclick="prDetail(${pr.number})">
          <div style="display:flex;gap:10px">
            <span style="color:${pr.state === 'open' ? 'var(--green)' : 'var(--red)'}">${pr.state === 'open' ? '↔' : '✓'}</span>
            <div style="flex:1">
              <div class="title">${esc(pr.title)} <span class="muted">#${pr.number}</span></div>
              <div class="desc">${esc(pr.head.ref)} → ${esc(pr.base.ref)} · 由 ${esc(pr.user.login)} · ${time(pr.created_at)} · ${num(pr.comments)} 条评论</div>
            </div>
          </div>
        </li>`).join('') || '<div class="muted center">没有 Pull Requests</div>'}
    </ul>`;
}

async function newPRForm() {
  const { owner, repo } = repoState;
  const r = await api('GET', `repos/${owner}/${repo}`);
  const branches = await api('GET', `repos/${owner}/${repo}/branches?per_page=100`);
  openModal(`
    <h3>新建 Pull Request</h3>
    <div class="form-inline">
      <select id="pr_base">${branches.map(b => `<option ${b.name === r.default_branch ? 'selected' : ''}>${esc(b.name)}</option>`).join('')}</select>
      <span class="muted">←</span>
      <select id="pr_head">${branches.map(b => `<option>${esc(b.name)}</option>`).join('')}</select>
    </div>
    <div class="form-row"><label>标题 *</label><input id="pr_title"></div>
    <div class="form-row"><label>内容</label><textarea id="pr_body" rows="6"></textarea></div>
    <div class="modal-actions"><button onclick="closeModal()">取消</button><button class="primary" onclick="doNewPR()">创建 PR</button></div>`);
}
async function doNewPR() {
  const { owner, repo } = repoState;
  const pr = await api('POST', `repos/${owner}/${repo}/pulls`, { body: { title: $('#pr_title').value, body: $('#pr_body').value, head: $('#pr_head').value, base: $('#pr_base').value } });
  closeModal(); toast('PR 已创建', 'ok'); prDetail(pr.number);
}

async function prDetail(n) {
  const { owner, repo } = repoState;
  const [pr, files, comments] = await Promise.all([
    api('GET', `repos/${owner}/${repo}/pulls/${n}`),
    api('GET', `repos/${owner}/${repo}/pulls/${n}/files`),
    api('GET', `repos/${owner}/${repo}/issues/${n}/comments`),
  ]);
  $('#tabBody').innerHTML = `
    <div class="actions-row">
      <button onclick="pullsList()">← 返回列表</button>
      <span style="font-weight:600;font-size:16px">${esc(pr.title)} <span class="muted">#${n}</span></span>
      <span class="badge ${pr.state === 'open' ? 'green' : 'red'}">${pr.state}</span>
      ${pr.merged ? '<span class="badge accent">已合并</span>' : ''}
      <span class="muted">${esc(pr.head.ref)} → ${esc(pr.base.ref)}</span>
      <span style="margin-left:auto"></span>
      ${pr.state === 'open' ? `
        <button onclick="reviewPR(${n},'COMMENT')">💬 评论</button>
        <button onclick="reviewPR(${n},'APPROVE')" class="primary">✓ 批准</button>
        <button onclick="reviewPR(${n},'REQUEST_CHANGES')" class="danger">✗ 请求更改</button>
        <button onclick="mergePR(${n})" class="primary" style="background:#8957e5">合并</button>` : ''}
    </div>
    <div class="card rendered" style="white-space:pre-wrap">${esc(pr.body || '')}</div>
    <h3>变更文件 (${num(files.length)})</h3>
    <div class="card" style="padding:6px">
      ${files.map(f => `<div class="file-row"><span>📄</span><span class="name">${esc(f.filename)}</span><span style="color:var(--green)">+${f.additions}</span><span style="color:var(--red)">-${f.deletions}</span></div>`).join('') || '<div class="muted">无变更</div>'}
    </div>
    <h3>评论</h3>
    <ul class="list card" style="padding:0">
      ${comments.map(c => `<li><b>${esc(c.user.login)}</b> <span class="muted" style="font-size:12px">${time(c.created_at)}</span><div style="white-space:pre-wrap">${esc(c.body || '')}</div></li>`).join('') || '<div class="muted center">暂无评论</div>'}
    </ul>
    <div class="form-row"><label>评论</label><textarea id="pr_comment" rows="3"></textarea></div>
    <button class="primary" onclick="addPRComment(${n})">评论</button>`;
}
async function reviewPR(n, event) {
  const body = event === 'COMMENT' && $('#pr_comment').value ? $('#pr_comment').value : (event === 'APPROVE' ? 'LGTM ✅' : '请修改以下问题');
  await api('POST', `repos/${repoState.owner}/${repoState.repo}/pulls/${n}/reviews`, { body: { body, event } });
  toast({ COMMENT: '已评论', APPROVE: '已批准 ✅', REQUEST_CHANGES: '已请求更改' }[event], 'ok');
  prDetail(n);
}
async function addPRComment(n) {
  await api('POST', `repos/${repoState.owner}/${repoState.repo}/issues/${n}/comments`, { body: { body: $('#pr_comment').value } });
  toast('评论成功', 'ok'); prDetail(n);
}
async function mergePR(n) {
  const method = prompt('合并方式：merge / squash / rebase（默认 squash）', 'squash');
  if (!method) return;
  await api('PUT', `repos/${repoState.owner}/${repoState.repo}/pulls/${n}/merge`, { body: { merge_method: method } });
  toast('已合并 🎉', 'ok'); prDetail(n);
}

/* ---- 提交 ---- */
async function commitsList() {
  const { owner, repo } = repoState;
  const commits = await api('GET', `repos/${owner}/${repo}/commits?per_page=30`);
  $('#tabBody').innerHTML = `
    <div class="card" style="padding:0">
      ${commits.map(c => `
        <div class="file-row" onclick="commitDetail('${esc(c.sha)}')">
          <span>🕘</span>
          <div style="flex:1">
            <div class="title">${esc(c.commit.message.split('\n')[0])}</div>
            <div class="desc">${esc(c.commit.author.name)} · ${time(c.commit.author.date)} · <span class="mono" style="font-size:11px">${c.sha.slice(0, 7)}</span></div>
          </div>
        </div>`).join('') || '<div class="muted center">暂无提交</div>'}
    </div>`;
}
async function commitDetail(sha) {
  const { owner, repo } = repoState;
  const c = await api('GET', `repos/${owner}/${repo}/commits/${sha}`);
  $('#tabBody').innerHTML = `
    <div class="actions-row"><button onclick="commitsList()">← 返回</button>
      <span class="mono" style="font-weight:600">${esc(c.commit.message)}</span>
      <span class="muted">${esc(c.commit.author.name)} · ${time(c.commit.author.date)} · ${sha.slice(0, 7)}</span>
    </div>
    ${c.files.map(f => `
      <div class="commit-box">
        <div class="commit-head" style="background:var(--panel2)">${esc(f.filename)} <span style="float:right"><span style="color:var(--green)">+${f.additions}</span> <span style="color:var(--red)">-${f.deletions}</span></span></div>
        ${f.patch ? `<pre>${esc(f.patch).split('\n').map(l => l.startsWith('+') ? `<span class="diff-line add">${esc(l)}</span>` : l.startsWith('-') ? `<span class="diff-line del">${esc(l)}</span>` : esc(l)).join('\n')}</pre>` : '<div class="muted center">二进制文件</div>'}
      </div>`).join('') || '<div class="muted center">无文件变更</div>'}`;
}

/* ---- 分支 ---- */
async function branchesList() {
  const { owner, repo } = repoState;
  const r = await api('GET', `repos/${owner}/${repo}`);
  const branches = await api('GET', `repos/${owner}/${repo}/branches?per_page=100`);
  const protections = await Promise.all(branches.map(b => api('GET', `repos/${owner}/${repo}/branches/${encodeURIComponent(b.name)}/protection`).catch(() => null)));
  $('#tabBody').innerHTML = `
    <div class="actions-row">
      <button class="primary" style="margin-left:auto" onclick="newBranchForm()">+ 新建分支</button>
    </div>
    <div class="card" style="padding:0">
      ${branches.map((b, i) => {
        const prot = protections[i];
        return `
        <div class="file-row">
          <span>🌿</span>
          <span class="name">${esc(b.name)} ${b.name === r.default_branch ? '<span class="badge accent">默认</span>' : ''}</span>
          <span class="mono muted" style="font-size:11px">${b.commit.sha.slice(0, 7)}</span>
          ${prot ? '<span class="badge yellow">受保护</span>' : ''}
          ${prot ? `<button onclick="protectionInfo('${esc(b.name)}')">规则</button>` : ''}
          ${b.name !== r.default_branch ? `<button class="danger" onclick="deleteBranch('${esc(b.name)}')">删除</button>` : ''}
        </div>`;
      }).join('') || '<div class="muted center">暂无分支</div>'}
    </div>`;
}
function newBranchForm() {
  openModal(`
    <h3>新建分支</h3>
    <div class="form-row"><label>分支名 *</label><input id="b_name" placeholder="feature/xxx"></div>
    <div class="form-row"><label>基于分支/提交 SHA</label><input id="b_from" placeholder="默认主分支"></div>
    <div class="modal-actions"><button onclick="closeModal()">取消</button><button class="primary" onclick="doNewBranch()">创建</button></div>`);
}
async function doNewBranch() {
  const { owner, repo } = repoState;
  const name = $('#b_name').value.trim();
  const from = $('#b_from').value.trim();
  const r = await api('GET', `repos/${owner}/${repo}`);
  const ref = await api('GET', `git/ref/heads/${from || r.default_branch}`); // 注意：/git/ref 开头无需 repos
  await api('POST', `repos/${owner}/${repo}/git/refs`, { body: { ref: `refs/heads/${name}`, sha: ref.object.sha } });
  closeModal(); toast('分支已创建', 'ok'); branchesList();
}
async function deleteBranch(name) {
  if (!confirm(`确定删除分支 ${name} ？`)) return;
  await api('DELETE', `repos/${repoState.owner}/${repoState.repo}/git/refs/heads/${encodeURIComponent(name)}`);
  toast('已删除', 'ok'); branchesList();
}
async function protectionInfo(name) {
  const { owner, repo } = repoState;
  const p = await api('GET', `repos/${owner}/${repo}/branches/${encodeURIComponent(name)}/protection`);
  openModal(`
    <h3>分支保护规则：${esc(name)}</h3>
    <ul class="list">
      <li>必须 PR 才能合并：${p.required_pull_request_reviews ? '✅ 是' : '❌ 否'}</li>
      <li>状态检查：${p.required_status_checks ? `${p.required_status_checks.required_status_checks.map(c => esc(c.context)).join(', ') || '✅ 有检查'}` : '❌ 无'}</li>
      <li>强制推送：${p.allow_force_pushes && p.allow_force_pushes.enabled ? '❌ 允许' : '✅ 禁止'}</li>
      <li>分支被删除：${p.allow_deletions && p.allow_deletions.enabled ? '✅ 允许' : '❌ 禁止'}</li>
    </ul>
    <div class="modal-actions"><button onclick="closeModal()">关闭</button></div>`);
}

/* ---- Releases ---- */
async function releasesList() {
  const { owner, repo } = repoState;
  const releases = await api('GET', `repos/${owner}/${repo}/releases?per_page=30`);
  $('#tabBody').innerHTML = `
    <div class="actions-row"><button class="primary" style="margin-left:auto" onclick="newReleaseForm()">+ 新建 Release</button></div>
    <ul class="list card" style="padding:0">
      ${releases.map(rel => `
        <li>
          <div class="title">🏷️ ${esc(rel.tag_name)} <span class="muted">${esc(rel.name || '')}</span></div>
          <div class="desc">发布者 ${esc(rel.author.login)} · ${time(rel.published_at)} ${rel.prerelease ? '<span class="badge yellow">预发布</span>' : ''}</div>
          <div style="white-space:pre-wrap;margin-top:6px">${esc(rel.body || '')}</div>
        </li>`).join('') || '<div class="muted center">暂无 Release</div>'}
    </ul>`;
}
function newReleaseForm() {
  openModal(`
    <h3>新建 Release</h3>
    <div class="form-row"><label>标签 *</label><input id="rel_tag" placeholder="v1.0.0"></div>
    <div class="form-row"><label>目标分支/提交</label><input id="rel_branch" placeholder="默认主分支"></div>
    <div class="form-row"><label>标题</label><input id="rel_name"></div>
    <div class="form-row"><label>说明</label><textarea id="rel_body" rows="6"></textarea></div>
    <div class="form-row"><label>类型</label><select id="rel_prerelease"><option value="false">正式版</option><option value="true">预发布</option></select></div>
    <div class="modal-actions"><button onclick="closeModal()">取消</button><button class="primary" onclick="doNewRelease()">发布</button></div>`);
}
async function doNewRelease() {
  const { owner, repo } = repoState;
  const r = await api('GET', `repos/${owner}/${repo}`);
  await api('POST', `repos/${owner}/${repo}/releases`, { body: {
    tag_name: $('#rel_tag').value.trim(), target_commitish: $('#rel_branch').value.trim() || r.default_branch,
    name: $('#rel_name').value, body: $('#rel_body').value, prerelease: $('#rel_prerelease').value === 'true',
  } });
  closeModal(); toast('Release 已发布 🎉', 'ok'); releasesList();
}

/* ---- Actions ---- */
let runsFilter = '';
async function actionsList() {
  const { owner, repo } = repoState;
  const [workflows, runs] = await Promise.all([
    api('GET', `repos/${owner}/${repo}/actions/workflows?per_page=50`),
    api('GET', `repos/${owner}/${repo}/actions/runs?per_page=20`),
  ]);
  $('#tabBody').innerHTML = `
    <h3>工作流 (${num(workflows.total_count)})</h3>
    <div class="card" style="padding:0">
      ${workflows.workflows.map(w => `
        <div class="file-row">
          <span>⚙️</span>
          <span class="name">${esc(w.name)}</span>
          <span class="mono muted" style="font-size:11px">${esc(w.path)}</span>
          ${w.state === 'active' ? '<span class="badge green">active</span>' : '<span class="badge yellow">disabled</span>'}
        </div>`).join('') || '<div class="muted center">暂无工作流</div>'}
    </div>
    <h3 style="margin-top:16px">最近运行 (${num(runs.total_count)})</h3>
    <div class="card" style="padding:0">
      ${runs.workflow_runs.map(run => `
        <div class="file-row" onclick="runDetail(${run.id})">
          <span>${runIcon(run.status)}</span>
          <div style="flex:1">
            <div class="title">${esc(run.name)} <span class="mono muted" style="font-size:11px">#${run.run_number}</span></div>
            <div class="desc">${esc(run.head_branch)} · ${esc(run.head_sha.slice(0, 7))} · ${time(run.created_at)}</div>
          </div>
          <span class="badge ${run.conclusion === 'success' ? 'green' : run.conclusion === 'failure' ? 'red' : ''}">${esc(run.conclusion || run.status)}</span>
        </div>`).join('') || '<div class="muted center">暂无运行记录</div>'}
    </div>`;
}
function runIcon(status) {
  if (status === 'completed') return '<span style="color:var(--green)">✓</span>';
  if (status === 'in_progress') return '<span style="color:var(--accent)">⟳</span>';
  return '⏸';
}
async function runDetail(id) {
  const { owner, repo } = repoState;
  const run = await api('GET', `repos/${owner}/${repo}/actions/runs/${id}`);
  const jobs = await api('GET', `repos/${owner}/${repo}/actions/runs/${id}/jobs`);
  const logs = await fetch(`/api/gh/repos/${owner}/${repo}/actions/runs/${id}/logs`).then(r => r.text()).catch(() => '(日志不可用)');
  $('#tabBody').innerHTML = `
    <div class="actions-row"><button onclick="actionsList()">← 返回</button>
      <span style="font-weight:600">${esc(run.name)} <span class="mono muted">#${run.run_number}</span></span>
      <span class="badge ${run.conclusion === 'success' ? 'green' : run.conclusion === 'failure' ? 'red' : ''}">${esc(run.conclusion || run.status)}</span>
    </div>
    <div class="card"><div class="muted">分支 ${esc(run.head_branch)} · 提交 ${run.head_sha.slice(0, 7)} · 触发者 ${esc(run.triggering_actor ? run.triggering_actor.login : '')} · ${time(run.created_at)}</div>
      <a href="${esc(run.html_url)}" target="_blank">在 GitHub 查看 ↗</a></div>
    <h3>Jobs</h3>
    <div class="card" style="padding:0">
      ${jobs.jobs.map(j => `<div class="file-row"><span>⚙️</span><span class="name">${esc(j.name)}</span>
        <span class="badge ${j.conclusion === 'success' ? 'green' : j.conclusion === 'failure' ? 'red' : ''}">${esc(j.conclusion || j.status)}</span>
        <span class="muted" style="font-size:12px">${time(j.started_at)}</span></div>`).join('') || '<div class="muted center">无 Job</div>'}
    </div>
    <h3>日志（前 400 行）</h3>
    <pre class="card" style="max-height:420px;overflow:auto;font-size:12px">${esc(logs.split('\n').slice(0, 400).join('\n'))}</pre>`;
}

/* ================= 页面：全局 Issues / PRs ================= */
async function renderGlobalIssues() {
  $('#main').innerHTML = `<div class="center"><span class="spinner"></span></div>`;
  const q = await api('GET', `search/issues?q=is:issue author:@me&sort=updated&order=desc&per_page=30`);
  $('#main').innerHTML = `
    <h2>🐛 我的 Issues</h2>
    <div class="card" style="padding:0">${q.items.map(i => `
      <div class="file-row" onclick="goto('/repo/${esc(i.repository_url.split('/repos/')[1])}?tab=issues')">
        <span class="name"><b>${esc(i.title)}</b> <span class="muted">#${i.number}</span></span>
        <span class="muted" style="font-size:12px">${esc(i.repository_url.split('/repos/')[1])}</span>
        <span class="badge ${i.state === 'open' ? 'green' : 'red'}">${i.state}</span>
      </div>`).join('') || '<div class="muted center">暂无 Issues</div>'}</div>`;
}

async function renderGlobalPulls() {
  $('#main').innerHTML = `<div class="center"><span class="spinner"></span></div>`;
  const q = await api('GET', `search/issues?q=is:pr author:@me&sort=updated&order=desc&per_page=30`);
  $('#main').innerHTML = `
    <h2>🔀 我的 Pull Requests</h2>
    <div class="card" style="padding:0">${q.items.map(i => `
      <div class="file-row" onclick="goto('/repo/${esc(i.repository_url.split('/repos/')[1])}?tab=pulls')">
        <span class="name"><b>${esc(i.title)}</b> <span class="muted">#${i.number}</span></span>
        <span class="muted" style="font-size:12px">${esc(i.repository_url.split('/repos/')[1])}</span>
        <span class="badge ${i.state === 'open' ? 'green' : 'red'}">${i.state}</span>
      </div>`).join('') || '<div class="muted center">暂无 PR</div>'}</div>`;
}

/* ================= 页面：搜索 ================= */
async function renderSearch() {
  const { params } = parseHash();
  const q = params.get('q') || '';
  let type = params.get('type') || 'repositories';
  $('#main').innerHTML = `
    <h2>🔍 搜索</h2>
    <div class="actions-row">
      <input id="searchQ" value="${esc(q)}" placeholder="关键词…" style="flex:1;max-width:400px" onkeydown="if(event.key==='Enter')doSearch()">
      <select id="searchType" style="width:150px">
        <option value="repositories" ${type === 'repositories' ? 'selected' : ''}>仓库</option>
        <option value="issues" ${type === 'issues' ? 'selected' : ''}>Issues</option>
        <option value="pullrequests" ${type === 'pullrequests' ? 'selected' : ''}>Pull Requests</option>
        <option value="users" ${type === 'users' ? 'selected' : ''}>用户</option>
        <option value="code" ${type === 'code' ? 'selected' : ''}>代码</option>
      </select>
      <button class="primary" onclick="doSearch()">搜索</button>
    </div>
    <div id="searchResult"></div>`;
  if (q) doSearch();
}
function doSearch() {
  const q = $('#searchQ').value.trim();
  const type = $('#searchType').value;
  if (!q) return;
  goto('/search?q=' + encodeURIComponent(q) + '&type=' + type);
  performSearch(q, type);
}
async function performSearch(q, type) {
  const box = $('#searchResult');
  box.innerHTML = `<div class="center"><span class="spinner"></span></div>`;
  let data;
  if (type === 'code') data = await api('GET', `search/code?q=${encodeURIComponent(q)}&per_page=30`);
  else if (type === 'pullrequests') data = await api('GET', `search/issues?q=${encodeURIComponent(q)}+type:pr&per_page=30`);
  else data = await api('GET', `search/${type}?q=${encodeURIComponent(q)}&per_page=30`);
  box.innerHTML = `<div class="muted" style="margin-bottom:10px">共 ${num(data.total_count)} 条结果</div>
    <div class="card" style="padding:0">` + (data.items || []).map(item => searchItem(type, item)).join('') || `<div class="muted center">无结果</div>` + `</div>`;
}
function searchItem(type, it) {
  if (type === 'repositories') return `<div class="file-row" onclick="goto('/repo/${esc(it.owner.login)}/${esc(it.name)}')"><span class="name"><b>${esc(it.full_name)}</b></span><span class="muted">${esc(it.description || '')}</span><span class="badge">⭐ ${num(it.stargazers_count)}</span></div>`;
  if (type === 'users') return `<div class="file-row" onclick="goto('/explorer')"><span class="name"><b>${esc(it.login)}</b></span><span class="muted">${esc(it.type)}</span></div>`;
  if (type === 'issues') return `<div class="file-row" onclick="goto('/repo/${esc(it.repository_url.split('/repos/')[1])}?tab=issues')"><span class="name"><b>${esc(it.title)}</b> <span class="muted">#${it.number}</span></span><span class="muted" style="font-size:12px">${esc(it.repository_url.split('/repos/')[1])}</span><span class="badge ${it.state === 'open' ? 'green' : 'red'}">${it.state}</span></div>`;
  if (type === 'pullrequests') return `<div class="file-row" onclick="goto('/repo/${esc(it.repository_url.split('/repos/')[1])}?tab=pulls')"><span class="name"><b>${esc(it.title)}</b></span><span class="muted" style="font-size:12px">${esc(it.repository_url.split('/repos/')[1])}</span></div>`;
  return `<div class="file-row"><span class="name mono" style="font-size:12px">${esc(it.path)}</span><span class="muted" style="font-size:12px">${esc(it.repository.full_name)}</span></div>`;
}

/* ================= 页面：通知 ================= */
async function renderNotifications() {
  $('#main').innerHTML = `<div class="center"><span class="spinner"></span></div>`;
  const notifs = await api('GET', 'notifications?per_page=50');
  $('#main').innerHTML = `
    <div class="actions-row">
      <h2 style="margin:0">🔔 通知 (${num(notifs.length)} 未读)</h2>
      <button class="primary" style="margin-left:auto" onclick="markAllRead()">全部标记已读</button>
    </div>
    <ul class="list card" style="padding:0">
      ${notifs.map(n => {
        const repo = n.repository.full_name;
        const target = n.subject.type === 'Issue' ? `${n.subject.url.split('/issues/')[1]}` : '';
        return `<li style="display:flex;align-items:center;gap:12px">
          <div style="flex:1;cursor:pointer" onclick="openNotif('${esc(repo)}', '${esc(n.subject.type)}', '${target}')">
            <div class="title">${esc(n.subject.title)}</div>
            <div class="desc">${esc(repo)} · ${esc(n.subject.type)} · ${time(n.updated_at)}</div>
          </div>
          <button onclick="markOneRead('${esc(n.id)}', this)">标记已读</button>
        </li>`;
      }).join('') || '<div class="muted center">🎉 没有未读通知</div>'}
    </ul>`;
}
function openNotif(repo, type, target) {
  const tab = type === 'Issue' ? 'issues' : type === 'PullRequest' ? 'pulls' : 'code';
  goto(`/repo/${repo}?tab=${tab}`);
}
async function markAllRead() {
  await api('PUT', 'notifications', { body: {} });
  toast('已全部标记为已读', 'ok'); renderNotifications();
}
async function markOneRead(id, btn) {
  btn.disabled = true;
  await api('PATCH', `notifications/threads/${id}`);
  toast('已标记', 'ok'); renderNotifications();
}

/* ================= 页面：Gists ================= */
async function renderGists() {
  $('#main').innerHTML = `<div class="center"><span class="spinner"></span></div>`;
  const gists = await api('GET', 'gists?per_page=30');
  $('#main').innerHTML = `
    <div class="actions-row">
      <h2 style="margin:0">📝 我的 Gists</h2>
      <button class="primary" style="margin-left:auto" onclick="newGistForm()">+ 新建 Gist</button>
    </div>
    <ul class="list card" style="padding:0">
      ${gists.map(g => `
        <li>
          <div class="title" style="cursor:pointer" onclick="viewGist('${esc(g.id)}')">${esc(g.description || Object.keys(g.files)[0] || '无标题')}</div>
          <div class="desc">${num(g.files ? Object.keys(g.files).length : 0)} 个文件 · ${time(g.created_at)} ${g.public ? '<span class="badge green">公开</span>' : '<span class="badge yellow">私密</span>'}</div>
        </li>`).join('') || '<div class="muted center">暂无 Gist</div>'}
    </ul>`;
}
function newGistForm() {
  openModal(`
    <h3>新建 Gist</h3>
    <div class="form-row"><label>描述</label><input id="g_desc"></div>
    <div class="form-row"><label>文件名 *</label><input id="g_file" placeholder="snippet.js"></div>
    <div class="form-row"><label>内容 *</label><textarea id="g_body" rows="8"></textarea></div>
    <div class="form-row"><label>可见性</label><select id="g_public"><option value="true">公开</option><option value="false">私密</option></select></div>
    <div class="modal-actions"><button onclick="closeModal()">取消</button><button class="primary" onclick="doNewGist()">创建</button></div>`);
}
async function doNewGist() {
  const fileName = $('#g_file').value.trim();
  if (!fileName) return toast('请输入文件名', 'err');
  const g = await api('POST', 'gists', { body: { description: $('#g_desc').value, public: $('#g_public').value === 'true', files: { [fileName]: { content: $('#g_body').value } } } });
  closeModal(); toast('Gist 已创建', 'ok'); viewGist(g.id);
}
async function viewGist(id) {
  const g = await api('GET', `gists/${id}`);
  openModal(`
    <h3>${esc(g.description || 'Gist')}</h3>
    ${Object.entries(g.files).map(([name, f]) => `<div class="form-row"><label>${esc(name)}</label>
      <pre style="background:#0d1117;border:1px solid var(--border);border-radius:6px;padding:10px;max-height:50vh;overflow:auto;font-size:12px">${esc(f.content || '')}</pre></div>`).join('')}
    <div class="modal-actions"><a href="${esc(g.html_url)}" target="_blank" style="margin-right:auto">在 GitHub 打开 ↗</a><button onclick="closeModal()">关闭</button></div>`);
}

/* ================= 页面：Webhook 事件 ================= */
async function renderEvents() {
  const evts = await (await fetch('/api/webhooks/events')).json();
  $('#main').innerHTML = `
    <h2>⚡ Webhook 事件日志</h2>
    <p class="muted">应用安装到账号后，GitHub 会把仓库事件推送到 <code>${esc(location.origin)}/webhook</code>（需公网可达或内网穿透）。机器人开启时新 Issue / 新 PR 会自动回复。</p>
    <table class="tbl">
      <tr><th>时间</th><th>事件</th><th>动作</th><th>仓库</th><th>触发者</th><th>ID</th></tr>
      ${evts.map(e => `<tr><td>${time(e.at)}</td><td><span class="badge accent">${esc(e.event)}</span></td><td>${esc(e.action)}</td><td>${esc(e.repo)}</td><td>${esc(e.sender)}</td><td class="mono muted">${e.id}</td></tr>`).join('') || `<tr><td colspan="6" class="center muted">还没有收到事件</td></tr>`}
    </table>`;
}

/* ================= 页面：API 调试台 ================= */
async function renderExplorer() {
  $('#main').innerHTML = `
    <h2>🧪 API 调试台</h2>
    <p class="muted">任意调用 GitHub REST API（以当前登录身份）。这是网页版全部操作的后端能力入口。</p>
    <div class="card">
      <div class="form-inline" style="margin-bottom:10px">
        <select id="ex_method" style="width:110px">
          <option>GET</option><option>POST</option><option>PUT</option><option>PATCH</option><option>DELETE</option>
        </select>
        <input id="ex_path" placeholder="例如 user/repos 或 repos/octocat/Hello-World/issues" onkeydown="if(event.key==='Enter')exSend()">
        <button class="primary" onclick="exSend()">发送</button>
      </div>
      <div class="form-row"><label>Accept 覆盖（可选，例如 application/vnd.github.html+json 渲染 README）</label><input id="ex_accept" placeholder="留空默认"></div>
      <div class="form-row"><label>请求体（JSON，可选）</label><textarea id="ex_body" rows="6" placeholder='{"title":"...","body":"..."}'></textarea></div>
    </div>
    <pre id="ex_result" class="card" style="min-height:200px;max-height:60vh;overflow:auto;font-size:12.5px">响应将显示在这里</pre>`;
}
async function exSend() {
  const method = $('#ex_method').value;
  const path = $('#ex_path').value.trim().replace(/^\/+/, '');
  if (!path) return toast('请输入 API 路径', 'err');
  const accept = $('#ex_accept').value.trim();
  let body;
  try { body = $('#ex_body').value.trim() ? JSON.parse($('#ex_body').value) : undefined; }
  catch { return toast('请求体不是合法 JSON', 'err'); }
  const result = $('#ex_result');
  result.textContent = '请求中…';
  try {
    const data = await api(method, path, { body, accept });
    result.textContent = JSON.stringify(data, null, 2).slice(0, 20000) || '(空响应)';
  } catch (e) {
    result.textContent = '❌ ' + e.message;
  }
}

/* ================= 启动 ================= */
window.goto = goto;
window.closeModal = closeModal;
window.activateInstall = activateInstall;
window.filterRepos = filterRepos;
window.starRepo = starRepo;
window.newRepoForm = newRepoForm;
window.doCreateRepo = doCreateRepo;
window.toggleStar = toggleStar;
window.toggleWatch = toggleWatch;
window.codeBrowse = codeBrowse;
window.viewFile = viewFile;
window.apiRaw = apiRaw;
window.editFile = editFile;
window.deleteFile = deleteFile;
window.doCommitFile = doCommitFile;
window.newFileForm = newFileForm;
window.doNewFile = doNewFile;
window.uploadForm = uploadForm;
window.doUpload = doUpload;
window.issueFilter = null; // placeholder 防误
window.issuesList = issuesList;
window.newIssueForm = newIssueForm;
window.doNewIssue = doNewIssue;
window.issueDetail = issueDetail;
window.setIssueState = setIssueState;
window.addComment = addComment;
window.editIssue = editIssue;
window.doEditIssue = doEditIssue;
window.prFilter = prFilter;
window.pullsList = pullsList;
window.newPRForm = newPRForm;
window.doNewPR = doNewPR;
window.prDetail = prDetail;
window.reviewPR = reviewPR;
window.addPRComment = addPRComment;
window.mergePR = mergePR;
window.commitsList = commitsList;
window.commitDetail = commitDetail;
window.branchesList = branchesList;
window.newBranchForm = newBranchForm;
window.doNewBranch = doNewBranch;
window.deleteBranch = deleteBranch;
window.protectionInfo = protectionInfo;
window.releasesList = releasesList;
window.newReleaseForm = newReleaseForm;
window.doNewRelease = doNewRelease;
window.actionsList = actionsList;
window.runDetail = runDetail;
window.renderSearch = renderSearch;
window.doSearch = doSearch;
window.markAllRead = markAllRead;
window.markOneRead = markOneRead;
window.renderGists = renderGists;
window.newGistForm = newGistForm;
window.doNewGist = doNewGist;
window.viewGist = viewGist;
window.exSend = exSend;

boot();
