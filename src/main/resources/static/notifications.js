 * notifications.js — global bell + toast + WebSocket client for HRMS + PM.
 * Loaded by PM pages (via pm-core.js) and by the employee dashboard.
 * Exposes: window.Notifications
 */
(function () {
  'use strict';

  const EMP_ID = () => {
    // PM pages store the string in PM.user.employeeId; dashboard uses a hidden input
    if (window.PM && window.PM.user && window.PM.user.employeeId) return window.PM.user.employeeId;
    const el = document.getElementById('currentEmployeeId');
    return el ? el.value : null;
  };

  const state = {
    list: [],
    unread: 0,
    socket: null,
    connected: false
  };

  /* ---------- TOAST ---------- */

  function ensureToastHost() {
    let host = document.getElementById('notifToastHost');
    if (!host) {
      host = document.createElement('div');
      host.id = 'notifToastHost';
      host.style.cssText = 'position:fixed;top:14px;right:14px;z-index:9999;display:flex;flex-direction:column;gap:8px;max-width:360px;pointer-events:none;';
      document.body.appendChild(host);
    }
    return host;
  }

  function toast(n) {
    const host = ensureToastHost();
    const el = document.createElement('div');
    const tone = n.priority === 'HIGH' ? '#ef4444'
              : n.priority === 'LOW'  ? '#64748b'
              : '#6366f1';
    el.style.cssText = `
      pointer-events:auto;background:#1e293b;border:1px solid #2a3950;border-left:3px solid ${tone};
      border-radius:10px;padding:10px 12px;color:#f1f5f9;font-family:Inter,sans-serif;font-size:12.5px;
      box-shadow:0 12px 40px rgba(0,0,0,.45);transform:translateX(120%);transition:transform .28s ease;
      cursor:pointer;display:flex;gap:10px;align-items:flex-start;`;
    el.innerHTML = `
      <div style="width:26px;height:26px;border-radius:50%;background:${tone}22;color:${tone};
                  display:grid;place-content:center;flex-shrink:0;"><i class="fas fa-bell"></i></div>
      <div style="flex:1;min-width:0;">
        <div style="font-weight:600;margin-bottom:2px;">${escape(n.title || '')}</div>
        <div style="font-size:11.5px;color:#94a3b8;line-height:1.4;">${escape(n.message || '')}</div>
      </div>`;
    el.addEventListener('click', () => {
      if (n.deepLink) window.location.href = n.deepLink;
      remove();
    });
    const remove = () => {
      el.style.transform = 'translateX(120%)';
      setTimeout(() => el.remove(), 300);
    };
    host.appendChild(el);
    requestAnimationFrame(() => el.style.transform = 'translateX(0)');
    setTimeout(remove, 6000);
  }

  function escape(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c =>
      ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
  }

  /* ---------- BADGE ---------- */

  function paintBadge() {
    document.querySelectorAll('.notif-bell-badge').forEach(el => {
      if (state.unread > 0) {
        el.textContent = state.unread;
        el.style.display = 'inline-grid';
      } else {
        el.style.display = 'none';
      }
    });
    // legacy PM badge id
    const legacy = document.getElementById('pmNotifBadge');
    if (legacy) {
      legacy.textContent = state.unread;
      legacy.style.display = state.unread > 0 ? 'inline-grid' : 'none';
    }
  }

  /* ---------- HTTP ---------- */

  async function api(method, url, body) {
    const opts = {
      method: method || 'GET',
      credentials: 'same-origin',
      headers: { 'Accept':'application/json', 'X-Requested-With':'XMLHttpRequest' }
    };
    if (body) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    const res = await fetch(url, opts);
    const text = await res.text();
    let data = null;
    if (text) { try { data = JSON.parse(text); } catch (e) { data = { success:false, message:text }; } }
    if (!res.ok || (data && data.success === false)) throw new Error((data && data.message) || ('HTTP ' + res.status));
    return data ? data.data : null;
  }

  async function refresh() {
    try {
      const [list, unread] = await Promise.all([
        api('GET', '/api/pm/notifications?limit=30'),
        api('GET', '/api/pm/notifications/unread-count')
      ]);
      state.list = list || [];
      state.unread = unread ? (unread.count || 0) : 0;
      paintBadge();
      window.dispatchEvent(new CustomEvent('notifications:refreshed', { detail: state }));
    } catch (e) {
      console.warn('notifications refresh failed', e);
    }
  }

  async function markRead(id) {
    try { await api('POST', `/api/pm/notifications/${id}/read`); refresh(); }
    catch (e) { console.warn(e); }
  }

  async function markAllRead() {
    try { await api('POST', '/api/pm/notifications/mark-all-read'); refresh(); }
    catch (e) { console.warn(e); }
  }

  /* ---------- WEBSOCKET ---------- */

  function connectSocket() {
    if (state.connected) return;
    const employeeId = EMP_ID();
    if (!employeeId) return;

    // Prefer SockJS+Stomp (both loaded by pm-core.js pages), else native WebSocket via stomp.umd
    const hasSockJS = typeof window.SockJS !== 'undefined';
    const hasStomp  = typeof window.Stomp !== 'undefined' || typeof window.StompJs !== 'undefined';
    if (!hasStomp) return;

    let client;
    try {
      if (window.StompJs && window.StompJs.Client) {
        client = new window.StompJs.Client({
          webSocketFactory: () => hasSockJS ? new SockJS('/ws') : new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws-native'),
          reconnectDelay: 5000,
          debug: () => {}
        });
      } else if (window.Stomp && window.Stomp.over) {
        client = window.Stomp.over(hasSockJS ? new SockJS('/ws') : new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws-native'));
        client.debug = () => {};
        client.reconnect_delay = 5000;
      } else return;

      const onConnect = () => {
        state.connected = true;
        // personal bell
        const dest = `/topic/user/${employeeId}/notifications`;
        const sub = (c, d, h) => (c.subscribe ? c.subscribe(d, h) : c.subscribe(d, h));
        sub(client, dest, frame => {
          try {
            const payload = JSON.parse(frame.body);
            toast(payload);
            refresh();
            // also fire the legacy event so pm-core's board refresh kicks in
            window.dispatchEvent(new CustomEvent('pm:event', { detail: payload }));
          } catch (e) { console.warn(e); }
        });
        // global events (announcements etc.)
        sub(client, '/topic/global/events', frame => {
          try {
            const p = JSON.parse(frame.body);
            toast(p);
            refresh();
          } catch (e) {}
        });
      };

      if (client.activate) {
        client.onConnect = onConnect;
        client.activate();
      } else {
        client.connect({}, onConnect);
      }

      state.socket = client;
    } catch (e) {
      console.warn('notifications websocket failed', e);
    }
  }

  /* ---------- PUBLIC API ---------- */

  window.Notifications = {
    refresh,
    markRead,
    markAllRead,
    get list() { return state.list; },
    get unread() { return state.unread; },
    boot() {
      refresh();
      connectSocket();
      // refresh on window focus (catch notifications received while tab was inactive)
      window.addEventListener('focus', refresh);
      // periodic poll fallback
      setInterval(refresh, 60000);
    }
  };
})();