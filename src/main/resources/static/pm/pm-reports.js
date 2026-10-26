/**
 * pm-reports.js — Reports + Budget tabs for /pm/10-insights   (STEP 7.5)
 * Depends on pm-core.js (PM.api, PM.can, PM.user) and Chart.js 4.
 *
 * Every number on this page comes from /api/pm/reports/*. The server already limits who may call
 * what (ReportController):
 *   budget       all projects = Super Admin | one project = owner / MANAGER / Super Admin
 *   utilization  HR, HR Manager, Super Admin
 *   velocity / quality / risk   anyone who can view that project
 * This file only avoids calling what the server would refuse, so nobody sees zeros or silent failures.
 */
(function () {
  'use strict';

  var TABS = {
    reports: { title: 'Reports',  icon: 'fa-chart-pie',          sub: 'Charts and CSV exports for one project at a time' },
    budget:  { title: 'Budget',   icon: 'fa-indian-rupee-sign',  sub: 'Budget and spend for the projects you manage' }
  };
  var EXPORTS = {
    budget:      '/api/pm/reports/budget/export.csv',
    risk:        '/api/pm/reports/risk/export.csv',
    quality:     '/api/pm/reports/quality/export.csv',
    velocity:    '/api/pm/reports/velocity/export.csv',
    utilization: '/api/pm/reports/utilization/export.csv'
  };
  var GRID = 'rgba(42,57,80,.4)', TICK = '#94a3b8';

  var state = { projects: [], projectId: null, tab: 'reports' };

  function $(id) { return document.getElementById(id); }
  function inr(n) { return '₹' + Math.round(n || 0).toLocaleString('en-IN'); }
  function isSuper() { return !!(PM.user && PM.user.superAdmin); }
  function curProject() {
    return state.projects.filter(function (p) { return p.id === state.projectId; })[0] || null;
  }
  function finProjects() { return state.projects.filter(function (p) { return p.fin; }); }

  /* ---------- small html helpers ---------- */
  function msg(t) { return '<div class="empty">' + PM.esc(t) + '</div>'; }
  function loading() { return '<div class="pm-card" style="grid-column:1/-1">' + msg('Loading…') + '</div>'; }
  function card(title, icon, body) {
    return '<div class="pm-card"><div class="chart-head"><div class="chart-title"><i class="fas ' + icon + '"></i> ' +
      PM.esc(title) + '</div></div>' + body + '</div>';
  }
  function box(id) { return '<div class="chart-box"><canvas id="' + id + '"></canvas></div>'; }

  function chart(id, cfg) {
    var el = $(id); if (!el || !window.Chart) return null;
    var old = Chart.getChart(el); if (old) old.destroy();
    return new Chart(el, cfg);
  }
  function baseOpts(legend) {
    return {
      responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: legend !== false, labels: { color: TICK, boxWidth: 10, font: { size: 10 } } } },
      scales: {
        y: { beginAtZero: true, ticks: { color: TICK }, grid: { color: GRID } },
        x: { ticks: { color: TICK }, grid: { display: false } }
      }
    };
  }

  /* ---------- projects + picker ---------- */
  async function loadProjects() {
    try {
      var list = await PM.apiList('GET', '/api/pm/projects?page=0&size=200');
      state.projects = list.map(function (p) {
        return { id: p.id, name: p.name, fin: p.financeVisible === true };
      });
    } catch (e) { state.projects = []; }
    if (!state.projectId || !curProject()) state.projectId = state.projects.length ? state.projects[0].id : null;
  }

  function renderPicker() {
    var s = $('reportsPicker');
    s.innerHTML = state.projects.map(function (p) {
      return '<option value="' + p.id + '"' + (p.id === state.projectId ? ' selected' : '') + '>' + PM.esc(p.name) + '</option>';
    }).join('');
    s.disabled = !state.projects.length;
  }

  /* ---------- CSV export (fetch, so a refusal shows a message instead of a blank page) ---------- */
  async function exportCsv(kind) {
    var url = (kind === 'budgetAll') ? EXPORTS.budget : EXPORTS[kind];
    if (!url) return;
    if (kind !== 'utilization' && kind !== 'budgetAll') {
      if (!state.projectId) { PM.toast('Pick a project first', 'info'); return; }
      url += '?projectId=' + state.projectId + (kind === 'velocity' ? '&lastSprints=8' : '');
    }
    try {
      var res = await fetch(url, { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' } });
      if (!res.ok) {
        var m = ''; try { m = (await res.json()).message; } catch (e) { /* not json */ }
        throw new Error(m || ('HTTP ' + res.status));
      }
      var blob = await res.blob();
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = kind + '-' + new Date().toISOString().slice(0, 10) + '.csv';
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
    } catch (e) { PM.toast(e.message || 'Export failed', 'error'); }
  }

  function setExportButtons(p) {
    $('expBudget').hidden   = !(p && p.fin);
    $('expRisk').hidden     = !p;
    $('expQuality').hidden  = !p;
    $('expVelocity').hidden = !p;
    $('expUtil').hidden     = !PM.can('admin');
  }

  /* ============================================================
     REPORTS TAB  (one project)
     ============================================================ */
  async function loadReports() {
    var grid = $('reportsGrid'), p = curProject();
    setExportButtons(p);
    if (!p) { grid.innerHTML = '<div class="pm-card" style="grid-column:1/-1">' + msg('No projects to report on yet.') + '</div>'; return; }

    var pid = p.id, q = '?projectId=' + pid;
    grid.innerHTML = loading();
    var r = await Promise.all([
      p.fin ? PM.api('GET', '/api/pm/reports/budget' + q).catch(function () { return null; }) : Promise.resolve(undefined),
      PM.api('GET', '/api/pm/reports/velocity' + q + '&lastSprints=8').catch(function () { return null; }),
      PM.api('GET', '/api/pm/reports/quality' + q).catch(function () { return null; }),
      PM.api('GET', '/api/pm/reports/risk' + q).catch(function () { return null; })
    ]);
    if (pid !== state.projectId || state.tab !== 'reports') return;   // picker or tab changed meanwhile
    var budget = r[0], velocity = r[1], quality = r[2], risk = r[3];

    var html = '';
    if (budget === undefined) html += card('Budget vs Actual', 'fa-indian-rupee-sign', msg('Budget figures are visible to the project owner, managers and Super Admin.'));
    else if (!budget || !budget.length) html += card('Budget vs Actual', 'fa-indian-rupee-sign', msg(budget === null ? 'Could not load the budget.' : 'No budget set yet.'));
    else html += card('Budget vs Actual', 'fa-indian-rupee-sign', box('budgetChart'));

    html += (!velocity || !velocity.length)
      ? card('Velocity by Sprint', 'fa-chart-line', msg(velocity === null ? 'Could not load velocity.' : 'No finished sprints yet.'))
      : card('Velocity by Sprint', 'fa-chart-line', box('velocityChart'));

    var qOk = quality && (quality.bySeverity || []).length;
    html += card('Quality — Issues by Severity', 'fa-bug', qOk ? box('qualityChart') : msg(quality === null ? 'Could not load issues.' : 'No issues yet.'));

    var rOk = risk && (risk.bySeverity || []).length;
    html += card('Risk Register', 'fa-shield', rOk ? box('riskChart') : msg(risk === null ? 'Could not load risks.' : 'No risks yet.'));

    grid.innerHTML = html;

    if (budget && budget.length) chart('budgetChart', {
      type: 'bar',
      data: {
        labels: budget.map(function (x) { return x.projectName; }),
        datasets: [
          { label: 'Budget', data: budget.map(function (x) { return x.budgetTotal; }), backgroundColor: '#334155', borderRadius: 4 },
          { label: 'Spent',  data: budget.map(function (x) { return x.budgetSpent; }), backgroundColor: '#6366f1', borderRadius: 4 }
        ]
      },
      options: baseOpts()
    });
    if (velocity && velocity.length) chart('velocityChart', {
      type: 'line',
      data: {
        labels: velocity.map(function (x) { return x.sprintName; }),
        datasets: [
          { label: 'Committed', data: velocity.map(function (x) { return x.committedPoints; }), borderColor: '#64748b', borderDash: [5, 5], pointRadius: 0, fill: false },
          { label: 'Completed', data: velocity.map(function (x) { return x.completedPoints; }), borderColor: '#6366f1', backgroundColor: 'rgba(99,102,241,.12)', fill: true, tension: .3, pointRadius: 4 }
        ]
      },
      options: baseOpts()
    });
    if (qOk) chart('qualityChart', {
      type: 'doughnut',
      data: {
        labels: quality.bySeverity.map(function (s) { return s.severity; }),
        datasets: [{ data: quality.bySeverity.map(function (s) { return s.count; }),
          backgroundColor: ['#ef4444', '#f59e0b', '#0ea5e9', '#64748b'], borderWidth: 0 }]
      },
      options: { responsive: true, maintainAspectRatio: false, cutout: '60%',
        plugins: { legend: { position: 'bottom', labels: { color: TICK, boxWidth: 8, padding: 10, font: { size: 10 } } } } }
    });
    if (rOk) chart('riskChart', {
      type: 'bar',
      data: {
        labels: risk.bySeverity.map(function (s) { return s.severity; }),
        datasets: [{ data: risk.bySeverity.map(function (s) { return s.count; }),
          backgroundColor: ['#ef4444', '#f59e0b', '#10b981'], borderRadius: 4 }]
      },
      options: baseOpts(false)
    });
  }

  /* People utilization: HR, HR Manager and Super Admin only (the server enforces it as well) */
  async function loadUtilization() {
    var wrap = $('utilCard');
    if (!PM.can('admin')) { wrap.hidden = true; return; }
    wrap.hidden = false;
    var body = $('utilBody');
    try {
      var rows = await PM.api('GET', '/api/pm/reports/utilization') || [];
      rows = rows.slice().sort(function (a, b) { return (b.utilizationPct || 0) - (a.utilizationPct || 0); }).slice(0, 15);
      if (!rows.length) { body.innerHTML = msg('No time logged yet.'); return; }
      body.innerHTML = '<div class="chart-box" style="height:' + Math.max(180, rows.length * 22 + 40) + 'px"><canvas id="utilChart"></canvas></div>';
      chart('utilChart', {
        type: 'bar',
        data: {
          labels: rows.map(function (u) { return u.employeeName; }),
          datasets: [{ label: 'Utilization %', data: rows.map(function (u) { return u.utilizationPct; }),
            backgroundColor: rows.map(function (u) { return u.utilizationPct > 100 ? '#ef4444' : (u.utilizationPct >= 85 ? '#f59e0b' : '#6366f1'); }),
            borderRadius: 4 }]
        },
        options: Object.assign(baseOpts(false), { indexAxis: 'y' })
      });
    } catch (e) { body.innerHTML = msg('Could not load utilization.'); }
  }

  /* ============================================================
     BUDGET TAB  (Super Admin: all projects; owner / MANAGER: their projects)
     ============================================================ */
  async function loadBudget() {
    var rows = [];
    try {
      if (isSuper()) {
        rows = await PM.api('GET', '/api/pm/reports/budget') || [];
      } else {
        var parts = await Promise.all(finProjects().slice(0, 50).map(function (p) {
          return PM.api('GET', '/api/pm/reports/budget?projectId=' + p.id).catch(function () { return []; });
        }));
        parts.forEach(function (x) { rows = rows.concat(x || []); });
      }
    } catch (e) { PM.toast('Failed to load budget: ' + e.message, 'error'); }
    if (state.tab !== 'budget') return;

    $('expBudgetAll').hidden = !isSuper();   // the all-projects CSV is Super Admin only on the server
    if (!rows.length) {
      $('budgetFull').hidden = true;
      $('budgetEmpty').hidden = false;
      return;
    }
    $('budgetEmpty').hidden = true;
    $('budgetFull').hidden = false;

    var t = rows.reduce(function (a, r) {
      a.total += r.budgetTotal || 0; a.spent += r.budgetSpent || 0;
      if ((r.spentPct || 0) > 100) a.over++;
      return a;
    }, { total: 0, spent: 0, over: 0 });

    $('kpiTotal').textContent = inr(t.total);
    $('kpiSpent').textContent = inr(t.spent) + ' (' + (t.total > 0 ? Math.round(t.spent * 100 / t.total) : 0) + '%)';
    $('kpiLeft').textContent  = inr(t.total - t.spent);
    $('kpiOver').textContent  = t.over;

    $('budgetRows').innerHTML = rows.map(function (r) {
      var pct = r.spentPct != null ? r.spentPct : (r.budgetTotal ? r.budgetSpent * 100 / r.budgetTotal : 0);
      var cls = pct > 100 ? 'danger' : (pct >= 85 ? 'warning' : 'success');
      return '<tr><td style="font-weight:600">' + PM.esc(r.projectName) + '</td>' +
        '<td>' + inr(r.budgetTotal) + '</td><td>' + inr(r.budgetSpent) + '</td>' +
        '<td><div style="display:flex;align-items:center;gap:6px;min-width:120px"><div class="pm-progress" style="flex:1">' +
        '<div class="pm-progress-bar ' + cls + '" style="width:' + Math.min(100, Math.round(pct)) + '%"></div></div>' +
        '<span style="font-size:var(--fs-xs);font-weight:600;min-width:34px">' + Math.round(pct) + '%</span></div></td></tr>';
    }).join('');

    chart('budgetVsChart', {
      type: 'bar',
      data: {
        labels: rows.map(function (r) { return r.projectName; }),
        datasets: [
          { label: 'Budget', data: rows.map(function (r) { return r.budgetTotal; }), backgroundColor: '#334155', borderRadius: 4 },
          { label: 'Actual', data: rows.map(function (r) { return r.budgetSpent; }), backgroundColor: '#6366f1', borderRadius: 4 }
        ]
      },
      options: baseOpts()
    });
  }

  /* ============================================================
     TABS
     ============================================================ */
  function switchTab(tab) {
    if (!TABS[tab]) tab = 'reports';
    if (tab === 'budget' && !PM.can('finance')) tab = 'reports';
    state.tab = tab;

    document.querySelectorAll('#pmTabs button').forEach(function (b) { b.classList.toggle('active', b.dataset.tab === tab); });
    document.querySelectorAll('.pm-pane').forEach(function (p) { p.classList.toggle('active', p.id === 'pane-' + tab); });
    $('pmPageTitle').textContent = TABS[tab].title;
    $('pmCrumb').textContent = TABS[tab].title;
    $('pmPageSub').textContent = TABS[tab].sub;
    $('pmPageIcon').className = 'fas ' + TABS[tab].icon;
    $('pickerWrap').hidden = (tab !== 'reports');

    try { history.replaceState(null, '', location.pathname + '?tab=' + tab); } catch (e) { /* ignore */ }
    if (PM.user && PM.buildSidebar) PM.buildSidebar(PM.user);   // keep the highlighted menu entry right

    if (tab === 'budget') loadBudget();
    else { loadReports(); loadUtilization(); }
  }
  window.switchTab = function (tab) { switchTab(tab); };

  async function init() {
    await loadProjects();
    renderPicker();
    $('reportsPicker').onchange = function () { state.projectId = parseInt(this.value, 10); loadReports(); };
    switchTab(new URLSearchParams(location.search).get('tab') || 'reports');
  }

  async function refresh() {
    if (state.tab === 'budget') return loadBudget();
    await loadReports();
  }

  window.PMReports = { init: init, refresh: refresh, exportCsv: exportCsv, switchTab: switchTab };
})();