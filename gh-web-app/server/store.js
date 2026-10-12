'use strict';

// 内存会话与 Webhook 事件存储（单实例足够；生产环境可替换为 Redis / DB）
const sessions = new Map(); // sid -> { type: 'user'|'app', token, user, createdAt }
const events = [];          // webhook 事件日志（新事件在前）
const EVENT_CAP = 200;

function getToken(sid) {
  const s = sessions.get(sid);
  return s && s.token ? s : null;
}

function setSession(sid, data) {
  sessions.set(sid, data);
}

function dropSession(sid) {
  sessions.delete(sid);
}

function addEvent(evt) {
  events.unshift(evt);
  if (events.length > EVENT_CAP) events.length = EVENT_CAP;
}

function listEvents(limit = 50) {
  return events.slice(0, limit);
}

module.exports = { sessions, events, getToken, setSession, dropSession, addEvent, listEvents };
