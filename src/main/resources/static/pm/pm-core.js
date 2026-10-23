/**
 * pm-core.js — Shared frontend helpers for the HRMS PM module.
 * Loaded on every /pm/*.html page. Replaces the copy-pasted PM object.
 *
 * Public API:
 *   PM.toast(msg, type, dur)          — show a toast
 *   PM.openDrawer(html, title)        — right-side drawer
 *   PM.closeDrawer()                  — close drawer
 *   PM.openCmd()                      — command palette (⌘K)
 *   PM.toggleSidebar()                — collapse sidebar
 *   PM.toggleTheme()                  — dark/light
 *   PM.esc(s)                         — HTML escape
 *   PM.getInitials(name)              — "Vinay Kumar" → "VK"
 *   PM.fmtDate(iso)                   — short date
 *   PM.fmtDateTime(iso)               — date + time
 *   PM.api(method, url, body?)        — fetch wrapper, throws on error
 *   PM.apiList(method, url, body?)    — same, but always returns an array
 *   PM.getMe()                        — cached /api/pm/me
 *   PM.meTasks()                      — cached /api/pm/me/tasks
 *   PM.meProjects()                   — cached /api/pm/me/projects
 *   PM.notifications                  — the shared notification client (after boot)
 *   PM.approvals()                    — the shared approval UI (after boot)
 *   PM.buildSidebar(user)             — render role-aware sidebar
 *   PM.buildHeader(user)              — render header avatar/name
 *   PM.bootstrap(opts?)               — call on DOMContentLoaded
 *   PM.logout()                       — POST /candidate/logout
 *   PM.user                           — current user (after bootstrap)
 */
(function () {
  'use strict';

  /* ============================================================
     THEME
     ============================================================ */
  function initTheme() {
    if (localStorage.getItem('pm-theme') === 'light') {
      document.body.classList.add('pm-light');
    }
  }

  function toggleTheme() {
    document.body.classList.toggle('pm-light');
    localStorage.setItem('pm-theme', document.body.classList.contains('pm-light') ? 'light' : 'dark');
    toast('Theme toggled', 'info');
  }

  /* ============================================================
     TOAST
     ============================================================ */
  function ensureToastContainer() {
    let el = document.querySelector('.pm-toasts');
    if (!el) {
      el = document.createElement('div');
      el.className = 'pm-toasts';
      document.body.appendChild(el);
    }
    return el;
  }

  function toast(msg, type, dur) {
    type = type || 'info';
    dur = dur || 3000;
    const cont = ensureToastContainer();
    const t = document.createElement('div');
    t.className = 'pm-toast ' + type;
    const icons = { success: 'fa-check', error: 'fa-times', warning: 'fa-exclamation', info: 'fa-info' };
    t.innerHTML = '<div class="pm-toast-icon"><i class="fas ' + (icons[type] || icons.info) + '"></i></div>' +
                  '<div class="pm-toast-msg">' + msg + '</div>';
    cont.appendChild(t);
    requestAnimationFrame(() => t.classList.add('show'));
    const rm = () => { t.classList.remove('show'); setTimeout(() => t.remove(), 300); };
    t.addEventListener('click', rm);
    if (dur > 0) setTimeout(rm, dur);
  }

  /* ============================================================
     DRAWER
     ============================================================ */
  function openDrawer(html, title) {
    let bd = document.getElementById('pmDrawerBd');
    if (!bd) {
      bd = document.createElement('div');
      bd.id = 'pmDrawerBd';
      bd.className = 'pm-drawer-backdrop';
      bd.addEventListener('click', closeDrawer);
      document.body.appendChild(bd);
    }
    let dr = document.getElementById('pmDrawer');
    if (!dr) {
      dr = document.createElement('div');
      dr.id = 'pmDrawer';
      dr.className = 'pm-drawer';
      dr.innerHTML = '<div class="pm-drawer-head">' +
                       '<div class="pm-drawer-title" id="pmDrawerTitle"></div>' +
                       '<button class="pm-iconbtn" onclick="PM.closeDrawer()"><i class="fas fa-times"></i></button>' +
                     '</div>' +
                     '<div class="pm-drawer-body" id="pmDrawerBody"></div>';
      document.body.appendChild(dr);
    }
    document.getElementById('pmDrawerTitle').textContent = title || 'Details';
    document.getElementById('pmDrawerBody').innerHTML = html || '';
    requestAnimationFrame(() => { bd.classList.add('open'); dr.classList.add('open'); });
  }

  function closeDrawer() {
    const bd = document.getElementById('pmDrawerBd');
    const dr = document.getElementById('pmDrawer');
    if (bd) bd.classList.remove('open');
    if (dr) dr.classList.remove('open');
  }

  /* ============================================================
     SIDEBAR / THEME / ESC
     ============================================================ */
  function toggleSidebar() {
    const shell = document.getElementById('pmShell');
    if (shell) shell.classList.toggle('collapsed');
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
    );
  }

  /* ============================================================
     FORMATTING HELPERS
     ============================================================ */
  function getInitials(name) {
    if (!name) return '?';
    const parts = String(name).trim().split(/\s+/);
    const a = parts[0] ? parts[0][0] : '';
    const b = parts[1] ? parts[1][0] : '';
    const out = (a + b).toUpperCase();
    return out || '?';
  }

  function fmtDate(iso) {
    if (!iso) return '—';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch (e) { return iso; }
  }

  function fmtDateTime(iso) {
    if (!iso) return '—';
    try {
      const d = new Date(iso);
      return d.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
    } catch (e) { return iso; }
  }

  /* ============================================================
     API WRAPPER
     Throws Error with .status and .message on any failure.
     Callers must handle errors — no silent failure path.
     ============================================================ */
  async function api(method, url, body) {
    const opts = {
      method: method || 'GET',
      credentials: 'same-origin',
      headers: {
        'Accept': 'application/json',
        'X-Requested-With': 'XMLHttpRequest'
      }
    };
    if (body !== undefined && body !== null) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    let data = null;
    const text = await res.text();
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = { success: false, message: text }; }
    }
    if (!res.ok) {
      const err = new Error((data && data.message) || ('HTTP ' + res.status));
      err.status = res.status;
      throw err;
    }
    if (data && data.success === false) {
      const err = new Error(data.message || 'Request failed');
      err.status = res.status;
      throw err;
    }
    return data ? data.data : null;
  }

  /* ============================================================
     API LIST — always returns an array, whether the backend sent
     a plain List or a Spring PageResponse ({ content: [...] }).
     Use this for any endpoint that may be paginated.
     ============================================================ */
  async function apiList(method, url, body) {
    const data = await api(method, url, body);
    if (Array.isArray(data)) return data;
    if (data && Array.isArray(data.content)) return data.content;
    return [];
  }

  /* ============================================================
     /api/pm/me — cached
     ============================================================ */
  let _meCache = null;
  async function getMe() {
    if (_meCache) return _meCache;
    _meCache = await api('GET', '/api/pm/me');
    return _meCache;
  }

  /* ============================================================
     LOGOUT
     ============================================================ */
  function logout() {
    PM.toast('Logging out...', 'info', 800);
    setTimeout(() => { window.location.href = '/candidate/logout'; }, 300);
  }

  /* ============================================================
     HEADER BUILDER
     ============================================================ */
  function buildHeader(user) {
    const initials = user ? (user.avatarInitials || getInitials(user.name)) : '?';
    const name = user ? (user.name || '') : '';

    const av = document.getElementById('pmUserAvatar');
    if (av) {
      av.textContent = initials;
      av.title = name;
      av.style.cursor = 'pointer';
      av.onclick = function (e) {
        e.stopPropagation();
        toggleUserMenu();
      };
    }

    const nm = document.getElementById('pmUserName');
    if (nm) nm.textContent = name;

    if (av && !document.getElementById('pmUserMenu')) {
      const menu = document.createElement('div');
      menu.id = 'pmUserMenu';
      menu.style.cssText = 'position:fixed;top:52px;right:12px;background:var(--card);border:1px solid var(--bd);' +
        'border-radius:8px;box-shadow:0 12px 40px rgba(0,0,0,.5);z-index:2000;padding:6px;min-width:200px;display:none';
      menu.innerHTML =
        '<div style="padding:8px 10px;border-bottom:1px solid var(--bd);margin-bottom:4px">' +
          '<div style="font-weight:600;font-size:13px">' + esc(name) + '</div>' +
          '<div style="font-size:11px;color:var(--tx3)">' + esc(user ? user.hrmsRole : '') + '</div>' +
        '</div>' +
        '<div class="pm-user-menu-item" data-action="settings" style="padding:7px 10px;border-radius:5px;cursor:pointer;font-size:12.5px">' +
          '<i class="fas fa-gear" style="width:14px;color:var(--pri)"></i> Settings</div>' +
        '<div class="pm-user-menu-item" data-action="logout" style="padding:7px 10px;border-radius:5px;cursor:pointer;font-size:12.5px;color:var(--dng)">' +
          '<i class="fas fa-right-from-bracket" style="width:14px"></i> Logout</div>';
      document.body.appendChild(menu);

      menu.querySelectorAll('.pm-user-menu-item').forEach(item => {
        item.addEventListener('mouseenter', () => { item.style.background = 'var(--card2)'; });
        item.addEventListener('mouseleave', () => { item.style.background = ''; });
        item.addEventListener('click', () => {
          const action = item.dataset.action;
          toggleUserMenu(false);
          if (action === 'settings') window.location.href = '/pm/11-settings';
          else if (action === 'logout') logout();
        });
      });

      document.addEventListener('click', () => toggleUserMenu(false));
    }
  }

  function toggleUserMenu(force) {
    const m = document.getElementById('pmUserMenu');
    if (!m) return;
    const shouldOpen = force === undefined ? m.style.display !== 'block' : force;
    m.style.display = shouldOpen ? 'block' : 'none';
  }

  /* ============================================================
     SIDEBAR BUILDER
     ============================================================ */
  const SIDEBAR_NAV = [
    { section: 'DASHBOARD', icon: 'fa-home' },
    { icon: 'fa-gauge-high', label: 'Dashboard', href: '/pm/01-home' },
    { icon: 'fa-chart-simple', label: 'Portfolio', href: '/pm/01-home#portfolio' },
    { icon: 'fa-route', label: 'Roadmap', href: '/pm/01-home#roadmap' },
    { icon: 'fa-inbox', label: 'My Work', href: '/pm/03-my-work', badge: 'myWork' },

    { section: 'PROJECTS', icon: 'fa-folder-open' },
    { icon: 'fa-list', label: 'All Projects', href: '/pm/04-projects' },
        { icon: 'fa-plus', label: 'Create Project', href: '/pm/04-projects?action=create', requires: 'creator' },
        { icon: 'fa-layer-group', label: 'Templates', href: '/pm/04-projects?action=templates', requires: 'creator' },
        { icon: 'fa-robot', label: 'Auto-Rules', href: '/pm/04-projects?action=autorules', requires: 'creator' },
    { section: 'PLANNING', icon: 'fa-diagram-project' },
    { icon: 'fa-layer-group', label: 'Backlog', href: '/pm/05-planning?tab=backlog' },
    { icon: 'fa-bolt', label: 'Sprints', href: '/pm/05-planning?tab=sprints' },
    { icon: 'fa-flag-checkered', label: 'Milestones', href: '/pm/05-planning?tab=milestones' },
    { icon: 'fa-bullseye', label: 'Goals & OKRs', href: '/pm/05-planning?tab=goals' },
    { icon: 'fa-chart-gantt', label: 'Gantt', href: '/pm/05-planning?tab=gantt' },

    { section: 'TASKS', icon: 'fa-list-check' },
    { icon: 'fa-table-columns', label: 'Task Board', href: '/pm/06-tasks?view=board' },
    { icon: 'fa-list-ul', label: 'Task List', href: '/pm/06-tasks?view=list' },
    { icon: 'fa-calendar-days', label: 'Calendar', href: '/pm/06-tasks?view=calendar' },
    { icon: 'fa-diagram-project', label: 'Dependency Graph', href: '/pm/06b-graph' },

    { section: 'CLIENTS', icon: 'fa-handshake' },
    { icon: 'fa-address-card', label: 'Client Directory', href: '/pm/08-clients' },

    { section: 'TEAM', icon: 'fa-people-group' },
    { icon: 'fa-users', label: 'Teams', href: '/pm/07-team?tab=teams' },
    { icon: 'fa-address-book', label: 'Members', href: '/pm/07-team?tab=members' },
    { icon: 'fa-users-gear', label: 'Resources', href: '/pm/07-team?tab=resources' },
    { icon: 'fa-clock', label: 'Timesheets', href: '/pm/07-team?tab=timesheets' },
    { icon: 'fa-star-half-stroke', label: 'Performance', href: '/pm/07-team?tab=reviews' },

    { section: 'QUALITY', icon: 'fa-shield-halved' },
    { icon: 'fa-bug', label: 'Issues', href: '/pm/09-quality?tab=issues' },
    { icon: 'fa-triangle-exclamation', label: 'Risks', href: '/pm/09-quality?tab=risks' },

    { section: 'INSIGHTS', icon: 'fa-chart-pie' },
    { icon: 'fa-chart-line', label: 'Reports', href: '/pm/10-insights?tab=reports' },
    { icon: 'fa-indian-rupee-sign', label: 'Budget', href: '/pm/10-insights?tab=budget' },

    { section: 'WORKSPACE', icon: 'fa-briefcase' },
    { icon: 'fa-bell', label: 'Notifications', href: '/pm/02-inbox?tab=notifications', badge: 'notifications' },
    { icon: 'fa-square-check', label: 'Approvals', href: '/pm/02-inbox?tab=approvals',badge: 'approvals' },
    { icon: 'fa-clock-rotate-left', label: 'Activity Logs', href: '/pm/02-inbox?tab=activity' },
    { icon: 'fa-gear', label: 'Settings', href: '/pm/11-settings', requires: 'admin' }
  ];

  function buildSidebar(user) {
    const nav = document.getElementById('pmSidebarNav');
    if (!nav) return;

        const isAdmin = user && user.admin === true;
        const canCreate = user && (user.admin === true || user.canCreateProject === true);
    const currentPath = window.location.pathname + window.location.search;

    let html = '';
    SIDEBAR_NAV.forEach(item => {
      if (item.section) {
        html += '<div class="pm-nav-section"><i class="fas ' + item.icon + '"></i> ' + esc(item.section) + '</div>';
        return;
      }
            if (item.requires === 'admin' && !isAdmin) return;
            if (item.requires === 'creator' && !canCreate) return;

      const isActive = currentPath === item.href ||
                       currentPath.startsWith(item.href.split('?')[0]) ||
                       (item.href.includes('#') && currentPath.startsWith(item.href.split('#')[0]));
      const badgeHtml = item.badge
        ? '<span class="pm-nav-badge" id="pmNavBadge_' + item.badge + '" style="display:none">0</span>'
        : '';
      html += '<a class="pm-nav-link' + (isActive ? ' active' : '') + '" href="' + esc(item.href) + '">' +
                '<i class="fas ' + item.icon + '"></i>' +
                '<span class="pm-nav-label">' + esc(item.label) + '</span>' +
                badgeHtml +
              '</a>';
    });
    nav.innerHTML = html;
  }

  /* ============================================================
     COMMAND PALETTE
     ============================================================ */
  const CMDS = [
    { icon: 'fa-gauge-high', label: 'Dashboard', action: () => location.href = '/pm/01-home' },
    { icon: 'fa-inbox', label: 'My Work', action: () => location.href = '/pm/03-my-work' },
    { icon: 'fa-folder-open', label: 'All Projects', action: () => location.href = '/pm/04-projects' },
    { icon: 'fa-table-columns', label: 'Task Board', action: () => location.href = '/pm/06-tasks?view=board' },
    { icon: 'fa-list-ul', label: 'Task List', action: () => location.href = '/pm/06-tasks?view=list' },
    { icon: 'fa-calendar-days', label: 'Calendar', action: () => location.href = '/pm/06-tasks?view=calendar' },
    { icon: 'fa-diagram-project', label: 'Dependency Graph', action: () => location.href = '/pm/06b-graph' },
    { icon: 'fa-users', label: 'Teams', action: () => location.href = '/pm/07-team?tab=teams' },
    { icon: 'fa-address-card', label: 'Clients', action: () => location.href = '/pm/08-clients' },
    { icon: 'fa-bug', label: 'Issues', action: () => location.href = '/pm/09-quality?tab=issues' },
    { icon: 'fa-chart-line', label: 'Reports', action: () => location.href = '/pm/10-insights?tab=reports' },
    { icon: 'fa-gear', label: 'Settings', action: () => location.href = '/pm/11-settings' },
    { icon: 'fa-moon', label: 'Toggle theme', action: toggleTheme },
    { icon: 'fa-right-from-bracket', label: 'Logout', action: logout }
  ];

  let cmdIndex = 0, cmdFiltered = CMDS;

  function openCmd() {
    let bd = document.getElementById('pmCmdBd');
    if (!bd) {
      bd = document.createElement('div');
      bd.id = 'pmCmdBd';
      bd.className = 'pm-cmd-backdrop';
      bd.innerHTML =
        '<div class="pm-cmd" onclick="event.stopPropagation()">' +
          '<div class="pm-cmd-input">' +
            '<i class="fas fa-search"></i>' +
            '<input id="pmCmdInp" placeholder="Type a command..." autocomplete="off">' +
            '<kbd>ESC</kbd>' +
          '</div>' +
          '<div class="pm-cmd-list" id="pmCmdList"></div>' +
          '<div class="pm-cmd-foot">' +
            '<span><kbd>↑</kbd><kbd>↓</kbd> Navigate</span>' +
            '<span><kbd>↵</kbd> Select</span>' +
          '</div>' +
        '</div>';
      document.body.appendChild(bd);
      bd.addEventListener('click', closeCmd);
      bd.querySelector('#pmCmdInp').addEventListener('input', renderCmd);
      bd.querySelector('#pmCmdInp').addEventListener('keydown', handleCmdKeys);
    }
    bd.classList.add('open');
    renderCmd();
    setTimeout(() => bd.querySelector('#pmCmdInp').focus(), 40);
  }

  function closeCmd() {
    const bd = document.getElementById('pmCmdBd');
    if (bd) bd.classList.remove('open');
  }

  function renderCmd() {
    const q = (document.getElementById('pmCmdInp').value || '').toLowerCase();
    cmdFiltered = CMDS.filter(c => c.label.toLowerCase().includes(q));
    cmdIndex = 0;
    const list = document.getElementById('pmCmdList');
    list.innerHTML = cmdFiltered.map((c, i) =>
      '<div class="pm-cmd-item' + (i === 0 ? ' active' : '') + '" data-i="' + i + '">' +
        '<i class="fas ' + c.icon + '"></i><span>' + esc(c.label) + '</span>' +
      '</div>'
    ).join('') || '<div style="padding:14px;text-align:center;color:var(--tx3);font-size:12px">No matches</div>';
    list.querySelectorAll('.pm-cmd-item').forEach(el =>
      el.addEventListener('click', () => {
        const i = parseInt(el.dataset.i);
        cmdFiltered[i].action();
        closeCmd();
      })
    );
  }

  function handleCmdKeys(e) {
    const items = document.querySelectorAll('#pmCmdList .pm-cmd-item');
    if (!items.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      cmdIndex = (cmdIndex + 1) % items.length;
      items.forEach((it, i) => it.classList.toggle('active', i === cmdIndex));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      cmdIndex = (cmdIndex - 1 + items.length) % items.length;
      items.forEach((it, i) => it.classList.toggle('active', i === cmdIndex));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      items[cmdIndex].click();
    }
  }

  /* ===== SESSION EXPIRY + REALTIME ===== */
  (function () {
    var nativeFetch = window.fetch.bind(window);
    window.fetch = function () {
      return nativeFetch.apply(null, arguments).then(function (res) {
        if (res.status === 401) location.href = '/candidate/login?error=Session+expired';
        return res;
      });
    };

    function setLive(on) {
      var el = document.querySelector('.pm-live');
      if (!el) return;
      el.innerHTML = '<span class="dot"' + (on ? '' : ' style="background:#ef4444"') + '></span> ' +
                     (on ? 'Live' : 'Reconnecting…');
    }

    var timer;
    window.addEventListener('pm:event', function () {
      clearTimeout(timer);
      timer = setTimeout(function () {
        if (typeof window.pmRefresh === 'function') window.pmRefresh();
      }, 400);
    });

    if (window.StompJs && window.SockJS) {
      var client = new StompJs.Client({
        webSocketFactory: function () { return new SockJS('/ws'); },
        reconnectDelay: 5000,
        onConnect: function () {
          setLive(true);
          client.subscribe('/topic/pm/events', function (m) {
            window.dispatchEvent(new CustomEvent('pm:event', { detail: JSON.parse(m.body) }));
          });
        },
        onWebSocketClose: function () { setLive(false); }
      });
      client.activate();
    } else {
      console.warn('pm-core: SockJS/StompJs not loaded, live updates disabled');
    }
  })();

  /* ============================================================
     KEYBOARD SHORTCUTS
     ============================================================ */
  document.addEventListener('keydown', e => {
    const tag = (e.target && e.target.tagName) || '';
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(tag)) return;
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.key.toLowerCase() === 'k') { e.preventDefault(); openCmd(); return; }
    if (e.key === 'Escape') {
      closeCmd();
      closeDrawer();
      toggleUserMenu(false);
    }
    if (e.key === '[') { e.preventDefault(); toggleSidebar(); }
  });

  /* ============================================================
     PHASE 3 — My Work helpers
     ============================================================ */
  let _meTasksCache = null;
  let _meProjectsCache = null;

  async function meTasks() {
    if (_meTasksCache) return _meTasksCache;
    _meTasksCache = await api('GET', '/api/pm/me/tasks');
    return _meTasksCache;
  }

  async function meProjects() {
    if (_meProjectsCache) return _meProjectsCache;
    _meProjectsCache = await api('GET', '/api/pm/me/projects');
    return _meProjectsCache;
  }

  function invalidateMeCaches() {
    _meTasksCache = null;
    _meProjectsCache = null;
  }

  // Auto-invalidate on every realtime event
    window.addEventListener('pm:event', invalidateMeCaches);

    /* ============================================================
       PHASE 2 — member drawers (pm-members.js is loaded on demand)
       ============================================================ */
    function loadMembersScript() {
      if (window.PMMembers) return Promise.resolve();
      return new Promise(function (resolve) {
        var s = document.createElement('script');
        s.id = 'pm-members-js';
        s.src = '/pm/pm-members.js';
        s.onload = resolve;
        s.onerror = resolve;
        document.head.appendChild(s);
      });
    }
      // PHASE 5 — "Health & budget" drawer (pm-finance.js loaded on demand)
      async function openProjectFinance(projectId) {
        if (!window.PMFinance) {
          await new Promise(function (resolve) {
            var s = document.createElement('script');
            s.src = '/pm/pm-finance.js';
            s.onload = resolve;
            s.onerror = resolve;
            document.head.appendChild(s);
          });
        }
        if (window.PMFinance) window.PMFinance.open(projectId);
        else toast('Could not load the finance panel', 'error');
      }
        async function openProjectClosure(projectId) {
          if (!window.PMClosure) {
            await new Promise(function (resolve) {
              var s = document.createElement('script');
              s.src = '/pm/pm-closure.js';
              s.onload = resolve;
              s.onerror = resolve;
              document.head.appendChild(s);
            });
          }
          if (window.PMClosure) window.PMClosure.open(projectId);
          else toast('Could not load the closure panel', 'error');
        }

      async function openProjectMembers(projectId, name) {
      await loadMembersScript();
      if (window.PMMembers) window.PMMembers.openProject(projectId, name);
      else toast('Could not load the members panel', 'error');
    }
    async function openTeamMembers(teamId, name) {
      await loadMembersScript();
      if (window.PMMembers) window.PMMembers.openTeam(teamId, name);
      else toast('Could not load the members panel', 'error');
    }
function fmtMinutes(m){ m = m || 0; const h = Math.floor(m/60), r = m % 60;
  return h ? (r ? h + 'h ' + r + 'm' : h + 'h') : r + 'm'; }
  /* ============================================================
     PHASE 4 — boot the shared notification client
     FIX 9 — don't double-load if the page already has the script tag
     ============================================================ */
  async function bootNotifications() {
    // If the page already has a <script src="/notifications.js"> tag, do NOT re-add it.
    const alreadyLoaded = document.querySelector('script[src="/notifications.js"]');
    if (!window.Notifications && !alreadyLoaded) {
      await new Promise((resolve, reject) => {
        const s = document.createElement('script');
        s.id = 'notif-shared-js';
        s.src = '/notifications.js';
        s.onload = resolve;
        s.onerror = reject;
        document.head.appendChild(s);
      });
    }
    // Wait until Notifications is actually defined (page tag may still be loading)
    if (!window.Notifications) {
      await new Promise(resolve => {
        const t0 = Date.now();
        const tick = () => {
          if (window.Notifications || Date.now() - t0 > 3000) resolve();
          else setTimeout(tick, 50);
        };
        tick();
      });
    }
    if (window.Notifications) window.Notifications.boot();
  }

  /* ============================================================
     PHASE 7 — boot the shared approval client
     FIX 9 — don't double-load if the page already has the script tag
     ============================================================ */
  async function loadApprovalsScript() {
    if (window.PMApprovals) return;
    // If the page already has a <script src="/pm/pm-approvals.js"> tag, wait for it instead of re-adding.
    const alreadyLoaded = document.querySelector('script[src="/pm/pm-approvals.js"]');
    if (alreadyLoaded) {
      await new Promise(resolve => {
        const t0 = Date.now();
        const tick = () => {
          if (window.PMApprovals || Date.now() - t0 > 3000) resolve();
          else setTimeout(tick, 50);
        };
        tick();
      });
      return;
    }
    await new Promise((resolve) => {
      const s = document.createElement('script');
      s.id = 'pm-approvals-js';
      s.src = '/pm/pm-approvals.js';
      s.onload = resolve;
      s.onerror = resolve;
      document.head.appendChild(s);
    });
  }

  /* ============================================================
     BOOTSTRAP
     ============================================================ */
  async function bootstrap(opts) {
    initTheme();
    try {
      const me = await getMe();
      PM.user = me;
      buildHeader(me);
      buildSidebar(me);

      // Phase 4: start the notification client
      bootNotifications().catch(e => console.warn('notif boot failed', e));

      // Phase 7: pre-load the approval UI so drawers work everywhere
      loadApprovalsScript().catch(e => console.warn('approvals boot failed', e));

      return me;
    } catch (err) {
      console.error('PM.bootstrap failed:', err);
      if (err.status === 401) {
        toast('Session expired. Redirecting to login...', 'error', 2000);
        setTimeout(() => { window.location.href = '/candidate/login'; }, 2200);
      } else {
        toast('Failed to load user: ' + err.message, 'error');
      }
      throw err;
    }
  }
  async function openIntake(tab) {
    if (!window.PMIntake) {
      await new Promise(function (resolve) {
        var s = document.createElement('script');
        s.src = '/pm/pm-intake.js';
        s.onload = resolve;
        s.onerror = resolve;
        document.head.appendChild(s);
      });
    }
    if (window.PMIntake) window.PMIntake.open(tab);
    else toast('Could not load project intake', 'error');
  }
  function openIntakeFromHash() {
    var h = (location.hash || '').replace('#', '');
    if (h === 'templates' || h === 'requests') openIntake(h);
  }
  window.addEventListener('hashchange', openIntakeFromHash);
  /* ============================================================
     EXPORT
     ============================================================ */
  window.PM = {
    // ui
    toast, openDrawer, closeDrawer, openCmd, closeCmd,
    toggleSidebar, toggleTheme, esc,
    // formatting
    getInitials, fmtDate, fmtDateTime,
    // data
    api, apiList, getMe, bootstrap, logout,
    // Phase 3
        meTasks, meProjects, invalidateMeCaches,
        // Phase 2
        openProjectMembers, openTeamMembers, openProjectFinance, openProjectClosure,
    // Phase 4 + 7 accessors (evaluated lazily — scripts may not be loaded yet)
    get notifications() { return window.Notifications; },
    approvals: () => window.PMApprovals,
    // user (populated after bootstrap)
    user: null,
    // exposes for pages that want the raw nav list
        _nav: SIDEBAR_NAV,
        // simple fallback so PM.confirm(...) calls on the projects page work
        confirm: function (o) {
          o = o || {};
          return Promise.resolve(window.confirm((o.title ? o.title + '\n\n' : '') + (o.message || 'Are you sure?')));
        }
  };
})();