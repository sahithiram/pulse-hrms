/**
 * pm-reports.js — Report + Budget rendering for /pm/10-insights
 * Depends on pm-core.js and Chart.js.
 */
(function () {
  'use strict';

  var state = {
    projectId: null,
    projects: [],
    budgetChart: null,
    velocityChart: null,
    qualityChart: null,
    riskChart: null,
    utilChart: null
  };

  async function loadProjects() {
    try {
      var list = await PM.api('GET', '/api/pm/projects?page=0&size=200') || [];
      state.projects = (list.content || list || []).map(function (p) { return { id: p.id, name: p.name }; });
      if (!state.projectId && state.projects.length) state.projectId = state.projects[0].id;
    } catch (e) { state.projects = []; }
  }

  function pickerHtml(id) {
    return state.projects.map(function (p) {
      return '<option value="' + p.id + '"' + (p.id === state.projectId ? ' selected' : '') + '>' + PM.esc(p.name) + '</option>';
    }).join('');
  }

  function ensurePicker() {
    var host = document.getElementById('reportsPicker');
    if (!host) {
      host = document.createElement('select');
      host.id = 'reportsPicker';
      host.className = 'pm-select';
      host.style.cssText = 'max-width:260px';
      host.onchange = function () { state.projectId = parseInt(this.value); refresh(); };
      var head = document.querySelector('.pm-page-head > div');
      if (head) head.appendChild(host);
    }
    host.innerHTML = pickerHtml();
  }

  async function loadReports() {
    var box = document.querySelector('#pane-reports .chart-grid');
    if (!box) return;
    box.innerHTML = '<div class="pm-card" style="grid-column:1/-1;text-align:center;padding:20px;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i> Loading reports…</div>';

    try {
      var [budget, velocity, quality, risk] = await Promise.all([
        PM.api('GET', '/api/pm/reports/budget' + (state.projectId ? '?projectId=' + state.projectId : '')).catch(function(){ return []; }),
        state.projectId
          ? PM.api('GET', '/api/pm/reports/velocity?projectId=' + state.projectId + '&lastSprints=8').catch(function(){ return []; })
          : Promise.resolve([]),
        PM.api('GET', '/api/pm/reports/quality' + (state.projectId ? '?projectId=' + state.projectId : '')).catch(function(){ return null; }),
        PM.api('GET', '/api/pm/reports/risk'    + (state.projectId ? '?projectId=' + state.projectId : '')).catch(function(){ return null; })
      ]);

      // Quick Generate cards get their CSV links bound
      document.querySelectorAll('#pane-reports .pm-btn').forEach(function (b) {
        var t = b.textContent || '';
        if (t.indexOf('Budget Report') > -1) b.onclick = function () { downloadCsv('/api/pm/reports/budget/export.csv' + (state.projectId ? '?projectId=' + state.projectId : '')); };
        if (t.indexOf('Risk Summary') > -1)  b.onclick = function () { downloadCsv('/api/pm/reports/risk/export.csv' + (state.projectId ? '?projectId=' + state.projectId : '')); };
      });

      var html = '';
      html += chartCard('budgetChart', 'fa-indian-rupee-sign', 'Budget vs Actual');
      html += chartCard('velocityChart', 'fa-chart-line', 'Velocity by Sprint');
      html += chartCard('qualityChart', 'fa-bug', 'Quality — Issues by Severity');
      html += chartCard('riskChart', 'fa-shield', 'Risk Register');
      box.innerHTML = html;

      renderBudgetChart(budget);
      renderVelocityChart(velocity);
      renderQualityChart(quality);
      renderRiskChart(risk);
    } catch (e) {
      box.innerHTML = '<div class="pm-card" style="grid-column:1/-1;color:var(--dng);text-align:center;padding:20px">Failed: ' + PM.esc(e.message) + '</div>';
    }
  }

  function chartCard(id, icon, title) {
    return '<div class="pm-card"><div class="chart-head"><div class="chart-title"><i class="fas ' + icon + '"></i> ' + title + '</div></div><div class="chart-box"><canvas id="' + id + '"></canvas></div></div>';
  }

  function destroyChart(key) { if (state[key]) { state[key].destroy(); state[key] = null; } }

  function renderBudgetChart(rows) {
    destroyChart('budgetChart');
    var el = document.getElementById('budgetChart');
    if (!el) return;
    state.budgetChart = new Chart(el, {
      type: 'bar',
      data: {
        labels: (rows || []).map(function (r) { return r.projectName; }),
        datasets: [
          { label: 'Budget', data: (rows || []).map(function (r) { return r.budgetTotal; }), backgroundColor: '#334155', borderRadius: 4 },
          { label: 'Spent',  data: (rows || []).map(function (r) { return r.budgetSpent; }), backgroundColor: '#6366f1', borderRadius: 4 }
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { labels: { color: '#94a3b8', boxWidth: 10, font: { size: 10 } } } },
        scales: { y: { beginAtZero: true, grid: { color: 'rgba(42,57,80,.4)' } }, x: { grid: { display: false } } }
      }
    });
  }

  function renderVelocityChart(rows) {
    destroyChart('velocityChart');
    var el = document.getElementById('velocityChart');
    if (!el) return;
    state.velocityChart = new Chart(el, {
      type: 'line',
      data: {
        labels: (rows || []).map(function (r) { return r.sprintName; }),
        datasets: [
          { label: 'Committed', data: (rows || []).map(function (r) { return r.committedPoints; }), borderColor: '#64748b', borderDash: [5,5], pointRadius: 0, fill: false },
          { label: 'Completed', data: (rows || []).map(function (r) { return r.completedPoints; }), borderColor: '#6366f1', backgroundColor: 'rgba(99,102,241,.12)', fill: true, tension: .3, pointRadius: 4 }
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { labels: { color: '#94a3b8', boxWidth: 10, font: { size: 10 } } } },
        scales: { y: { beginAtZero: true, grid: { color: 'rgba(42,57,80,.4)' } }, x: { grid: { display: false } } }
      }
    });
  }

  function renderQualityChart(q) {
    destroyChart('qualityChart');
    var el = document.getElementById('qualityChart');
    if (!el) return;
    if (!q || !(q.bySeverity || []).length) {
      el.parentElement.innerHTML = '<div style="text-align:center;padding:40px;color:var(--tx3)">No issues yet</div>';
      return;
    }
    state.qualityChart = new Chart(el, {
      type: 'doughnut',
      data: {
        labels: q.bySeverity.map(function (s) { return s.severity; }),
        datasets: [{
          data: q.bySeverity.map(function (s) { return s.count; }),
          backgroundColor: ['#ef4444', '#f59e0b', '#0ea5e9', '#64748b'],
          borderWidth: 0
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false, cutout: '60%',
        plugins: { legend: { position: 'bottom', labels: { boxWidth: 8, padding: 10, font: { size: 10 } } } }
      }
    });
  }

  function renderRiskChart(r) {
    destroyChart('riskChart');
    var el = document.getElementById('riskChart');
    if (!el) return;
    if (!r || !(r.bySeverity || []).length) {
      el.parentElement.innerHTML = '<div style="text-align:center;padding:40px;color:var(--tx3)">No risks yet</div>';
      return;
    }
    state.riskChart = new Chart(el, {
      type: 'bar',
      data: {
        labels: r.bySeverity.map(function (s) { return s.severity; }),
        datasets: [{
          data: r.bySeverity.map(function (s) { return s.count; }),
          backgroundColor: ['#ef4444', '#f59e0b', '#10b981'],
          borderRadius: 4
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, grid: { color: 'rgba(42,57,80,.4)' } }, x: { grid: { display: false } } }
      }
    });
  }

  async function loadBudget() {
    try {
      var [budget, util] = await Promise.all([
        PM.api('GET', '/api/pm/reports/budget').catch(function () { return []; }),
        PM.api('GET', '/api/pm/reports/utilization').catch(function () { return []; })
      ]);

      var totals = (budget || []).reduce(function (a, r) {
        a.total += r.budgetTotal || 0;
        a.spent += r.budgetSpent || 0;
        if ((r.spentPct || 0) > 100) a.over++;
        return a;
      }, { total: 0, spent: 0, over: 0 });

      var kpis = document.querySelectorAll('#pane-budget .kpi .kpi-value');
      if (kpis[0]) kpis[0].textContent = '₹' + Math.round(totals.total / 100000) + 'L';
      if (kpis[1]) kpis[1].textContent = '₹' + Math.round(totals.spent / 100000) + 'L (' + (totals.total > 0 ? Math.round(totals.spent * 100 / totals.total) : 0) + '%)';
      if (kpis[2]) kpis[2].textContent = '₹' + Math.round((totals.total - totals.spent) / 100000) + 'L';
      if (kpis[3]) kpis[3].textContent = totals.over;

      // Export button on Budget tab
      var exportBtn = document.querySelector('#pane-budget .pm-page-actions .pm-btn, #pane-budget .pm-btn');
      if (exportBtn) exportBtn.onclick = function () { downloadCsv('/api/pm/reports/budget/export.csv'); };

      var bEl = document.getElementById('budgetVsChart');
      if (bEl) {
        destroyChart('budgetChart');
        state.budgetChart = new Chart(bEl, {
          type: 'bar',
          data: {
            labels: (budget || []).map(function (r) { return r.projectName; }),
            datasets: [
              { label: 'Budget', data: (budget || []).map(function (r) { return r.budgetTotal; }), backgroundColor: '#334155', borderRadius: 4 },
              { label: 'Actual', data: (budget || []).map(function (r) { return r.budgetSpent; }), backgroundColor: '#6366f1', borderRadius: 4 }
            ]
          },
          options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { labels: { color: '#94a3b8', boxWidth: 10, font: { size: 10 } } } },
            scales: { y: { beginAtZero: true, grid: { color: 'rgba(42,57,80,.4)' } }, x: { grid: { display: false } } }
          }
        });
      }

      var cEl = document.getElementById('categoryChart');
      if (cEl) {
        destroyChart('utilChart');
        state.utilChart = new Chart(cEl, {
          type: 'doughnut',
          data: {
            labels: (util || []).slice(0, 6).map(function (u) { return u.employeeName; }),
            datasets: [{
              data: (util || []).slice(0, 6).map(function (u) { return u.utilizationPct; }),
              backgroundColor: ['#6366f1', '#10b981', '#f59e0b', '#a855f7', '#0ea5e9', '#ef4444'],
              borderWidth: 0
            }]
          },
          options: {
            responsive: true, maintainAspectRatio: false, cutout: '65%',
            plugins: { legend: { position: 'bottom', labels: { boxWidth: 8, padding: 10, font: { size: 10 } } } }
          }
        });
      }
    } catch (e) {
      PM.toast('Failed to load budget: ' + e.message, 'error');
    }
  }

  function downloadCsv(url) {
    window.location.href = url;
  }

  async function init() {
    await loadProjects();
    ensurePicker();
    await loadReports();
    // If the Budget pane is visible, load it too
    var budgetPane = document.getElementById('pane-budget');
    if (budgetPane && budgetPane.classList.contains('active')) loadBudget();
  }

  async function refresh() {
    var active = document.querySelector('#pmTabs button.active');
    if (!active) return;
    if (active.dataset.tab === 'reports') await loadReports();
    if (active.dataset.tab === 'budget')  await loadBudget();
  }

  window.PMReports = {
    init: init,
    refresh: refresh,
    loadReports: loadReports,
    loadBudget: loadBudget
  };
})();