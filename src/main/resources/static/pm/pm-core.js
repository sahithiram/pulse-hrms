/**
 * pm-core.js — Shared frontend helpers for the HRMS PM module.
 * Loaded on every /pm/*.html page. Replaces the copy-pasted PM object.
 *
 * Public API:
 *   PM.toast(msg, type, dur)     — show a toast
 *   PM.openDrawer(html, title)   — right-side drawer
 *   PM.closeDrawer()             — close drawer
 *   PM.openCmd()                 — command palette (⌘K)
 *   PM.toggleSidebar()           — collapse sidebar
 *   PM.toggleTheme()             — dark/light
 *   PM.esc(s)                    — HTML escape
 *   PM.getInitials(name)         — "Vinay Kumar" → "VK"
 *   PM.fmtDate(iso)              — short date
 *   PM.fmtDateTime(iso)          — date + time
 *   PM.api(method, url, body?)   — fetch wrapper, throws on error
 *   PM.apiList(method, url, body?) — same, but always returns an array
 *   PM.getMe()                   — cached /api/pm/me
 *   PM.meTasks()                 — cached /api/pm/me/tasks
 *   PM.meProjects()              — cached /api/pm/me/projects
 *   PM.notifications             — the shared notification client (after boot)
 *   PM.approvals()               — the shared approval UI (after boot)
 *   PM.buildSidebar(user)        — render role-aware sidebar
 *   PM.buildHeader(user)         — render header avatar/name
 *   PM.bootstrap(opts?)          — call on DOMContentLoaded
 *   PM.logout()                  — POST /candidate/logout
 *   PM.user                      — current user (after bootstrap)
 *   PM.can(rule)                 — STEP 7.1: does the current user pass this rule?
 *   PM.guard(rule)               — STEP 7.1: false + "No access" screen when not allowed
 *   PM.gateTabs(map)             — STEP 7.1: hide tab buttons the user can't use
 *   PM.openIntake(tab)           — STEP 7.1: open the project requests / templates drawer
 *   PM.isTeamLeadOnly(u?)        — check if user is restricted to Team Lead scope
 *
 * Rules for PM.can (all read from /api/pm/me):
 *   admin      Super Admin, HR, HR Manager         (me.admin)
 *   super      Super Admin                         (me.superAdmin)
 *   creator    Super Admin, PM                     (me.canCreateProject)
 *   reviewer   Super Admin, HR Manager             (me.intakeReviewer)
 *   templates  reviewer or creator                 (may open the Templates list)
 *   clients    Super Admin, HR, HR Manager, PM     (me.canManageClients)
 *   finance    Super Admin, project owner/MANAGER  (me.financeAccess)
 *   lead       runs work day to day                (me.leadTier)
 *   manage     admin or lead                       (planning, team, reports pages)
 *   people     HR / HR Manager                     (me.peopleAdmin)
 *   teamlead   has leadOfTeamIds                   (me.leadOfTeamIds)
 */
(function () {
  'use strict';
  try { if (sessionStorage.getItem('pm.tlOnly') === '1') document.documentElement.classList.add('pm-tl'); } catch (e) {}
  (function () {
    var st = document.createElement('style');
    st.textContent =
      'html.pm-tl .sidebar a[href^="/pm/01-home"],html.pm-tl .sidebar a[href^="/pm/08-clients"],' +
      'html.pm-tl .sidebar a[href^="/pm/09-budget"],html.pm-tl .sidebar a[href^="/pm/10-insights"],' +
      'html.pm-tl .sidebar a[href^="/pm/12-reports"],html.pm-tl .sidebar a[href^="/pm/11-settings"],' +
      'html.pm-tl .dropdown-menu a[href^="/pm/11-settings"],' +
      'html.pm-tl [onclick*="bulkDelete"],html.pm-tl [onclick*="ctxAction(\'delete\')"],html.pm-tl [onclick*="deleteTask("],' +
      'html.pm-tl .create-tab[data-mode="import"]{display:none!important}';
    document.head.appendChild(st);
  })();

  /* ============================================================
     ROLE RULES  (STEP 7.1)
     ============================================================ */
  function can(rule, u) {
    u = u || (window.PM && window.PM.user);
    if (!u) return false;
    switch (rule) {
      case undefined:
      case null:
      case '':
      case 'any':       return true;
      case 'admin':     return u.admin === true;
      case 'super':     return u.superAdmin === true;
      case 'creator':   return u.canCreateProject === true;
      case 'reviewer':  return u.intakeReviewer === true;
      case 'templates': return u.intakeReviewer === true || u.canCreateProject === true;
      case 'clients':   return u.canManageClients === true;
      case 'finance':   return u.financeAccess === true;
      case 'lead':      return u.leadTier === true;
      case 'manage':    return u.admin === true || u.leadTier === true;
      case 'people':    return u.peopleAdmin === true;
      case 'teamlead':  return Array.isArray(u.leadOfTeamIds) && u.leadOfTeamIds.length > 0;
      default:          return false; // unknown rule: deny
    }
  }

  function isTeamLeadOnly(u) {
    u = u || (window.PM && window.PM.user);
    if (!u) return false;
    if (typeof u.teamLeadOnly === 'boolean') return u.teamLeadOnly;
    return Array.isArray(u.leadOfTeamIds) && u.leadOfTeamIds.length > 0
        && !u.admin && !u.superAdmin && !u.pm && !u.financeAccess;
  }

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
    setTimeout(() => { window.location.href = '/candidate/auth/logout'; }, 300);
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
     SIDEBAR BUILDER  (STEP 7.1: every item has a rule)
       requires : a PM.can rule; the item is hidden when it fails
       label    : a string, or a function(user) → string
       intake   : 'requests' | 'templates' → opens the intake drawer
                  in place instead of navigating
     ============================================================ */
  const SIDEBAR_NAV = [
    { section: 'DASHBOARD', icon: 'fa-home' },
    {
      icon: 'fa-user-tie',
      label: function (u) { return isTeamLeadOnly(u) ? 'My Team' : 'Team Lead'; },
      href: '/pm/12-team-lead',
      requires: 'teamlead'
    },
    { icon: 'fa-arrow-left', label: 'Back to HRMS', href: '/candidate/dashboard' },
    { icon: 'fa-user-clock', label: 'My HR (leave, attendance)', href: '/candidate/dashboard' },
    { icon: 'fa-gauge-high', label: 'Dashboard', href: '/pm/01-home' },
    { icon: 'fa-chart-simple', label: 'Portfolio', href: '/pm/01-home#portfolio' },
    { icon: 'fa-route', label: 'Roadmap', href: '/pm/01-home#roadmap' },
    { icon: 'fa-inbox', label: 'My Work', href: '/pm/03-my-work', badge: 'myWork' },

    { section: 'PROJECTS', icon: 'fa-folder-open' },
    { icon: 'fa-list', label: 'All Projects', href: '/pm/04-projects' },
    {
      icon: 'fa-paper-plane',
      label: function (u) { return (u.admin === true || u.pm === true || u.intakeReviewer === true) ? 'Project Requests' : 'Request a project'; },
      href: '/pm/04-projects#requests', intake: 'requests'
    },
    { icon: 'fa-plus', label: 'Create Project', href: '/pm/04-projects?action=create', requires: 'creator' },
    { icon: 'fa-layer-group', label: 'Templates', href: '/pm/04-projects#templates', intake: 'templates', requires: 'templates' },
    { icon: 'fa-robot', label: 'Auto-Rules', href: '/pm/04-projects?action=autorules', requires: 'creator' },

    { section: 'PLANNING', icon: 'fa-diagram-project' },
    { icon: 'fa-layer-group', label: 'Backlog', href: '/pm/05-planning?tab=backlog', requires: 'manage' },
    { icon: 'fa-bolt', label: 'Sprints', href: '/pm/05-planning?tab=sprints', requires: 'manage' },
    { icon: 'fa-flag-checkered', label: 'Milestones', href: '/pm/05-planning?tab=milestones' },
    { icon: 'fa-bullseye', label: 'Goals & OKRs', href: '/pm/05-planning?tab=goals', requires: 'manage' },
    { icon: 'fa-chart-gantt', label: 'Gantt', href: '/pm/05-planning?tab=gantt' },

    { section: 'TASKS', icon: 'fa-list-check' },
    { icon: 'fa-table-columns', label: 'Task Board', href: '/pm/06-tasks?view=board' },
    { icon: 'fa-list-ul', label: 'Task List', href: '/pm/06-tasks?view=list' },
    { icon: 'fa-calendar-days', label: 'Calendar', href: '/pm/06-tasks?view=calendar' },
    { icon: 'fa-diagram-project', label: 'Dependency Graph', href: '/pm/06b-graph' },

    { section: 'CLIENTS', icon: 'fa-handshake' },
    { icon: 'fa-address-card', label: 'Client Directory', href: '/pm/08-clients', requires: 'clients' },

    { section: 'TEAM', icon: 'fa-people-group' },
    { icon: 'fa-users', label: 'Teams', href: '/pm/07-team?tab=teams' },
    { icon: 'fa-address-book', label: 'Members', href: '/pm/07-team?tab=members', requires: 'manage' },
    { icon: 'fa-users-gear', label: 'Resources', href: '/pm/07-team?tab=resources', requires: 'manage' },
    { icon: 'fa-clock', label: 'Timesheets', href: '/pm/07-team?tab=timesheets' },
    { icon: 'fa-star-half-stroke', label: 'Performance', href: '/pm/07-team?tab=reviews', requires: 'manage' },

    { section: 'QUALITY', icon: 'fa-shield-halved' },
    { icon: 'fa-bug', label: 'Issues', href: '/pm/09-quality?tab=issues' },
    { icon: 'fa-triangle-exclamation', label: 'Risks', href: '/pm/09-quality?tab=risks' },

    { section: 'INSIGHTS', icon: 'fa-chart-pie' },
    { icon: 'fa-chart-line', label: 'Reports', href: '/pm/10-insights?tab=reports', requires: 'manage' },
    { icon: 'fa-indian-rupee-sign', label: 'Budget', href: '/pm/10-insights?tab=budget', requires: 'finance' },

    { section: 'WORKSPACE', icon: 'fa-briefcase' },
    { icon: 'fa-bell', label: 'Notifications', href: '/pm/02-inbox?tab=notifications', badge: 'notifications' },
    { icon: 'fa-square-check', label: 'Approvals', href: '/pm/02-inbox?tab=approvals', badge: 'approvals' },
    { icon: 'fa-clock-rotate-left', label: 'Activity Logs', href: '/pm/02-inbox?tab=activity', requires: 'manage' },
    { icon: 'fa-gear', label: 'Settings', href: '/pm/11-settings', requires: 'admin' }
  ];

  function normPath(p) {
    return String(p || '').replace(/\.html$/, '').replace(/\/+$/, '');
  }

  /** Exactly one entry should light up for the page/tab/hash the person is on. */
  function isActiveNav(item) {
    const hashParts = item.href.split('#');
    const hash = hashParts[1] ? '#' + hashParts[1] : '';
    const qParts = hashParts[0].split('?');
    const path = normPath(qParts[0]);
    const query = qParts[1] || '';
    if (normPath(location.pathname) !== path) return false;
    if (hash) return location.hash === hash;
    if (query) return location.search.indexOf(query) !== -1;
    // plain page link: not active while a hash entry or an ?action= of the same page is in use
    if (/action=/.test(location.search)) return false;
    return !SIDEBAR_NAV.some(n => n.href && n.href.indexOf('#') > -1 &&
      normPath(n.href.split('#')[0]) === path && location.hash === '#' + n.href.split('#')[1]);
  }

  function buildSidebar(user) {
    const nav = document.getElementById('pmSidebarNav');
    if (!nav) return;
    user = user || PM.user;

    const tl = isTeamLeadOnly(user);
    const TL_HIDE = ['/pm/01-home', '/pm/08-clients', '/pm/09-budget', '/pm/10-insights', '/pm/12-reports', '/pm/11-settings', '/pm/04-projects?action=', '/pm/04-projects#'];
    const tlHidden = h => TL_HIDE.some(p => String(h || '').indexOf(p) === 0);
    const visible = SIDEBAR_NAV.filter(item => item.section ||
      ((!item.requires || can(item.requires, user)) && !(tl && tlHidden(item.href))));

    // 2) drop section headings that ended up with nothing under them
    const shown = visible.filter((item, i) => {
      if (!item.section) return true;
      const next = visible[i + 1];
      return next && !next.section;
    });

    let html = '';
    shown.forEach(item => {
      if (item.section) {
        html += '<div class="pm-nav-section"><i class="fas ' + item.icon + '"></i> ' + esc(item.section) + '</div>';
        return;
      }
      const label = typeof item.label === 'function' ? item.label(user || {}) : item.label;
      const badgeHtml = item.badge
        ? '<span class="pm-nav-badge" id="pmNavBadge_' + item.badge + '" style="display:none">0</span>'
        : '';
      html += '<a class="pm-nav-link' + (isActiveNav(item) ? ' active' : '') + '" href="' + esc(item.href) + '"' +
        (item.intake ? ' data-intake="' + item.intake + '"' : '') + '>' +
        '<i class="fas ' + item.icon + '"></i>' +
        '<span class="pm-nav-label">' + esc(label) + '</span>' +
        badgeHtml +
        '</a>';
    });
    nav.innerHTML = html;

    // intake entries open the drawer right here, on any page, without navigating
    nav.querySelectorAll('[data-intake]').forEach(a => {
      a.addEventListener('click', e => {
        e.preventDefault();
        openIntake(a.dataset.intake);
      });
    });
  }

  /* ============================================================
     COMMAND PALETTE
     ============================================================ */
  const CMDS = [
    { icon: 'fa-gauge-high', label: 'Dashboard', tlHide: true, action: () => location.href = '/pm/01-home' },
    { icon: 'fa-inbox', label: 'My Work', action: () => location.href = '/pm/03-my-work' },
    { icon: 'fa-folder-open', label: 'All Projects', action: () => location.href = '/pm/04-projects' },
    { icon: 'fa-paper-plane', label: 'Project requests', tlHide: true, action: () => openIntake('requests') },
    { icon: 'fa-layer-group', label: 'Project templates', requires: 'templates', tlHide: true, action: () => openIntake('templates') },
    { icon: 'fa-table-columns', label: 'Task Board', action: () => location.href = '/pm/06-tasks?view=board' },
    { icon: 'fa-list-ul', label: 'Task List', action: () => location.href = '/pm/06-tasks?view=list' },
    { icon: 'fa-calendar-days', label: 'Calendar', action: () => location.href = '/pm/06-tasks?view=calendar' },
    { icon: 'fa-diagram-project', label: 'Dependency Graph', action: () => location.href = '/pm/06b-graph' },
    { icon: 'fa-users', label: 'Teams', action: () => location.href = '/pm/07-team?tab=teams' },
    { icon: 'fa-address-card', label: 'Clients', requires: 'clients', tlHide: true, action: () => location.href = '/pm/08-clients' },
    { icon: 'fa-bug', label: 'Issues', action: () => location.href = '/pm/09-quality?tab=issues' },
    { icon: 'fa-chart-line', label: 'Reports', requires: 'manage', tlHide: true, action: () => location.href = '/pm/10-insights?tab=reports' },
    { icon: 'fa-gear', label: 'Settings', requires: 'admin', tlHide: true, action: () => location.href = '/pm/11-settings' },
    { icon: 'fa-moon', label: 'Toggle theme', action: toggleTheme },
    { icon: 'fa-arrow-left', label: 'Back to HRMS', action: () => location.href = '/candidate/dashboard' },
    { icon: 'fa-user-tie', label: 'My Team (Team Lead dashboard)', requires: 'teamlead', action: () => location.href = '/pm/12-team-lead' },
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
    cmdFiltered = CMDS.filter(c => (!c.requires || can(c.requires)) && !(c.tlHide && isTeamLeadOnly()) && c.label.toLowerCase().includes(q));
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

  /* ============================================================
     REALTIME (WEBSOCKET / STOMP)
     ============================================================ */
  (function () {
    var nativeFetch = window.fetch.bind(window);
    window.fetch = function () {
      return nativeFetch.apply(null, arguments).then(function (res) {
        if (res.status === 401) location.href = '/candidate/login?error=Session+expired';
        return res;
      });
    };

    function liveEl() {
      var el = document.querySelector('.pm-live'); if (el) return el;
      var host = document.querySelector('.top-header-actions'); if (!host) return null;
      el = document.createElement('span'); el.className = 'pm-live';
      el.style.cssText = 'display:inline-flex;align-items:center;gap:5px;font-size:11px;font-weight:600;color:var(--dark-text-secondary);padding:0 6px';
      host.insertBefore(el, host.firstChild); return el;
    }
    function setLive(on) {
      var el = liveEl(); if (!el) return;
      el.innerHTML = '<span class="dot" style="display:inline-block;width:6px;height:6px;border-radius:50%;background:' +
        (on ? '#10b981' : '#ef4444') + '"></span> ' + (on ? 'Live' : 'Reconnecting…');
    }
    function loadScript(src) {
      return new Promise(function (res) {
        var s = document.createElement('script'); s.src = src; s.onload = res; s.onerror = res; document.head.appendChild(s);
      });
    }
    var timer;
    window.addEventListener('pm:event', function () {
      clearTimeout(timer);
      timer = setTimeout(function () { if (typeof window.pmRefresh === 'function') window.pmRefresh(); }, 400);
    });

    var started = false;
    window.__pmStartRealtime = function (me) {
      if (started) return; started = true;
      var libs = [];
      if (!window.SockJS)  libs.push(loadScript('https://cdn.jsdelivr.net/npm/sockjs-client@1.5.2/dist/sockjs.min.js'));
      if (!window.StompJs) libs.push(loadScript('https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.0.0/bundles/stomp.umd.min.js'));
      Promise.all(libs).then(function () {
        if (!window.SockJS || !window.StompJs) { console.warn('pm-core: realtime libs failed to load'); return; }
        var tl = isTeamLeadOnly(me);
        var topics = tl ? (me.leadOfTeamIds || []).map(function (id) { return '/topic/pm/team/' + id; })
                        : ['/topic/pm/events'];
        var client = new StompJs.Client({
          webSocketFactory: function () { return new SockJS('/ws'); },
          reconnectDelay: 5000,
          onConnect: function () {
            setLive(true);
            topics.forEach(function (t) {
              client.subscribe(t, function (m) {
                try { window.dispatchEvent(new CustomEvent('pm:event', { detail: JSON.parse(m.body) })); } catch (e) {}
              });
            });
          },
          onWebSocketClose: function () { setLive(false); }
        });
        client.activate();
      });
    };
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

  function fmtMinutes(m) {
    m = m || 0;
    const h = Math.floor(m / 60), r = m % 60;
    return h ? (r ? h + 'h ' + r + 'm' : h + 'h') : r + 'm';
  }

  /* ============================================================
     PHASE 4 — boot the shared notification client
     ============================================================ */
  async function bootNotifications() {
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
     ============================================================ */
  async function loadApprovalsScript() {
    if (window.PMApprovals) return;
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
     PROJECT INTAKE DRAWER
     ============================================================ */
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
    if (h !== 'templates' && h !== 'requests') return;
    openIntake(h);
    try { history.replaceState(null, '', location.pathname + location.search); } catch (e) { /* ignore */ }
  }
  window.addEventListener('hashchange', function () {
    openIntakeFromHash();
    if (window.PM && window.PM.user) buildSidebar(window.PM.user);
  });

  /* ============================================================
     PAGE & TAB GUARDS
     ============================================================ */
  const ROUTES = [
    { path: '/pm/08-clients',   rule: 'clients' },
    { path: '/pm/12-team-lead', rule: 'teamlead' },
    { path: '/pm/10-insights',  rule: 'manage', tabs: { budget: 'finance' } },
    { path: '/pm/05-planning',  tabs: { backlog: 'manage', sprints: 'manage', goals: 'manage' } },
    { path: '/pm/07-team',      tabs: { members: 'manage', resources: 'manage', reviews: 'manage' } },
    { path: '/pm/02-inbox',     tabs: { activity: 'manage' } }
  ];

  function showNoAccess() {
    if (document.getElementById('pmNoAccess')) return;
    const el = document.createElement('div');
    el.id = 'pmNoAccess';
    el.style.cssText = 'position:fixed;inset:0;z-index:3000;background:var(--bg,var(--card));display:flex;' +
      'align-items:center;justify-content:center;flex-direction:column;gap:12px;color:var(--tx);text-align:center;padding:24px';
    el.innerHTML =
      '<i class="fas fa-lock" style="font-size:32px;color:var(--tx3)"></i>' +
      '<div style="font-size:16px;font-weight:600">You don\u2019t have access to this page</div>' +
      '<div style="color:var(--tx3);font-size:13px">Taking you back to the dashboard\u2026</div>' +
      '<a href="/pm/01-home" class="pm-btn pm-btn-primary">Go to the dashboard</a>';
    document.body.appendChild(el);
    setTimeout(function () { location.replace('/pm/01-home'); }, 2500);
  }

  function guard(rule) {
    if (can(rule)) return true;
    showNoAccess();
    return false;
  }

  function gateTabs(map) {
    if (!map) return;
    let firstOk = null, activeHidden = false;
    document.querySelectorAll('button[data-tab], a[data-tab], [role="tab"][data-tab], .pm-tab[data-tab]').forEach(el => {
      const rule = map[el.getAttribute('data-tab')];
      if (rule && !can(rule)) {
        el.style.display = 'none';
        el.setAttribute('data-pm-gated', '1');
        if (el.classList.contains('active')) activeHidden = true;
      } else if (!el.hasAttribute('data-pm-gated') && !firstOk) {
        firstOk = el;
      }
    });
    if (activeHidden && firstOk) firstOk.click();
  }

  function applyRouteGuard(user) {
    const path = normPath(location.pathname);
    const route = ROUTES.find(r => r.path === path);
    if (!route) return true;

    if (route.rule && !can(route.rule, user)) { showNoAccess(); return false; }

    if (route.tabs) {
      const tab = new URLSearchParams(location.search).get('tab');
      if (tab && route.tabs[tab] && !can(route.tabs[tab], user)) { showNoAccess(); return false; }
      gateTabs(route.tabs);
      setTimeout(function () { gateTabs(route.tabs); }, 300);
      setTimeout(function () { gateTabs(route.tabs); }, 1000);
    }
    return true;
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (window.PMTimer || document.getElementById('pm-timer-js')) return;
    var s = document.createElement('script');
    s.id = 'pm-timer-js';
    s.src = '/pm/js/pm-timer.js';
    s.async = true;
    s.onerror = function () { s.remove(); };
    document.body.appendChild(s);
  });

  function applyTeamLeadMode(me) {
    var tl = isTeamLeadOnly(me);
    try { sessionStorage.setItem('pm.tlOnly', tl ? '1' : '0'); } catch (e) {}
    document.documentElement.classList.toggle('pm-tl', tl);
    if (!tl) return;
    var empId = (document.getElementById('currentEmployeeId') || {}).value || me.employeeId || '';
    var back = empId ? '/candidate/dashboard/' + empId : '/candidate/dashboard';
    function addLink(ulId, href, icon, label, first) {
      var ul = document.getElementById(ulId);
      if (!ul || ul.querySelector('[data-pm-tl="' + href + '"]')) return;
      var li = document.createElement('li');
      li.innerHTML = '<a class="nav-link" data-pm-tl="' + href + '" href="' + href + '"><i class="fas ' + icon + '"></i> <span>' + label + '</span></a>';
      if (normPath(location.pathname) === normPath(href)) li.firstChild.classList.add('active');
      if (first) ul.insertBefore(li, ul.firstChild); else ul.appendChild(li);
    }
    addLink('sideGroupWorkspace', '/pm/12-team-lead', 'fa-user-tie', 'My Team', true);
    addLink('sideGroupDelivery',  '/pm/09-quality', 'fa-shield-halved', 'Quality', false);
    addLink('sideGroupWorkspace', back, 'fa-arrow-left', 'Back to HRMS', false);
    document.querySelectorAll('.side-group').forEach(function (g) {
      var any = Array.prototype.some.call(g.querySelectorAll('a.nav-link'), function (a) {
        return getComputedStyle(a).display !== 'none';
      });
      g.style.display = any ? '' : 'none';
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
      applyTeamLeadMode(me);
      buildHeader(me);
      buildSidebar(me);

      // Realtime connection initialization
      if (window.__pmStartRealtime) {
        window.__pmStartRealtime(me);
      }

      // Phase 4: start the notification client
      bootNotifications().catch(e => console.warn('notif boot failed', e));

      // Phase 7: pre-load the approval UI so drawers work everywhere
      loadApprovalsScript().catch(e => console.warn('approvals boot failed', e));

      // STEP 7.1: route check
      if (!applyRouteGuard(me)) return new Promise(function () { });

      // STEP 7.1: hash links
      openIntakeFromHash();

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

  /* ============================================================
     EXPORT
     ============================================================ */
  window.PM = {
    // ui
    toast, openDrawer, closeDrawer, openCmd, closeCmd,
    toggleSidebar, toggleTheme, esc,
    // formatting
    getInitials, fmtDate, fmtDateTime, fmtMinutes,
    // data
    api, apiList, getMe, bootstrap, logout,
    buildSidebar, buildHeader,
    // Phase 3
    meTasks, meProjects, invalidateMeCaches,
    // Phase 2
    openProjectMembers, openTeamMembers, openProjectFinance, openProjectClosure,
    // STEP 7.1
    can, guard, gateTabs, openIntake, isTeamLeadOnly,
    // Phase 4 + 7 accessors
    get notifications() { return window.Notifications; },
    approvals: () => window.PMApprovals,
    // user
    user: null,
    _nav: SIDEBAR_NAV,
    confirm: function (o) {
      o = o || {};
      return Promise.resolve(window.confirm((o.title ? o.title + '\n\n' : '') + (o.message || 'Are you sure?')));
    }
  };
})();